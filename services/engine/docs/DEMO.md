# Demo environment

Four synthetic users, each entering the graph down a **different path** — so a demo shows the
routing actually branching, not one happy path four times.

```bash
cp .env.demo .env
docker compose up -d mongo redis        # not optional — see below
python -m scripts.seed_demo             # seeds the four users
uvicorn app.main:app --reload --port 8000
open http://127.0.0.1:8000/flow
```

## The users

| User | Seeded with | Enters at | Shows off |
|---|---|---|---|
| `demo-fresh` | nothing | `intake` | The first-time journey: the 8-question Coachable Index from scratch. |
| `demo-repeat` | a completed session 2 days ago | `challenge` | Repeat users **skip intake** — the coach already knows them. |
| `demo-checkin` | the same, but 9 days ago | `checkin` | The 7-day gate fires: the coach opens by asking how the committed actions went. |
| `demo-ch` | a capability-shaped goal | `challenge` → `capability` | The CH path: 3 phases (Goals → Commitments → Development). |

Verified live — each one really does route differently:

```
demo-fresh     safety → profile_read → intake
demo-repeat    safety → profile_read → challenge
demo-checkin   safety → profile_read → checkin
demo-ch        safety → profile_read → challenge
```

## Why Mongo is not optional

The stores are **Mongo-only**. Without it they silently no-op — which means no prior-session
data can exist, and every interesting path (repeat user, action check-in, "what did you commit
to last time") is unreachable. A demo without Mongo can only ever show a fresh user's first
turn. `docker compose up -d mongo redis` is the whole requirement.

`scripts/seed_demo.py` fails loudly with instructions rather than silently seeding nothing.

## Running without an OpenAI key

Leave `OPENAI_API_KEY` empty and the app selects the **mock provider**: the entire graph runs
offline with deterministic canned replies. Every node still fires, so the flow view lights up
exactly as it would for real — instantly, and for free. Ideal for demoing the *architecture*.
Set a real key when you want to demo the *coaching*.

## Two knobs worth knowing

- `CEREBROZEN_FORCE_HANDOFF=__all__` — every stage hands off after one turn, so a handful of
  messages walks the **whole** graph (simulation, learning aid, closing, terminal). It's the
  fastest way to show the full flow end-to-end; it is meaningless as coaching. Test-only.
- `CEREBROZEN_LLM_PROMPT_CACHE=true` — on by default in `.env.demo`. Real prompt caching, keyed
  on the workbook's content hash.

## Re-seeding

```bash
python -m scripts.seed_demo --reset      # wipes the four demo users, then re-seeds
```

Only the `demo-*` users are touched. Nothing else in the database is read or written.
