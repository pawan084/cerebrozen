"""Seed a demo environment: synthetic users you can actually coach.

Why this exists: the app boots fine with an empty store, but every interesting path
— repeat users, the 7-day action check-in, the pattern mirror, "what did I commit
to last time" — only lights up when there is *prior* data. Without it a demo can
only ever show a fresh user's first turn, and the repeat-user flows cannot be
exercised at all.

Creates four users, each entering the graph down a different path:

    demo-fresh     brand-new; runs intake from scratch
    demo-repeat    completed a prior session 2 days ago; intake vars, actions, insights
    demo-checkin   same, but 9 days ago → the 7-day action check-in is DUE
    demo-ch        capability-shaped goal → the CH (3-phase) coaching path

REQUIRES MONGO. The stores are Mongo-only — with no Mongo they silently no-op, so
prior-session data cannot exist and the repeat-user paths cannot be demoed at all.
`docker compose up -d mongo redis` is enough; see docs/DEMO.md.

Run:  python -m scripts.seed_demo [--reset]
"""
from __future__ import annotations

import argparse
import sys
from datetime import datetime, timedelta, timezone
from typing import List

from app import config
from app.stores import agentic, conversation

# Stable ids — the demo picker in the UI refers to them by name.
FRESH, REPEAT, CHECKIN, CH = "demo-fresh", "demo-repeat", "demo-checkin", "demo-ch"
ALL_USERS = [FRESH, REPEAT, CHECKIN, CH]

# A realistic prior session — this is what the coach "remembers" next time.
_PRIOR_TURNS = [
    ("I keep avoiding a hard conversation with my manager about my workload.",
     "It sounds like this has been sitting with you a while. What makes it hard to raise?"),
    ("I think I'll look like I can't cope.",
     "So the risk you're weighing is how it lands on your competence. If a colleague you "
     "respected said the same thing to you, what would you think of them?"),
    ("Honestly? I'd think they were being sensible.",
     "That's a real gap between the standard you hold others to and the one you hold "
     "yourself to. What would 'sensible' look like for you this week?"),
]

_INTAKE_VARS = {
    "userRoleContext": "Engineering manager, 6 direct reports, 2 years in role",
    "coachingHistory": "One prior engagement in 2023 — useful but unstructured",
    "coachingNeeds": "Be more direct without feeling aggressive",
    "coaching_style_preference": "stretching",
    "userMotivations": "Grow into a director role within 18 months",
    "coachability_score": 78,
}

_ACTIONS = [
    {"full_text": "Book a 30-minute 1:1 with my manager and name the workload issue directly.",
     "expected_outcome": "A concrete re-prioritisation of at least one project."},
    {"full_text": "Write down the three things I'd tell a colleague in my situation, before the meeting.",
     "expected_outcome": "Walk in with my own advice in hand instead of improvising."},
]

_INSIGHTS = [
    {"insight_title": "You hold yourself to a standard you'd call unfair in anyone else",
     "insight_description": "You called a colleague doing the same thing 'sensible'. The double "
                            "standard is what's blocking the conversation — not your manager."},
]

_PATTERN = ("You move toward clarity fast once you say the thing out loud. The delay is always "
            "before the first sentence, not after it.")


def _backdate(user_id: str, days: int) -> None:
    """Backdate the user's actions/insights so the 7-day check-in gate can fire.

    The scheduler decides eligibility from each action's `session_date` (see
    app/checkin_scheduler.py: eligible once `session_date + due_days <= today`).
    The public write API stamps "now", so a seeder has to reach into the doc — this
    is the one place that's legitimate, and it is why the check-in path is testable
    locally at all.
    """
    coll = agentic._collection()
    if coll is None:
        return
    when = datetime.now(timezone.utc) - timedelta(days=days)

    # Read-modify-write, NOT Mongo's `actions.$[].ts` all-positional operator.
    # `$[]` is a Mongo-only extension; on the Postgres backend it wrote a literal key
    # called "actions.$[].ts" and the backdating silently did nothing — so the check-in
    # never became due and demo-checkin looked broken. A plain read-modify-write is
    # correct on BOTH backends, which is what a seeder should be.
    doc = coll.find_one({"user_id": user_id}) or {}
    for field in ("actions", "insights"):
        items = doc.get(field) or []
        for it in items:
            it["ts"] = when.isoformat()
            it["session_date"] = when.date().isoformat()
        if items:
            coll.update_one({"user_id": user_id}, {"$set": {field: items}})


