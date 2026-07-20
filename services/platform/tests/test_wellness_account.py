"""Consent, profile, trusted contact, streak — the account half of the wellness slice.

The content half (journal, sleep, check-ins) is deliberately NOT here: it lives in the
engine. The most important test in this file is the one asserting that it stays there —
"counts, never content" is only true if this database has nothing to leak.
"""

import jwt
import pytest

from app import config
from app.models import CONSENT_KEYS

ALL_TRUE = {key: True for key in CONSENT_KEYS}


async def _member(client, org_with_admin):
    """An ordinary employee in the org — the person all of this is about."""
    from datetime import datetime, timedelta, timezone

    from app.db import SessionLocal
    from app.models import Invitation
    from app.security import new_opaque_token

    raw, token_hash = new_opaque_token()
    async with SessionLocal() as session:
        session.add(
            Invitation(
                org_id=org_with_admin["org"]["id"],
                email="worker@acme.example",
                role="user",
                token_hash=token_hash,
                expires_at=datetime.now(timezone.utc) + timedelta(days=1),
            )
        )
        await session.commit()
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "Worker", "password": "hunter2hunter2"},
    )
    assert r.status_code == 201, r.text
    tokens = r.json()
    return {"Authorization": f"Bearer {tokens['access_token']}"}, tokens


# ── consent ──────────────────────────────────────────────────────────────────


async def test_consent_starts_at_no_and_says_so(client, org_with_admin):
    """Consent is an act, not an inheritance. A fresh account has agreed to nothing, and
    the endpoint says exactly that — six explicit falses, not a 404 the app renders as
    six unticked boxes it never asked about."""
    headers, _ = await _member(client, org_with_admin)

    r = await client.get("/users/me/consent", headers=headers)

    assert r.status_code == 200
    assert r.json() == {key: False for key in CONSENT_KEYS}


async def test_a_single_toggle_persists_without_disturbing_the_others(client, org_with_admin):
    """Settings sends ONE key at a time. A patch that silently reset the other five to
    their defaults would revoke consents the person never touched."""
    headers, _ = await _member(client, org_with_admin)
    await client.patch("/users/me/consent", json=ALL_TRUE, headers=headers)

    # Re-login: withdrawing below revokes tokens, and granting must survive a rotation.
    r = await client.patch("/users/me/consent", json={"journal_memory": True}, headers=headers)

    assert r.status_code == 200
    assert r.json()["journal_memory"] is True
    body = (await client.get("/users/me/consent", headers=headers)).json()
    assert body == ALL_TRUE, "patching one key disturbed the others"


async def test_the_six_categories_reach_the_engine_inside_the_signed_token(
    client, org_with_admin
):
    """How the engine can honour a consent it cannot query: the platform signs the flags
    into the access token. The engine verifies the signature it already shares, so the
    person's choice is enforceable offline and unforgeable by the client."""
    headers, _ = await _member(client, org_with_admin)
    await client.patch("/users/me/consent", json={"journal_memory": True}, headers=headers)

    tokens = await client.post(
        "/auth/login",
        data={"username": "worker@acme.example", "password": "hunter2hunter2"},
    )
    claims = jwt.decode(
        tokens.json()["access_token"], config.JWT_SECRET, algorithms=[config.JWT_ALGORITHM]
    )

    assert claims["consent"]["journal_memory"] is True
    assert claims["consent"]["mood_history"] is False
    assert set(claims["consent"]) == set(CONSENT_KEYS)


async def test_a_withdrawal_takes_effect_on_the_very_next_request(client, org_with_admin):
    """Withdrawal has to BITE, not merely be recorded.

    The engine enforces from the signed claim, so the token in the caller's hand still
    says yes for up to ACCESS_TTL_MIN — fifteen minutes of storing what they just told us
    to stop storing. A consent change therefore rotates the session and hands back a token
    that already carries the new answer.
    """
    headers, _ = await _member(client, org_with_admin)
    await client.patch("/users/me/consent", json=ALL_TRUE, headers=headers)

    r = await client.patch("/users/me/consent", json={"mood_history": False}, headers=headers)

    fresh = jwt.decode(
        r.json()["access_token"], config.JWT_SECRET, algorithms=[config.JWT_ALGORITHM]
    )
    assert fresh["consent"]["mood_history"] is False, "the new token still said yes"
    assert fresh["consent"]["ai_memory"] is True, "the rotation lost their other consents"


