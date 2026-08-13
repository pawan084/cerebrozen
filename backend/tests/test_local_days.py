"""Wave 21 pins: "today" is the user's day, not UTC's or the container's.

Register C59-C65 — the streak, habit dots, sleep windows, pattern buckets and
program day all read the user's own calendar via app/core/localtime.
"""
import ast
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from app.core.database import SessionLocal
from app.core.localtime import local_date, local_today, tz_for
from app.models.mood import MoodLog
from app.models.user import User
from tests.dates import ACCOUNT_TZ

IST = "Asia/Kolkata"


def test_localtime_helpers():
    # Junk falls back to UTC instead of raising (legacy rows predate the
    # wave-17 IANA validation).
    assert str(tz_for("Not/AZone")) == "UTC"
    assert str(tz_for("")) == "UTC"
    # 19:30 UTC is already "tomorrow" in IST (+05:30).
    d = datetime(2026, 1, 1, 19, 30, tzinfo=timezone.utc)
    assert local_date(d, IST).isoformat() == "2026-01-02"
    assert local_date(d, "UTC").isoformat() == "2026-01-01"


async def test_streak_counts_the_users_local_days(client):
    """Two check-ins on ONE IST day used to count as two UTC days — the
    server streak disagreed with the Android/iOS local-day count by one for
    every evening check-in east of UTC."""
    email = f"tz-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "T"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    assert (await client.patch("/users/me", json={"timezone": IST})).status_code == 200
    uid = uuid.UUID((await client.get("/users/me")).json()["id"])

    # One IST calendar day D, touched at 01:00 and again at 20:00 local —
    # which straddles UTC midnight (19:30 UTC on D-1, 14:30 UTC on D).
    day = local_today(IST)
    ist = ZoneInfo(IST)
    async with SessionLocal() as s:
        for hour in (1, 20):
            m = MoodLog(user_id=uid, mood="Good", intensity=3)
            m.created_at = datetime.combine(day, datetime.min.time(), tzinfo=ist).replace(hour=hour)
            s.add(m)
        await s.commit()

    streak = (await client.get("/users/me/streak")).json()
    assert streak["current"] == 1, streak
    assert sum(1 for d in streak["week"] if d["active"]) == 1
    # The active dot sits on the IST day both check-ins belong to.
    active = [d["date"] for d in streak["week"] if d["active"]]
    assert active == [day.isoformat()]


def test_the_account_timezone_default_is_what_the_helpers_assume():
    """`tests/dates` computes the account's day from a constant. If the model
    default moves, every fixture in the suite silently starts describing the
    wrong day — so the coupling is pinned rather than commented."""
    assert User.__table__.c.timezone.default.arg == ACCOUNT_TZ


def test_no_test_seeds_a_fixture_from_the_containers_clock():
    """`date.today()` is banned in this suite.

    It is the CONTAINER's day — UTC here and in CI — while every endpoint with
    an opinion about "today" answers in the user's timezone. Three tests were
    built that way and passed all morning and failed all evening; they had been
    wrong since the localtime fix corrected the app and left the tests behind.

    Checked over the AST rather than the text, so the explanations above (and
    this docstring) do not count as violations. Use `tests.dates.account_today`
    / `account_day` / `account_iso`, or `local_today(user.timezone)` when the
    test holds the row.
    """
    offenders: list[str] = []
    for path in sorted(Path(__file__).parent.glob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in ast.walk(tree):
            if (
                isinstance(node, ast.Call)
                and isinstance(node.func, ast.Attribute)
                and node.func.attr == "today"
                and isinstance(node.func.value, ast.Name)
                and node.func.value.id == "date"
            ):
                offenders.append(f"{path.name}:{node.lineno}")
    assert offenders == [], (
        "these seed fixtures from the container's clock instead of the account's day "
        f"(see tests/dates.py): {', '.join(offenders)}"
    )