def _seed_prior_session(user_id: str, session_id: str, *, days_ago: int) -> None:
    """One completed prior session: transcript + intake vars + actions + insights."""
    last = len(_PRIOR_TURNS) - 1
    for i, (user_msg, bot_msg) in enumerate(_PRIOR_TURNS):
        conversation.record_turn(
            session_id=session_id,
            user_id=user_id,
            user_message=user_msg,
            bot_text=bot_msg,
            agent_name="core_coaching_agent",
            ended=(i == last),   # marks the session closed — there is no close_session()
        )
    agentic.save_intake_vars(user_id, dict(_INTAKE_VARS))
    agentic.append_actions_insights(
        user_id,
        actions=[dict(a) for a in _ACTIONS],
        insights=[dict(i) for i in _INSIGHTS],
        session_id=session_id,
        agent_name="dynamic_actions_insights_agent",
    )
    agentic.save_pattern_mirror(user_id, _PATTERN)
    _backdate(user_id, days_ago)
    print(f"  · prior session {session_id}, {days_ago}d ago — {len(_PRIOR_TURNS)} turns, "
          f"{len(_ACTIONS)} actions, {len(_INSIGHTS)} insights")


def _reset(user_ids: List[str]) -> None:
    for coll in (agentic._collection(), conversation._collection()):
        if coll is not None:
            coll.delete_many({"user_id": {"$in": user_ids}})
    print(f"reset — cleared {len(user_ids)} demo users\n")


def main() -> int:
    ap = argparse.ArgumentParser(description="Seed demo users.")
    ap.add_argument("--reset", action="store_true", help="wipe the demo users first")
    args = ap.parse_args()

    if agentic._collection() is None:
        print(
            "ERROR: no Mongo. The stores are Mongo-only, so prior-session data cannot be\n"
            "       written and the repeat-user / check-in paths cannot be demoed.\n\n"
            "       docker compose up -d mongo redis\n"
            "       MONGO_DB_URL=mongodb://localhost:27017 python -m scripts.seed_demo\n",
            file=sys.stderr,
        )
        return 1

    print(f"seeding demo users into {config.MONGO_BACKEND_DB}\n")
    if args.reset:
        _reset(ALL_USERS)

    print(f"{FRESH} — brand-new user")
    print("  · nothing seeded; that IS the fresh path (intake runs from scratch)")

    print(f"\n{REPEAT} — repeat user, prior session 2 days ago")
    _seed_prior_session(REPEAT, "demo-prior-repeat", days_ago=2)

    print(f"\n{CHECKIN} — repeat user, action check-in DUE")
    _seed_prior_session(CHECKIN, "demo-prior-checkin", days_ago=9)
    print("  · actions are >7d old → the check-in gate should fire on the next session")

    print(f"\n{CH} — capability path")
    _seed_prior_session(CH, "demo-prior-ch", days_ago=3)
    agentic.save_intake_vars(CH, {
        **_INTAKE_VARS,
        "coachingNeeds": "Build the capability to run a department, not just a team",
        "coaching_style_preference": "directive",
    })
    print("  · capability-shaped goal → challenge_context should score this CH")

    print("\ndone. open the flow view and watch the graph light up:")
    print("  http://127.0.0.1:8000/flow")
    print("  users: " + "  ".join(ALL_USERS) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