async def test_the_stale_session_cannot_be_rotated_onward(client, org_with_admin):
    """The old refresh token dies with the consent it was minted under — otherwise a
    withdrawal could be undone by simply refreshing an older session."""
    headers, tokens = await _member(client, org_with_admin)

    await client.patch("/users/me/consent", json={"mood_history": False}, headers=headers)

    stale = await client.post(
        "/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
    )
    assert stale.status_code == 401


async def test_the_person_is_not_signed_out_for_touching_a_switch(client, org_with_admin):
    """The new pair arrives in the response, so the app swaps tokens and carries on. A
    consent screen that logs you out for using it is a consent screen nobody uses."""
    headers, _ = await _member(client, org_with_admin)

    r = (await client.patch("/users/me/consent", json={"ai_memory": True}, headers=headers)).json()
    new_headers = {"Authorization": f"Bearer {r['access_token']}"}

    assert (await client.get("/users/me", headers=new_headers)).status_code == 200
    onward = await client.post("/auth/refresh", json={"refresh_token": r["refresh_token"]})
    assert onward.status_code == 200, "the pair we handed back did not work"


async def test_an_empty_consent_patch_is_a_400(client, org_with_admin):
    headers, _ = await _member(client, org_with_admin)

    assert (await client.patch("/users/me/consent", json={}, headers=headers)).status_code == 400


async def test_consent_is_the_persons_own_and_nobody_elses(client, org_with_admin):
    """There is no route by which an org admin reads or sets an employee's consent. The
    HR portal cannot tick a box on somebody's behalf, which is the entire point of it."""
    admin_headers = org_with_admin["admin_headers"]
    member_headers, _ = await _member(client, org_with_admin)
    await client.patch("/users/me/consent", json=ALL_TRUE, headers=member_headers)

    # The admin's own consent — not the employee's — is all this can ever return.
    body = (await client.get("/users/me/consent", headers=admin_headers)).json()

    assert body == {key: False for key in CONSENT_KEYS}
    roster = (await client.get("/orgs/me/people", headers=admin_headers)).json()
    assert "consent" not in str(roster).lower(), "the roster leaked consent state"


# ── the firewall: this database holds no content ─────────────────────────────


async def test_no_platform_route_exposes_wellness_content(client):
    """"Counts, never content", asserted structurally rather than promised.

    The journal, the sleep log and the check-ins live in the engine. This service is the
    one an HR admin's token reaches — so it must have nowhere to reach. If someone later
    adds /journal here for convenience, this fails.
    """
    paths = [r.path for r in client._transport.app.routes if hasattr(r, "path")]

    for word in ("journal", "sleep", "mood", "transcript", "wellness"):
        assert not any(word in p.lower() for p in paths), (
            f"a platform route mentions '{word}' — coaching content must not live in the "
            "org database"
        )


async def test_the_user_table_holds_no_content_columns(client):
    """The schema half of the same promise: no column on this table could store a diary
    entry, a mood, or a night's sleep, however tempted a future endpoint might be."""
    from app.models import User

    columns = {c.name for c in User.__table__.columns}
    forbidden = {"journal", "mood", "sleep", "transcript", "entry", "body", "note"}

    assert not (columns & forbidden), f"content columns on the user table: {columns & forbidden}"


