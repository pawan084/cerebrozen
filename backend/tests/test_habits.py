"""Goals and habits — the things the user defines, not the app.

The tests lean on the design decisions rather than the CRUD, because the CRUD is
the boring part: a habit that can't say "you broke your streak", a goal that can
be released without it reading as failure, and a decompose that feeds the ONE
plan rather than starting a second list.
"""
import uuid
from datetime import date, timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.habit import Goal, Habit, HabitCompletion
from app.models.plan import Plan
from app.models.user import User


async def _signup(client, prefix="habit"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "H"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _user_id(addr: str) -> uuid.UUID:
    async with SessionLocal() as s:
        return (await s.scalar(select(User).where(User.email == addr))).id


# ── Goals ───────────────────────────────────────────────────────────────

async def test_goal_crud(client):
    await _signup(client)
    created = await client.post("/goals", json={"title": "Sleep before midnight", "why": "Mornings hurt"})
    assert created.status_code == 201
    gid = created.json()["id"]
    assert created.json()["status"] == "active"

    assert [g["title"] for g in (await client.get("/goals")).json()] == ["Sleep before midnight"]

    edited = await client.patch(f"/goals/{gid}", json={"title": "Sleep by 11"})
    assert edited.json()["title"] == "Sleep by 11"

    assert (await client.delete(f"/goals/{gid}")).status_code == 204
    assert (await client.get("/goals")).json() == []


async def test_a_goal_can_be_released_not_only_achieved(client):
    """Letting a goal go is a legitimate outcome; the vocabulary says so."""
    await _signup(client)
    gid = (await client.post("/goals", json={"title": "Run a marathon"})).json()["id"]

    released = await client.patch(f"/goals/{gid}", json={"status": "released"})
    assert released.status_code == 200
    assert released.json()["status"] == "released"

    # Resolved goals leave the default list but stay retrievable.
    assert (await client.get("/goals")).json() == []
    assert len((await client.get("/goals?include_resolved=true")).json()) == 1


async def test_unknown_goal_status_is_refused(client):
    await _signup(client)
    gid = (await client.post("/goals", json={"title": "x"})).json()["id"]
    assert (await client.patch(f"/goals/{gid}", json={"status": "failed"})).status_code == 422


async def test_decompose_replaces_the_active_plan_rather_than_adding_one(client):
    """One plan at a time — two competing lists is how a calm app becomes a
    task manager."""
    addr = await _signup(client)
    uid = await _user_id(addr)
    gid = (await client.post("/goals", json={"title": "Wind down earlier"})).json()["id"]

    first = await client.post("/plans/generate")
    assert first.status_code == 201

    decomposed = await client.post(f"/goals/{gid}/decompose")
    assert decomposed.status_code == 201
    assert decomposed.json()["steps"]

    async with SessionLocal() as s:
        active = (await s.scalars(
            select(Plan).where(Plan.user_id == uid, Plan.active.is_(True))
        )).all()
    assert len(active) == 1


async def test_another_users_goal_is_404(client):
    await _signup(client, "owner")
    gid = (await client.post("/goals", json={"title": "private"})).json()["id"]

    await _signup(client, "stranger")
    assert (await client.get("/goals")).json() == []
    assert (await client.patch(f"/goals/{gid}", json={"title": "hi"})).status_code == 404
    assert (await client.delete(f"/goals/{gid}")).status_code == 404
    assert (await client.post(f"/goals/{gid}/decompose")).status_code == 404


async def test_goals_are_exported_and_cascade(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    await client.post("/goals", json={"title": "exportable"})

    body = (await client.get("/users/me/export")).json()
    assert [g["title"] for g in body["goals"]] == ["exportable"]

    assert (await client.delete("/users/me")).status_code == 204
    async with SessionLocal() as s:
        assert (await s.scalars(select(Goal).where(Goal.user_id == uid))).all() == []


# ── Habits ──────────────────────────────────────────────────────────────

async def test_habit_crud_and_completion_toggle(client):
    await _signup(client)
    created = await client.post(
        "/habits", json={"title": "Ten minutes outside", "cue": "after lunch", "target_per_week": 5}
    )
    assert created.status_code == 201
    hid = created.json()["id"]
    assert created.json()["done_today"] is False
    assert created.json()["recent_days"] == []

    done = await client.post(f"/habits/{hid}/complete")
    assert done.json()["done_today"] is True
    assert done.json()["recent_days"] == [date.today().isoformat()]

    # A mis-tap must never be permanent.
    undone = await client.delete(f"/habits/{hid}/complete")
    assert undone.json()["done_today"] is False


async def test_completing_twice_is_still_one_day(client):
    await _signup(client)
    hid = (await client.post("/habits", json={"title": "Water"})).json()["id"]
    await client.post(f"/habits/{hid}/complete")
    second = await client.post(f"/habits/{hid}/complete")
    assert second.json()["recent_days"] == [date.today().isoformat()]


async def test_recent_days_is_a_window_not_a_streak(client):
    """A gap is just a gap — nothing in the payload can say "you broke it"."""
    addr = await _signup(client)
    uid = await _user_id(addr)
    hid = (await client.post("/habits", json={"title": "Stretch"})).json()["id"]

    today = utcnow().date()
    async with SessionLocal() as s:
        # Yesterday missed on purpose; a streak model would report 1, this reports both days.
        for offset in (0, 2):
            s.add(HabitCompletion(
                habit_id=uuid.UUID(hid), user_id=uid, day=today - timedelta(days=offset)
            ))
        await s.commit()

    row = (await client.get("/habits")).json()[0]
    assert len(row["recent_days"]) == 2
    assert "streak" not in row


async def test_completions_older_than_a_week_drop_out_of_the_window(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    hid = (await client.post("/habits", json={"title": "Old"})).json()["id"]
    async with SessionLocal() as s:
        s.add(HabitCompletion(
            habit_id=uuid.UUID(hid), user_id=uid, day=utcnow().date() - timedelta(days=30)
        ))
        await s.commit()
    assert (await client.get("/habits")).json()[0]["recent_days"] == []


async def test_archiving_hides_without_deleting(client):
    await _signup(client)
    hid = (await client.post("/habits", json={"title": "Paused"})).json()["id"]
    await client.patch(f"/habits/{hid}", json={"archived": True})

    assert (await client.get("/habits")).json() == []
    assert len((await client.get("/habits?include_archived=true")).json()) == 1


async def test_another_users_habit_is_404(client):
    await _signup(client, "owner")
    hid = (await client.post("/habits", json={"title": "private"})).json()["id"]

    await _signup(client, "stranger")
    assert (await client.post(f"/habits/{hid}/complete")).status_code == 404
    assert (await client.patch(f"/habits/{hid}", json={"title": "hi"})).status_code == 404
    assert (await client.delete(f"/habits/{hid}")).status_code == 404


async def test_deleting_a_habit_takes_its_completions(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    hid = (await client.post("/habits", json={"title": "Gone"})).json()["id"]
    await client.post(f"/habits/{hid}/complete")

    assert (await client.delete(f"/habits/{hid}")).status_code == 204
    async with SessionLocal() as s:
        rows = (await s.scalars(
            select(HabitCompletion).where(HabitCompletion.habit_id == uuid.UUID(hid))
        )).all()
    assert rows == []


async def test_account_delete_cascades_habits(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    hid = (await client.post("/habits", json={"title": "Goes away"})).json()["id"]
    await client.post(f"/habits/{hid}/complete")

    assert (await client.delete("/users/me")).status_code == 204
    async with SessionLocal() as s:
        assert (await s.scalars(select(Habit).where(Habit.user_id == uid))).all() == []
        assert (await s.scalars(
            select(HabitCompletion).where(HabitCompletion.user_id == uid)
        )).all() == []
