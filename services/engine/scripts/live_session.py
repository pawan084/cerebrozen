"""Drive a real multi-turn coaching session against the live model on SQLite.

Runs a first session for a user through the coaching arc, then a second session for the
SAME user to show it now reads as `repeat`. Prints each streamed reply plus the machine-read
summary (stage, coaching_path, tokens, cost). Requires OPENAI_API_KEY in the environment/.env.

    python -m scripts.live_session
"""
from __future__ import annotations

import asyncio

from app.config import settings
from app.service import get_service
from app.stores import mongo


FIRST_SESSION_TURNS = [
    "Hi — I want to get better at giving my team direct, honest feedback.",
    "I'm an engineering manager. I avoid it because I don't want to demotivate people.",
    "The specific case is a senior engineer whose code reviews are harsh with juniors.",
    "I think I'm scared they'll push back and it'll damage the relationship.",
    "Yeah, staying quiet is really costing the team's morale.",
    "A good outcome is a calm, specific conversation this week. I'll commit to that.",
]

SECOND_SESSION_TURNS = [
    "I'm back — I had that feedback conversation and it went okay.",
    "I'd like to work on staying consistent with it now.",
]


async def _drive(user_id: str, first_message: str, follow_ups: list[str],
                 session_id: str | None = None) -> str:
    svc = get_service()
    print("\n" + "=" * 70)

    async def run(events, label: str) -> dict:
        print(f"\n─── {label} ───")
        reply, done = "", {}
        async for ev in events:
            if ev["type"] == "status":
                print(f"   · {ev['message']}")
            elif ev["type"] == "delta":
                reply += ev["text"]
            elif ev["type"] == "error":
                print(f"   ! error: {ev['error']}")
            elif ev["type"] == "done":
                done = ev
        print(f"   COACH: {reply.strip()[:600]}")
        print(f"   [stage={done.get('stage')} path={done.get('coaching_path')} "
              f"tok_in={done.get('tokens_in')} tok_out={done.get('tokens_out')} "
              f"cost=${done.get('cost_usd')} {done.get('latency_ms')}ms]")
        return done

    done = await run(
        svc.start_session(user_id, first_message, session_id=session_id), "USER (start)"
    )
    sid = done.get("session_id")
    for i, msg in enumerate(follow_ups, 1):
        print(f"\n   USER: {msg}")
        done = await run(svc.run_turn(user_id, sid, msg), f"turn {i + 1}")
    return sid


async def main() -> None:
    if not settings.openai_api_key:
        raise SystemExit("OPENAI_API_KEY not set — add it to .env first.")
    print(f"backend={settings.effective_backend}  model={settings.model_coaching}")
    mongo.connect()

    user = "live-demo-user"
    print("\n########## SESSION 1 (expected: fresh) ##########")
    await _drive(user, FIRST_SESSION_TURNS[0], FIRST_SESSION_TURNS[1:])

    print("\n\n########## SESSION 2 (expected: repeat) ##########")
    from app.graph.orchestrator import build_context_package
    pkg = build_context_package(user, "probe")
    print(f"context package now says userRepeatFresh = {pkg['userRepeatFresh']!r} "
          f"(sessions_completed={pkg.get('session_count')})")
    await _drive(user, SECOND_SESSION_TURNS[0], SECOND_SESSION_TURNS[1:])


if __name__ == "__main__":
    asyncio.run(main())