async def test_no_platform_table_holds_content_columns(client):
    """The firewall extends to EVERY table on the org database — including the new
    billing/subscription tables — not just users. None may grow a column that could
    store what someone said to their coach."""
    from app import models  # noqa: F401 — register all tables
    from app.db import Base

    # The coaching-content words. NOT "message" — DemoRequest.message is a sales-inquiry
    # field, not coaching content; the firewall guards journals/moods/sleep/transcripts.
    forbidden = {"journal", "mood", "sleep", "transcript", "diary", "entry", "body"}
    offenders = {
        t.name: (cols & forbidden)
        for t in Base.metadata.sorted_tables
        if (cols := {c.name for c in t.columns}) & forbidden
    }
    assert not offenders, f"content columns on platform tables: {offenders}"


# ── profile ──────────────────────────────────────────────────────────────────


async def test_the_profile_patch_takes_one_field_or_several(client, org_with_admin):
    """Settings PATCHes {companion} alone; onboarding PATCHes goals+motivations together.
    Both must work, and neither may wipe what it did not mention."""
    headers, _ = await _member(client, org_with_admin)

    await client.patch("/users/me", json={"companion": "Straight Talker"}, headers=headers)
    await client.patch(
        "/users/me",
        json={"goals": ["lead better"], "motivations": ["my team"]},
        headers=headers,
    )
    me = (await client.get("/users/me", headers=headers)).json()

    assert me["companion"] == "Straight Talker", "the second patch wiped the first"
    assert me["goals"] == ["lead better"]
    assert me["motivations"] == ["my team"]
    assert me["name"] == "Worker", "a field nobody patched was cleared"


async def test_the_crisis_region_is_a_profile_field_the_person_owns(client, org_with_admin):
    headers, _ = await _member(client, org_with_admin)

    await client.patch("/users/me", json={"region": "IN"}, headers=headers)

    assert (await client.get("/users/me", headers=headers)).json()["region"] == "IN"


# ── age attestation ──────────────────────────────────────────────────────────


async def test_the_age_gate_records_when_they_said_so_not_how_old_they_are(
    client, org_with_admin
):
    """A date, not a birthday. The product is not for children and DPDP treats a child's
    data differently — so the attestation is worth keeping, and the date of birth is not
    worth asking for."""
    from sqlalchemy import select

    from app.db import SessionLocal
    from app.models import User

    headers, _ = await _member(client, org_with_admin)

    r = await client.post("/users/me/attest", json={"adult": True}, headers=headers)

    assert r.status_code == 200 and r.json()["adult"] is True
    async with SessionLocal() as session:
        user = (
            await session.execute(select(User).where(User.email == "worker@acme.example"))
        ).scalar_one()
    assert user.adult_attested_at is not None
    columns = {c.name for c in User.__table__.columns}
    assert "date_of_birth" not in columns and "age" not in columns, "we asked how old they are"


async def test_a_fresh_personal_signup_is_not_yet_adult_attested(client):
    """The engine gates coaching on the signed `adult` claim. A brand-new consumer account
    has attested nothing, so its token must say so."""
    from app.security import decode_access_token

    r = await client.post(
        "/auth/signup",
        json={"email": "unattested@example.com", "password": "hunter2hunter2", "name": "U"},
    )
    assert r.status_code == 201, r.text
    assert decode_access_token(r.json()["access_token"])["adult"] is False


async def test_attest_rotates_the_token_so_the_adult_claim_is_immediate(client):
    """Attest must hand back a fresh token carrying adult=true — otherwise a user who just
    confirmed 18+ in onboarding would be refused their first coaching turn until the token
    rotated (≤15 min)."""
    from app.security import decode_access_token

    r = await client.post(
        "/auth/signup",
        json={"email": "attesting@example.com", "password": "hunter2hunter2", "name": "A"},
    )
    h = {"Authorization": f"Bearer {r.json()['access_token']}"}
    r2 = await client.post("/users/me/attest", json={"adult": True}, headers=h)
    assert r2.status_code == 200
    body = r2.json()
    assert body["adult"] is True and body["access_token"]
    assert decode_access_token(body["access_token"])["adult"] is True


