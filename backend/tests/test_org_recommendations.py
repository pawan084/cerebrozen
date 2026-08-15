"""Counts-only org recommendations (audit J#3a — services/org_recommendations).

The invariant that carries the feature is the sibling's, kept test-asserted at
the same boundary they asserted it: **a suppressed group never reaches any LLM
payload**. Everything else — the gate, the honest keyless fallback, the
no-wellbeing vocabulary — is downstream of that.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.organization import (
    ROLE_ANALYST,
    ROLE_BENEFITS_OWNER,
    STATUS_ACTIVE,
    EligibilityGroup,
    Organization,
    OrgAdmin,
    OrgMembership,
)
from app.models.user import User


async def _org_with_admin(client, *, role=ROLE_BENEFITS_OWNER):
    email = f"orgadmin-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "A"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    async with SessionLocal() as db:
        org = Organization(name=f"Org {uuid.uuid4().hex[:6]}", seats_licensed=100, reporting_threshold=20)
        db.add(org)
        await db.flush()
        uid = await db.scalar(select(User.id).where(User.email == email))
        db.add(OrgAdmin(org_id=org.id, user_id=uid, role=role))
        await db.commit()
        return org.id


async def _group(org_id, name, eligible, activated):
    """A group of `eligible` memberships, `activated` of them status=active.

    Counts are pure membership-row aggregates (`_count`), and `user_id` is
    non-nullable, so every seat gets a user row — status carries activation.
    """
    async with SessionLocal() as db:
        g = EligibilityGroup(org_id=org_id, name=name)
        db.add(g)
        await db.flush()
        for i in range(eligible):
            member = User(email=f"m-{uuid.uuid4().hex[:12]}@test.app", hashed_password="x", name="M")
            db.add(member)
            await db.flush()
            db.add(OrgMembership(
                org_id=org_id, group_id=g.id, user_id=member.id,
                status=STATUS_ACTIVE if i < activated else "invited",
                external_ref=f"ref-{i}",
            ))
        await db.commit()


async def test_requires_an_org_admin(client):
    email = f"user-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "U"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    assert (await client.get("/org/recommendations")).status_code == 403


async def test_keyless_fallback_is_honest_and_never_empty(client):
    await _org_with_admin(client)
    r = await client.get("/org/recommendations")
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["source"] == "rule"
    assert body["recommendations"], "an empty dashboard on a sweep failure — the rule this exists to prevent"
    # Counts-only vocabulary: no priority stronger than advisory.
    assert all(rec["priority"] in {"advisory", "informational"} for rec in body["recommendations"])


async def test_an_analyst_may_read(client):
    """Read-only surface derived from the reports analysts already see."""
    await _org_with_admin(client, role=ROLE_ANALYST)
    assert (await client.get("/org/recommendations")).status_code == 200


async def test_suppressed_groups_never_reach_the_llm(client, monkeypatch):
    """The load-bearing invariant, asserted at the prompt boundary (the
    sibling's test contract, ported with our threshold of 20)."""
    org_id = await _org_with_admin(client)
    await _group(org_id, "Engineering", eligible=30, activated=12)   # reportable
    await _group(org_id, "Founders", eligible=3, activated=2)        # suppressed (< 20)

    captured: list[str] = []

    async def _capture(system, prompt, max_tokens=1024):
        captured.append(prompt)
        return None   # fall through to the rule fallback

    from app.services import ai as ai_service
    monkeypatch.setattr(ai_service, "complete_json", _capture)

    r = await client.get("/org/recommendations")
    assert r.status_code == 200

    assert captured, "the reportable group should have produced an LLM attempt"
    payload = captured[-1]
    assert "Engineering" in payload
    assert "Founders" not in payload, "a suppressed group reached the LLM payload"
    # And no digit of the suppressed group's counts either. Comma-anchored:
    # a bare "eligible 3" matches inside Engineering's "eligible 30".
    assert "eligible 3," not in payload


async def test_ai_branch_shapes_and_caps(client, monkeypatch):
    """Mocked-model 'ai' branch: junk priorities collapse to informational,
    junk items are dropped, and the count caps at three."""
    org_id = await _org_with_admin(client)
    await _group(org_id, "Engineering", eligible=25, activated=10)

    async def _fake_json(system, prompt, max_tokens=1024):
        return {"recommendations": [
            {"title": "A" * 200, "body": "B" * 900, "priority": "URGENT!!"},
            {"no_title": True},                                   # junk → dropped
            {"title": "Second", "body": "ok", "priority": "advisory"},
            {"title": "Third", "body": "ok", "priority": "informational"},
            {"title": "Fourth", "body": "ok", "priority": "advisory"},
        ]}

    from app.services import ai as ai_service
    monkeypatch.setattr(ai_service, "complete_json", _fake_json)

    body = (await client.get("/org/recommendations")).json()
    assert body["source"] == "ai"
    recs = body["recommendations"]
    assert len(recs) == 3                                          # capped
    assert len(recs[0]["title"]) <= 70 and len(recs[0]["body"]) <= 240
    assert recs[0]["priority"] == "informational"                  # junk priority collapsed
