"""B2B2C: entitlement, aggregates, and the boundary that makes both safe.

The interesting tests here are the negative ones. An organisation model is easy
to get right in the happy path and easy to get catastrophically wrong in one
extra column, so several of these assert that something does NOT exist.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.organization import (
    MIN_REPORTING_THRESHOLD,
    ROLE_ANALYST,
    ROLE_BENEFITS_OWNER,
    STATUS_ACTIVE,
    STATUS_ENDED,
    EligibilityGroup,
    Organization,
    OrgAdmin,
    OrgMembership,
)
from app.models.user import User
from app.services import organizations as org_service


async def _signup(client, prefix: str) -> tuple[str, str]:
    """Returns (email, access token) and leaves the client authorised as them."""
    email = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "T"})
    assert r.status_code == 201, r.text
    token = r.json()["access_token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return email, token


async def _user_id(db, email: str) -> uuid.UUID:
    user = await db.scalar(select(User).where(User.email == email))
    assert user is not None
    return user.id


async def _make_org(db, *, name: str, admin_email: str, role: str = ROLE_BENEFITS_OWNER,
                    threshold: int = MIN_REPORTING_THRESHOLD) -> Organization:
    org = Organization(name=name, region="IN", reporting_threshold=threshold, seats_licensed=100)
    db.add(org)
    await db.flush()
    db.add(OrgAdmin(org_id=org.id, user_id=await _user_id(db, admin_email), role=role))
    await db.commit()
    await db.refresh(org)
    return org


# ---------------------------------------------------------------- access


async def test_a_plain_user_is_not_an_org_admin(client):
    await _signup(client, "plain")
    r = await client.get("/org")
    assert r.status_code == 403
    assert "administrator of any organisation" in r.json()["detail"]


async def test_platform_admin_is_not_automatically_an_org_admin(client):
    """Being CereBro staff must not grant access to a customer's reporting.

    They are different jobs. Conflating them would let a support engineer open
    an employer's dashboard by accident.
    """
    email, _ = await _signup(client, "staff")
    async with SessionLocal() as db:
        user = await db.scalar(select(User).where(User.email == email))
        user.is_admin = True
        await db.commit()

    r = await client.get("/org")
    assert r.status_code == 403


async def test_org_admin_reads_only_their_own_organisation(client):
    admin_email, _ = await _signup(client, "owner")
    async with SessionLocal() as db:
        org = await _make_org(db, name="Acme Health", admin_email=admin_email)
        # A second organisation the caller has nothing to do with.
        other = Organization(name="Rival Corp", region="IN")
        db.add(other)
        await db.commit()
        other_id = other.id

    r = await client.get("/org")
    assert r.status_code == 200
    assert r.json()["name"] == "Acme Health"
    assert r.json()["id"] == str(org.id)
    assert r.json()["id"] != str(other_id)


async def test_there_is_no_route_that_takes_an_org_id(client):
    """Cross-tenant reads are prevented structurally, not by a check.

    Every /org route resolves the organisation from the signed-in user. If a
    path parameter named org_id ever appears, a client can supply another
    organisation's id and the only thing standing between them is a comparison
    somebody has to remember to write.
    """
    from app.main import app

    org_paths = [r.path for r in app.routes if getattr(r, "path", "").startswith("/org")]
    assert org_paths, "no /org routes registered"
    assert not [p for p in org_paths if "{org_id}" in p]


# ---------------------------------------------------------------- roles


async def test_analyst_can_read_but_not_write(client):
    admin_email, _ = await _signup(client, "analyst")
    async with SessionLocal() as db:
        await _make_org(db, name="Readonly Ltd", admin_email=admin_email, role=ROLE_ANALYST)

    assert (await client.get("/org/summary")).status_code == 200
    r = await client.post("/org/groups", json={"name": "Everyone"})
    assert r.status_code == 403
    assert "may read reports but not change" in r.json()["detail"]


# ---------------------------------------------------------------- thresholds


def test_threshold_cannot_be_set_below_the_floor():
    assert org_service.clamp_threshold(1) == MIN_REPORTING_THRESHOLD
    assert org_service.clamp_threshold(None) == MIN_REPORTING_THRESHOLD
    assert org_service.clamp_threshold(0) == MIN_REPORTING_THRESHOLD
    assert org_service.clamp_threshold(-5) == MIN_REPORTING_THRESHOLD
    # More cautious than the default is always allowed.
    assert org_service.clamp_threshold(50) == 50


async def test_patching_the_threshold_clamps_rather_than_rejecting(client):
    admin_email, _ = await _signup(client, "owner2")
    async with SessionLocal() as db:
        await _make_org(db, name="Clamp Co", admin_email=admin_email)

    r = await client.patch("/org", json={"reporting_threshold": 2, "retention_months": 12})
    assert r.status_code == 200
    body = r.json()
    assert body["reporting_threshold"] == MIN_REPORTING_THRESHOLD
    # The rest of the edit survived rather than being lost to a 422.
    assert body["retention_months"] == 12


async def test_small_groups_are_suppressed_not_rounded(client):
    admin_email, _ = await _signup(client, "owner3")
    async with SessionLocal() as db:
        org = await _make_org(db, name="Small Co", admin_email=admin_email)
        group = EligibilityGroup(org_id=org.id, name="Caregivers")
        db.add(group)
        await db.flush()
        # Four members: under the threshold of 20.
        for i in range(4):
            u = User(email=f"m{i}-{uuid.uuid4().hex[:8]}@test.app", hashed_password="x")
            db.add(u)
            await db.flush()
            db.add(OrgMembership(org_id=org.id, user_id=u.id, group_id=group.id, status=STATUS_ACTIVE))
        await db.commit()

    r = await client.get("/org/groups/totals")
    assert r.status_code == 200
    row = next(t for t in r.json() if t["name"] == "Caregivers")
    assert row["suppressed"] is True
    # Null, not zero and not a rounded band — a reader must be able to tell
    # "too small to report" from "nobody activated".
    assert row["activated"] is None
    assert row["active"] is None
    # Eligible is the organisation's own list, so it is not withheld.
    assert row["eligible"] == 4


async def test_a_group_over_the_threshold_reports_numbers(client):
    admin_email, _ = await _signup(client, "owner4")
    async with SessionLocal() as db:
        org = await _make_org(db, name="Big Co", admin_email=admin_email)
        group = EligibilityGroup(org_id=org.id, name="All employees")
        db.add(group)
        await db.flush()
        for i in range(MIN_REPORTING_THRESHOLD + 1):
            u = User(email=f"b{i}-{uuid.uuid4().hex[:8]}@test.app", hashed_password="x")
            db.add(u)
            await db.flush()
            db.add(OrgMembership(org_id=org.id, user_id=u.id, group_id=group.id, status=STATUS_ACTIVE))
        await db.commit()

    row = next(t for t in (await client.get("/org/groups/totals")).json() if t["name"] == "All employees")
    assert row["suppressed"] is False
    assert row["activated"] == MIN_REPORTING_THRESHOLD + 1


async def test_summary_states_that_individual_reporting_does_not_exist(client):
    admin_email, _ = await _signup(client, "owner5")
    async with SessionLocal() as db:
        await _make_org(db, name="Stated Co", admin_email=admin_email)

    body = (await client.get("/org/summary")).json()
    # Read the boundary rather than infer it from a missing field.
    assert body["individual_reporting_available"] is False
    assert body["reporting_threshold"] >= MIN_REPORTING_THRESHOLD


# ---------------------------------------------------------------- boundary


async def test_no_org_response_carries_a_member_identifier(client):
    """The membership list is seats, not a roster.

    An administrator manages entitlement by their own external_ref. Returning
    user ids or emails would hand every employer a mapping from their payroll to
    a CereBro account, which is a re-identification key for anything that ever
    leaks alongside it.
    """
    admin_email, _ = await _signup(client, "owner6")
    member_email, _ = await _signup(client, "member")
    # Re-authorise as the admin.
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    async with SessionLocal() as db:
        await _make_org(db, name="Roster Co", admin_email=admin_email)

    created = await client.post("/org/members", json={"email": member_email, "external_ref": "EMP-1"})
    assert created.status_code == 201, created.text

    listed = (await client.get("/org/members")).json()
    assert len(listed) == 1
    row = listed[0]
    assert row["external_ref"] == "EMP-1"
    for forbidden in ("user_id", "email", "name"):
        assert forbidden not in row, f"membership response leaked {forbidden}"


async def test_the_org_model_never_imports_wellbeing_models():
    """A structural check on the thing the product promises.

    If someone adds `from app.models.mood import MoodLog` to the organisation
    model or its service, the join that follows is one line away.
    """
    import app.api.routes.organizations as routes
    import app.models.organization as models
    import app.services.organizations as service

    banned = ("MoodLog", "JournalEntry", "ChatMessage", "SleepLog", "SafetyPlan", "ContextMemory")
    for module in (models, service, routes):
        for name in banned:
            assert not hasattr(module, name), f"{module.__name__} reaches {name}"


async def test_ending_sponsorship_keeps_the_account(client):
    admin_email, _ = await _signup(client, "owner7")
    member_email, _ = await _signup(client, "member2")
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    async with SessionLocal() as db:
        await _make_org(db, name="Ending Co", admin_email=admin_email)

    membership_id = (await client.post("/org/members", json={"email": member_email})).json()["id"]
    ended = await client.delete(f"/org/members/{membership_id}")
    assert ended.status_code == 200
    assert ended.json()["status"] == STATUS_ENDED

    async with SessionLocal() as db:
        member = await db.scalar(select(User).where(User.email == member_email))
        # The person still exists and is still active. Only the entitlement went.
        assert member is not None and member.is_active
        assert await org_service.is_sponsored(db, member.id) is False


async def test_adding_a_member_does_not_create_an_account(client):
    """An employer cannot conjure a CereBro account for someone."""
    admin_email, _ = await _signup(client, "owner8")
    async with SessionLocal() as db:
        await _make_org(db, name="NoConjure Co", admin_email=admin_email)

    stranger = f"stranger-{uuid.uuid4().hex[:8]}@test.app"
    r = await client.post("/org/members", json={"email": stranger})
    assert r.status_code == 202
    async with SessionLocal() as db:
        assert await db.scalar(select(User).where(User.email == stranger)) is None


# ---------------------------------------------------------------- entitlement


async def test_sponsorship_grants_entitlement_only_while_in_force(client):
    from datetime import timedelta

    from app.core.database import utcnow

    admin_email, _ = await _signup(client, "owner9")
    member_email, _ = await _signup(client, "member3")
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    async with SessionLocal() as db:
        await _make_org(db, name="Window Co", admin_email=admin_email)
        member_id = await _user_id(db, member_email)

    today = utcnow().date()
    await client.post(
        "/org/members",
        json={"email": member_email, "access_start": str(today - timedelta(days=1)),
              "access_end": str(today + timedelta(days=1))},
    )
    async with SessionLocal() as db:
        assert await org_service.is_sponsored(db, member_id) is True

    # Move the window into the past: entitlement lapses without anything being
    # deleted, which is what a contract ending should look like.
    async with SessionLocal() as db:
        m = await db.scalar(select(OrgMembership).where(OrgMembership.user_id == member_id))
        m.access_end = today - timedelta(days=1)
        await db.commit()
        assert await org_service.is_sponsored(db, member_id) is False


async def test_an_inactive_organisation_grants_nothing(client):
    admin_email, _ = await _signup(client, "owner10")
    member_email, _ = await _signup(client, "member4")
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    async with SessionLocal() as db:
        org = await _make_org(db, name="Lapsed Co", admin_email=admin_email)
        member_id = await _user_id(db, member_email)

    await client.post("/org/members", json={"email": member_email})
    async with SessionLocal() as db:
        assert await org_service.is_sponsored(db, member_id) is True
        o = await db.get(Organization, org.id)
        o.is_active = False
        await db.commit()
        assert await org_service.is_sponsored(db, member_id) is False


# ---------------------------------------------------------------- groups & programmes


async def test_groups_and_sponsorship_are_scoped_to_the_callers_org(client):
    admin_email, _ = await _signup(client, "owner11")
    async with SessionLocal() as db:
        await _make_org(db, name="Scoped Co", admin_email=admin_email)
        foreign = Organization(name="Foreign Co")
        db.add(foreign)
        await db.flush()
        foreign_group = EligibilityGroup(org_id=foreign.id, name="Theirs")
        db.add(foreign_group)
        await db.commit()
        foreign_group_id = str(foreign_group.id)

    # Another organisation's group is not addressable, even with its real id.
    r = await client.post("/org/members", json={"email": "x@test.app", "group_id": foreign_group_id})
    assert r.status_code == 404
    r = await client.post(
        "/org/programmes", json={"programme_slug": "calm-workdays", "group_id": foreign_group_id}
    )
    assert r.status_code == 404


async def test_membership_payload_rejects_unknown_fields(client):
    """The importer refuses what it does not recognise instead of ignoring it.

    A CSV column called `mood` or `diagnosis` must fail loudly, not be dropped
    silently — silence is how a wellness field ends up in an employer's file.
    """
    admin_email, _ = await _signup(client, "owner12")
    async with SessionLocal() as db:
        await _make_org(db, name="Strict Co", admin_email=admin_email)

    r = await client.post("/org/members", json={"email": "a@test.app", "mood": "anxious"})
    assert r.status_code == 422


async def test_sponsoring_a_programme_records_availability(client):
    admin_email, _ = await _signup(client, "owner13")
    async with SessionLocal() as db:
        await _make_org(db, name="Sponsor Co", admin_email=admin_email)

    created = await client.post("/org/programmes", json={"programme_slug": "calm-workdays"})
    assert created.status_code == 201
    assert created.json()["programme_slug"] == "calm-workdays"
    listed = (await client.get("/org/programmes")).json()
    assert [p["programme_slug"] for p in listed] == ["calm-workdays"]

# ---------------------------------------------------------------- branches


async def test_an_inactive_organisation_locks_its_admins_out(client):
    """A lapsed contract closes the portal, not just the entitlement.

    Otherwise an organisation whose sponsorship has ended keeps reading its
    reporting surface indefinitely.
    """
    admin_email, _ = await _signup(client, "owner14")
    async with SessionLocal() as db:
        org = await _make_org(db, name="Closed Co", admin_email=admin_email)

    assert (await client.get("/org")).status_code == 200
    async with SessionLocal() as db:
        o = await db.get(Organization, org.id)
        o.is_active = False
        await db.commit()

    r = await client.get("/org")
    assert r.status_code == 403
    assert "not active" in r.json()["detail"]


async def test_groups_can_be_created_and_listed(client):
    admin_email, _ = await _signup(client, "owner15")
    async with SessionLocal() as db:
        await _make_org(db, name="Groups Co", admin_email=admin_email)

    created = await client.post(
        "/org/groups",
        json={"name": "Graduate trainees", "rule": "Joined within 12 months", "source": "hris"},
    )
    assert created.status_code == 201
    assert created.json()["name"] == "Graduate trainees"

    listed = (await client.get("/org/groups")).json()
    assert [g["name"] for g in listed] == ["Graduate trainees"]
    assert listed[0]["source"] == "hris"


async def test_the_same_person_cannot_hold_two_seats(client):
    """A duplicate import must not silently double-count a seat."""
    admin_email, _ = await _signup(client, "owner16")
    member_email, _ = await _signup(client, "member5")
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    async with SessionLocal() as db:
        await _make_org(db, name="Dupe Co", admin_email=admin_email)

    assert (await client.post("/org/members", json={"email": member_email})).status_code == 201
    again = await client.post("/org/members", json={"email": member_email})
    assert again.status_code == 409
    assert "Already a sponsored member" in again.json()["detail"]


async def test_one_org_cannot_end_anothers_membership(client):
    """The membership id is a real id, so the ownership check has to hold."""
    admin_email, _ = await _signup(client, "owner17")
    victim_admin, _ = await _signup(client, "owner18")
    member_email, _ = await _signup(client, "member6")

    # Build the victim organisation and give it a member.
    r = await client.post("/auth/login", data={"username": victim_admin, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    async with SessionLocal() as db:
        await _make_org(db, name="Victim Co", admin_email=victim_admin)
    victim_membership = (await client.post("/org/members", json={"email": member_email})).json()["id"]

    # Now act as an administrator of a different organisation.
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    async with SessionLocal() as db:
        await _make_org(db, name="Attacker Co", admin_email=admin_email)

    r = await client.delete(f"/org/members/{victim_membership}")
    assert r.status_code == 404, "one organisation ended another's sponsorship"

    # And it really is untouched.
    async with SessionLocal() as db:
        m = await db.get(OrgMembership, uuid.UUID(victim_membership))
        assert m.status == STATUS_ACTIVE


async def test_a_membership_that_has_not_started_grants_nothing(client):
    """Access dates are a window, not a switch — the near edge matters too."""
    from datetime import timedelta

    from app.core.database import utcnow

    admin_email, _ = await _signup(client, "owner19")
    member_email, _ = await _signup(client, "member7")
    r = await client.post("/auth/login", data={"username": admin_email, "password": "password123"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    async with SessionLocal() as db:
        await _make_org(db, name="Future Co", admin_email=admin_email)
        member_id = await _user_id(db, member_email)

    tomorrow = utcnow().date() + timedelta(days=1)
    await client.post("/org/members", json={"email": member_email, "access_start": str(tomorrow)})

    async with SessionLocal() as db:
        assert await org_service.is_sponsored(db, member_id) is False