async def test_a_b2b_seat_is_adult_by_contract(client, org_with_admin):
    """A B2B employee never does consumer 18+ onboarding, so the claim is true by contract —
    the age gate must not lock enterprise seats out of coaching."""
    from app.security import decode_access_token

    _, tokens = await _member(client, org_with_admin)
    assert decode_access_token(tokens["access_token"])["adult"] is True


# ── trusted contact ──────────────────────────────────────────────────────────


async def test_an_unset_trusted_contact_is_null_not_an_error(client, org_with_admin):
    """The app renders "Not set" from a null. A 404 would have been an error state where
    an honest empty one belongs."""
    headers, _ = await _member(client, org_with_admin)

    r = await client.get("/users/me/trusted-contact", headers=headers)

    assert r.status_code == 200
    assert r.json() is None


async def test_a_trusted_contact_round_trips_and_can_be_removed(client, org_with_admin):
    headers, _ = await _member(client, org_with_admin)

    await client.put(
        "/users/me/trusted-contact",
        json={"name": "Sam", "method": "sms", "value": "+91 90000 00000",
              "notify_consent": True},
        headers=headers,
    )
    stored = (await client.get("/users/me/trusted-contact", headers=headers)).json()

    assert stored["name"] == "Sam" and stored["value"] == "+91 90000 00000"

    assert (await client.delete("/users/me/trusted-contact", headers=headers)).status_code == 204
    assert (await client.get("/users/me/trusted-contact", headers=headers)).json() is None


async def test_the_org_never_sees_the_trusted_contact(client, org_with_admin):
    """It is a third party's name and number, given in confidence by an employee. The
    employer has no claim on it whatsoever."""
    member_headers, _ = await _member(client, org_with_admin)
    await client.put(
        "/users/me/trusted-contact",
        json={"name": "Sam", "method": "sms", "value": "+91 90000 00000"},
        headers=member_headers,
    )

    roster = (await client.get("/orgs/me/people",
                               headers=org_with_admin["admin_headers"])).json()

    assert "Sam" not in str(roster)
    assert "90000" not in str(roster)


async def test_deleting_the_account_takes_the_trusted_contact_with_it(client, org_with_admin):
    """Somebody else's phone number has no business outliving the account it was given
    to — and there is nobody left to ask for its removal."""
    headers, _ = await _member(client, org_with_admin)
    await client.put(
        "/users/me/trusted-contact",
        json={"name": "Sam", "method": "sms", "value": "+91 90000 00000"},
        headers=headers,
    )

    await client.delete("/users/me?confirm=true", headers=headers)

    from sqlalchemy import select

    from app.db import SessionLocal
    from app.models import User

    async with SessionLocal() as session:
        rows = (await session.execute(select(User))).scalars().all()
    scrubbed = [u for u in rows if not u.is_active]
    assert scrubbed and all(u.trusted_contact_value == "" for u in scrubbed)
    assert all(not any(u.consents().values()) for u in scrubbed), "consents survived deletion"


# ── export ───────────────────────────────────────────────────────────────────


async def test_the_export_includes_consent_and_the_trusted_contact(client, org_with_admin):
    """Right of access: what we hold about them includes the choices they made."""
    headers, _ = await _member(client, org_with_admin)
    await client.patch("/users/me/consent", json={"ai_memory": True}, headers=headers)
    await client.put(
        "/users/me/trusted-contact",
        json={"name": "Sam", "method": "sms", "value": "+91 90000 00000"},
        headers=headers,
    )

    export = (await client.get("/users/me/export", headers=headers)).json()

    assert export["consent"]["ai_memory"] is True
    assert export["consent"]["updated_at"]
    assert export["trusted_contact"]["name"] == "Sam"
    assert "engine" in export["note"], "the export must point at the content half"


# ── streak ───────────────────────────────────────────────────────────────────


