"""Bulk eligibility import — mostly a test that it refuses things.

The happy path is a loop. The interesting behaviour is what an HR export gets
told when it arrives carrying more than eligibility, because that file is the
most likely way for wellbeing data to reach a place it must never be.
"""
import uuid

import pytest
from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.admin_audit import AdminAuditLog
from app.models.organization import (
    ROLE_ANALYST,
    ROLE_BENEFITS_OWNER,
    Organization,
    OrgAdmin,
    OrgMembership,
)
from app.models.user import User
from app.services import eligibility_csv

PASSWORD = "password123"


async def _signup(client, prefix: str) -> str:
    email = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": PASSWORD, "name": "T"})
    assert r.status_code == 201, r.text
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return email


async def _org_for(admin_email: str, role: str = ROLE_BENEFITS_OWNER) -> uuid.UUID:
    async with SessionLocal() as db:
        org = Organization(name=f"Import Co {uuid.uuid4().hex[:6]}", seats_licensed=500)
        db.add(org)
        await db.flush()
        user_id = await db.scalar(select(User.id).where(User.email == admin_email))
        db.add(OrgAdmin(org_id=org.id, user_id=user_id, role=role))
        await db.commit()
        return org.id


async def _login(client, email: str):
    r = await client.post("/auth/login", data={"username": email, "password": PASSWORD})
    assert r.status_code == 200, r.text
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"


async def _seats(org_id: uuid.UUID) -> list[OrgMembership]:
    async with SessionLocal() as db:
        return list((await db.scalars(
            select(OrgMembership).where(OrgMembership.org_id == org_id)
        )).all())


# ---------------------------------------------------------------- the parser


def test_an_unrecognised_column_names_itself():
    """"Unknown column" alone makes an administrator guess, and guessing ends
    in a second upload of the same personal data."""
    with pytest.raises(eligibility_csv.CsvRejected) as exc:
        eligibility_csv.parse("email,mood,diagnosis\na@b.com,Low,F41.1\n")
    message = str(exc.value)
    assert "mood" in message and "diagnosis" in message
    assert "rejected rather than ignored" in message


def test_the_allowlist_stops_columns_no_denylist_would_have():
    """The point of an allowlist: it rejects the field nobody thought of."""
    for column in ("wellbeing_score", "eap_referral", "absence_reason", "risk_band"):
        with pytest.raises(eligibility_csv.CsvRejected, match=column):
            eligibility_csv.parse(f"email,{column}\na@b.com,3\n")


def test_headers_are_forgiving_about_form_and_strict_about_meaning():
    rows = eligibility_csv.parse("Email, External Ref ,Access End\na@b.com,EMP-1,2027-01-01\n")
    assert rows[0].values == {"email": "a@b.com", "external_ref": "EMP-1", "access_end": "2027-01-01"}
    # ...but a column that merely looks similar is still unknown.
    with pytest.raises(eligibility_csv.CsvRejected, match="access_ended_reason"):
        eligibility_csv.parse("email,access_ended_reason\na@b.com,left\n")


def test_a_file_with_no_rows_is_an_error_not_an_empty_success():
    """Reporting "0 added" for a file the administrator believes has 400 rows
    is the failure they would not notice."""
    for text in ("", "email,external_ref\n", "email,external_ref\n\n\n"):
        with pytest.raises(eligibility_csv.CsvRejected):
            eligibility_csv.parse(text)


def test_the_email_column_is_required():
    with pytest.raises(eligibility_csv.CsvRejected, match="email"):
        eligibility_csv.parse("external_ref,access_end\nEMP-1,2027-01-01\n")


def test_bounds_are_enforced_on_size_and_rows():
    big = "email\n" + "a@b.com\n" * (eligibility_csv.MAX_ROWS + 5)
    with pytest.raises(eligibility_csv.CsvRejected, match="rows"):
        eligibility_csv.parse(big)
    with pytest.raises(eligibility_csv.CsvRejected, match="KB"):
        eligibility_csv.parse("email\n" + "x" * (eligibility_csv.MAX_BYTES + 1))


def test_blank_lines_are_skipped_not_counted():
    rows = eligibility_csv.parse("email\na@b.com\n\nc@d.com\n")
    assert [r.line for r in rows] == [2, 4]   # line numbers still match the file


# ---------------------------------------------------------------- the route


async def test_a_file_carrying_health_data_imports_nothing_at_all(client):
    """Rejected whole, not column-by-column: dropping the offending column
    silently would teach the administrator that sending it was fine."""
    owner = await _signup(client, "owner")
    org_id = await _org_for(owner)
    member = await _signup(client, "member")
    await _login(client, owner)

    r = await client.post("/org/members/import", json={
        "csv": f"email,external_ref,diagnosis\n{member},EMP-1,F41.1\n",
    })
    assert r.status_code == 422
    assert "diagnosis" in r.json()["detail"]
    # The valid row in that file was NOT imported.
    assert await _seats(org_id) == []


