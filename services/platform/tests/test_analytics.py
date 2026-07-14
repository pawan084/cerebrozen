"""HR analytics: first-party ingest and the k-anonymity floor.

The floor is the load-bearing privacy mechanism (docs/SECURITY.md): a
behavioral metric from fewer than COHORT_FLOOR distinct people must never
leave the aggregation layer."""

import uuid

import pytest

from app import config
from app.db import SessionLocal
from app.models import ActivityEvent, User


async def _seed_events(org_id: str, people: int, kinds: dict[str, int]) -> None:
    """`people` synthetic members each emit `kinds` (kind -> count)."""
    async with SessionLocal() as session:
        for i in range(people):
            user = User(
                org_id=org_id,
                email=f"synthetic-{uuid.uuid4().hex[:10]}@x.example",
                password_hash="x",
            )
            session.add(user)
            await session.flush()
            for kind, n in kinds.items():
                for _ in range(n):
                    session.add(
                        ActivityEvent(org_id=org_id, user_id=user.id, kind=kind)
                    )
        await session.commit()


async def test_members_report_their_own_beats(client, org_with_admin):
    r = await client.post(
        "/events/coaching",
        json={"kind": "session_started"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 202


async def test_bogus_kinds_and_internal_staff_are_refused(client, internal, org_with_admin):
    r = await client.post(
        "/events/coaching",
        json={"kind": "keystrokes_per_minute"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 400, "the kind whitelist is the content firewall"
    headers, _ = internal
    r = await client.post(
        "/events/coaching", json={"kind": "session_started"}, headers=headers
    )
    assert r.status_code == 400


async def test_small_cohorts_are_suppressed(client, org_with_admin, monkeypatch):
    """3 people < floor of 8: every behavioral metric is null, and the
    payload says why (suppressed + the floor)."""
    await _seed_events(
        org_with_admin["org"]["id"], people=3,
        kinds={"session_started": 4, "session_completed": 3, "action_saved": 2, "action_completed": 1},
    )
    body = (
        await client.get("/orgs/me/analytics", headers=org_with_admin["admin_headers"])
    ).json()
    assert body["cohort_floor"] == config.COHORT_FLOOR == 8
    for name, metric in body["metrics"].items():
        assert metric["suppressed"] is True, f"{name} leaked a small cohort"
        assert metric["value"] is None
    # Administrative seat facts stay visible — the roster already shows them.
    assert body["seats"]["active_members"] == 4  # admin + 3 synthetic


async def test_large_cohorts_report_real_numbers(client, org_with_admin, internal, monkeypatch):
    org_id = org_with_admin["org"]["id"]
    headers, _ = internal
    await client.patch(f"/orgs/{org_id}", json={"seats_total": 20}, headers=headers)
    await _seed_events(
        org_id, people=8,
        kinds={"session_started": 2, "session_completed": 1, "action_saved": 2, "action_completed": 1},
    )
    body = (
        await client.get("/orgs/me/analytics", headers=org_with_admin["admin_headers"])
    ).json()
    m = body["metrics"]
    assert m["sessions_started"] == {"value": 16, "suppressed": False}
    assert m["sessions_completed"] == {"value": 8, "suppressed": False}
    assert m["session_completion_rate"] == {"value": 0.5, "suppressed": False}
    assert m["actions_saved"]["value"] == 16
    assert m["action_completion_rate"] == {"value": 0.5, "suppressed": False}
    assert m["active_coaching_users"] == {"value": 8, "suppressed": False}


async def test_a_rate_never_outlives_its_suppressed_component(client, org_with_admin, monkeypatch):
    """8 people started sessions, but only 2 completed any: the completion
    RATE must suppress, or it reveals what the completed-count hid."""
    org_id = org_with_admin["org"]["id"]
    await _seed_events(org_id, people=6, kinds={"session_started": 1})
    await _seed_events(
        org_id, people=2, kinds={"session_started": 1, "session_completed": 1}
    )
    body = (
        await client.get("/orgs/me/analytics", headers=org_with_admin["admin_headers"])
    ).json()
    m = body["metrics"]
    assert m["sessions_started"]["suppressed"] is False  # 8 contributors
    assert m["sessions_completed"]["suppressed"] is True  # 2 contributors
    assert m["session_completion_rate"]["suppressed"] is True


async def test_plain_users_cannot_read_analytics(client, org_with_admin):
    from tests.test_platform import _invite_and_accept

    tokens = await _invite_and_accept(
        client, org_with_admin["admin_headers"], "nosy@acme.example"
    )
    r = await client.get(
        "/orgs/me/analytics",
        headers={"Authorization": f"Bearer {tokens['access_token']}"},
    )
    assert r.status_code == 403