async def test_a_streak_counts_consecutive_days_of_their_own_activity(client, org_with_admin):
    from datetime import datetime, timedelta, timezone

    from app.db import SessionLocal
    from app.models import ActivityEvent, User
    from sqlalchemy import select

    headers, _ = await _member(client, org_with_admin)
    me = (await client.get("/users/me", headers=headers)).json()

    today = datetime.now(timezone.utc)
    async with SessionLocal() as session:
        user = (await session.execute(select(User).where(User.id == me["id"]))).scalar_one()
        for days_ago in (0, 1, 2, 5):  # a 3-day run, then a gap, then one more
            session.add(
                ActivityEvent(
                    org_id=user.org_id, user_id=user.id, kind="session_started",
                    created_at=today - timedelta(days=days_ago),
                )
            )
        await session.commit()

    body = (await client.get("/users/me/streak", headers=headers)).json()

    assert body["current"] == 3
    assert body["longest"] == 3


async def test_a_streak_survives_a_day_that_is_not_over_yet(client, org_with_admin):
    """Yesterday but not today is still a live streak. Walking from today would zero it
    every morning until the person opened the app — punishing them for the hour."""
    from datetime import datetime, timedelta, timezone

    from app.db import SessionLocal
    from app.models import ActivityEvent, User
    from sqlalchemy import select

    headers, _ = await _member(client, org_with_admin)
    me = (await client.get("/users/me", headers=headers)).json()

    async with SessionLocal() as session:
        user = (await session.execute(select(User).where(User.id == me["id"]))).scalar_one()
        for days_ago in (1, 2):
            session.add(
                ActivityEvent(
                    org_id=user.org_id, user_id=user.id, kind="session_started",
                    created_at=datetime.now(timezone.utc) - timedelta(days=days_ago),
                )
            )
        await session.commit()

    assert (await client.get("/users/me/streak", headers=headers)).json()["current"] == 2


async def test_no_activity_is_a_zero_not_a_crash(client, org_with_admin):
    headers, _ = await _member(client, org_with_admin)

    assert (await client.get("/users/me/streak", headers=headers)).json() == {
        "current": 0, "longest": 0
    }


# ── the crisis region actually reaches the crisis screen ─────────────────────
#
# The app shipped a region picker that wrote User.region and nothing read it back —
# every user saw one country's helplines regardless of what they picked. These pin the
# resolution, so a placebo control cannot come back silently.


async def test_the_person_choice_beats_the_org_default(client, org_with_admin):
    from app.models import Org, User
    from app.routers.users import effective_crisis_region

    org = Org(name="Acme", slug="acme-x", crisis_region="IN")
    user = User(email="a@b.c", name="A", role="user", password_hash="x", region="GB")
    assert effective_crisis_region(user, org) == "GB", "the person knows where they are"


async def test_the_org_default_applies_when_the_person_has_not_chosen():
    from app.models import Org, User
    from app.routers.users import effective_crisis_region

    org = Org(name="Acme", slug="acme-y", crisis_region="IN")
    user = User(email="a@b.c", name="A", role="user", password_hash="x", region="")
    assert effective_crisis_region(user, org) == "IN"


async def test_an_org_less_user_with_no_choice_resolves_to_unknown_not_a_guess():
    """"" means the engine serves the international directory. An org-less user must not
    inherit some other tenant's country."""
    from app.models import User
    from app.routers.users import effective_crisis_region

    user = User(email="a@b.c", name="A", role="user", password_hash="x", region="")
    assert effective_crisis_region(user, None) == ""


async def test_whitespace_is_not_a_choice():
    from app.models import Org, User
    from app.routers.users import effective_crisis_region

    org = Org(name="Acme", slug="acme-z", crisis_region="IN")
    user = User(email="a@b.c", name="A", role="user", password_hash="x", region="   ")
    assert effective_crisis_region(user, org) == "IN", "blanks must fall through, not win"


async def test_me_exposes_the_resolved_region_the_client_should_ask_for(client, org_with_admin):
    headers, _ = await _member(client, org_with_admin)
    await client.patch("/users/me", json={"region": "AU"}, headers=headers)
    me = (await client.get("/users/me", headers=headers)).json()
    assert me["region"] == "AU", "the raw choice stays visible for the picker"
    assert me["crisis_region"] == "AU", "and the resolved value is what the crisis screen uses"