async def test_valid_rows_import_and_the_rest_are_reported(client):
    owner = await _signup(client, "owner")
    org_id = await _org_for(owner)
    joined = await _signup(client, "joined")
    also = await _signup(client, "also")
    await _login(client, owner)

    stranger = f"stranger-{uuid.uuid4().hex[:8]}@test.app"
    r = await client.post("/org/members/import", json={
        "csv": (
            "email,external_ref,access_end\n"
            f"{joined},EMP-1,2027-01-01\n"
            f"{stranger},EMP-2,\n"
            f"{also},EMP-3,\n"
            "not-an-address,EMP-4,\n"
        ),
    })
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["added"] == 2 and body["skipped"] == 2

    by_ref = {row["external_ref"]: row for row in body["rows"]}
    assert by_ref["EMP-1"]["outcome"] == "added"
    # No account, so no seat — and no account is created for them either.
    assert by_ref["EMP-2"]["outcome"] == "no_account"
    assert by_ref["EMP-4"]["outcome"] == "invalid"
    assert [row["line"] for row in body["rows"]] == [2, 3, 4, 5]

    seats = await _seats(org_id)
    assert sorted(s.external_ref for s in seats) == ["EMP-1", "EMP-3"]
    assert next(s for s in seats if s.external_ref == "EMP-1").access_end is not None

    async with SessionLocal() as db:
        assert await db.scalar(select(User).where(User.email == stranger)) is None


async def test_the_report_never_contains_an_email_address(client):
    """The seat list is deliberately not a roster of who holds an account, and
    an import report is part of the seat list."""
    owner = await _signup(client, "owner")
    await _org_for(owner)
    member = await _signup(client, "member")
    await _login(client, owner)

    stranger = f"stranger-{uuid.uuid4().hex[:8]}@test.app"
    r = await client.post("/org/members/import", json={
        "csv": f"email,external_ref\n{member},EMP-1\n{stranger},EMP-2\n",
    })
    assert r.status_code == 200
    assert member not in r.text and stranger not in r.text


async def test_an_existing_seat_is_reported_not_duplicated(client):
    owner = await _signup(client, "owner")
    org_id = await _org_for(owner)
    member = await _signup(client, "member")
    await _login(client, owner)

    first = await client.post("/org/members/import", json={
        "csv": f"email,external_ref\n{member},EMP-1\n",
    })
    assert first.json()["added"] == 1
    again = await client.post("/org/members/import", json={
        "csv": f"email,external_ref\n{member},EMP-1\n",
    })
    assert again.json()["added"] == 0
    assert again.json()["rows"][0]["outcome"] == "already_member"
    assert len(await _seats(org_id)) == 1


async def test_the_trail_records_one_action_not_five_hundred(client):
    """An audit log nobody can read is not accountability."""
    owner = await _signup(client, "owner")
    org_id = await _org_for(owner)
    members = [await _signup(client, f"m{i}") for i in range(3)]
    await _login(client, owner)

    rows = "".join(f"{m},EMP-{i}\n" for i, m in enumerate(members))
    r = await client.post("/org/members/import", json={"csv": f"email,external_ref\n{rows}"})
    assert r.json()["added"] == 3

    async with SessionLocal() as db:
        logged = list((await db.scalars(
            select(AdminAuditLog).where(AdminAuditLog.org_id == org_id)
        )).all())
    assert [row.action for row in logged] == ["org.seat_import"]
    assert logged[0].detail["added"] == 3
    # Counts only — the addresses were never stored, and are not stored here.
    for member in members:
        assert member not in str(logged[0].detail)


async def test_an_analyst_cannot_import(client):
    """Reading reports and changing eligibility are different jobs."""
    owner = await _signup(client, "analyst")
    org_id = await _org_for(owner, role=ROLE_ANALYST)
    member = await _signup(client, "member")
    await _login(client, owner)

    r = await client.post("/org/members/import", json={
        "csv": f"email,external_ref\n{member},EMP-1\n",
    })
    assert r.status_code == 403
    assert await _seats(org_id) == []


async def test_a_group_from_another_organisation_is_not_found(client):
    """The group is applied to every row, so it is checked before any of them."""
    other_owner = await _signup(client, "other")
    other_org = await _org_for(other_owner)
    async with SessionLocal() as db:
        from app.models.organization import EligibilityGroup

        group = EligibilityGroup(org_id=other_org, name="Theirs")
        db.add(group)
        await db.commit()
        group_id = str(group.id)

    owner = await _signup(client, "owner")
    org_id = await _org_for(owner)
    member = await _signup(client, "member")
    await _login(client, owner)

    r = await client.post("/org/members/import", json={
        "csv": f"email,external_ref\n{member},EMP-1\n",
        "group_id": group_id,
    })
    assert r.status_code == 404
    assert await _seats(org_id) == []


async def test_unknown_top_level_fields_are_refused(client):
    """`extra="forbid"` on the request body, matching the single-invite route:
    the CSV is not the only place someone can send something unexpected."""
    owner = await _signup(client, "owner")
    await _org_for(owner)
    await _login(client, owner)

    r = await client.post("/org/members/import", json={
        "csv": "email\na@b.com\n",
        "notify_manager": True,
    })
    assert r.status_code == 422
