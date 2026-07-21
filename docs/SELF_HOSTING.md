# Self-hosting & sovereignty

CereBroZen is **sovereign-capable by construction**: every service boots and every
test suite passes with **zero external credentials** (a mock LLM provider stands in),
so the whole platform can run on your own infrastructure — including air-gapped — with
no data leaving it. This is the differentiator no coaching incumbent offers (see the
positioning review). This doc is the operator's path and the proof.

## One command, keyless

```bash
docker compose up --build      # web :3000, admin :3001, app :3002, platform :8100, engine :8000
```

With no provider keys set, the stack comes up fully usable:

- **Coaching** runs on the **mock LLM provider** (deterministic fallbacks) — `CEREBROZEN_LLM_PROVIDER=mock`. Set `OPENAI_API_KEY` (or point `ollama` on-prem) to use a real model.
- **Rate limiting / counters** use in-process **fakeredis** — set `REDIS_URL` for a shared, durable limiter.
- **Email** (invitations, password reset, OTP) degrades to **manual link/code sharing** — links are logged, never lost. Set `SMTP_*` to actually deliver.
- **Billing** uses the **mock provider** (freemium works end-to-end, no charges) — real Stripe/Play adapters activate behind the same seam once keys exist.
- **Voice** falls back to **on-device** STT/TTS — the cloud (LiveKit/Deepgram/ElevenLabs) loop is optional and key-gated.

Nothing above reaches the public internet unless you supply the key that turns it on.

## Verify the posture — no credentials needed

```bash
curl -s localhost:8100/health/status
```

```json
{ "service": "platform", "env": "local", "database": "postgres",
  "email_delivery": false, "billing_provider": "mock",
  "dev_seed_enabled": true, "sovereign_ready": true }
```

`sovereign_ready` confirms the instance is running on a self-hostable datastore;
`email_delivery`/`billing_provider` tell you honestly which subsystems are degraded
(keyless) vs live. This endpoint reveals **posture, never data**, so it needs no auth
and is safe to scrape for monitoring.

Two things about that output worth knowing before you use it as a proof surface:

- **`database` follows how you started the platform**, so it is `"postgres"` after
  the compose command above (the sample here said `"sqlite"` until 2026-07-21, which
  made the doc look wrong to anyone who actually ran it). You only get `"sqlite"`
  running the process bare with no `DATABASE_URL`. Both are `sovereign_ready: true`
  — that flag asserts *self-hostable*, not *which one*.
- **`billing_provider` is a posture, not a provider name.** It reports `"mock"` or
  `"live"` (derived from `BILLING_MOCK`), so a Stripe or Play deployment reads
  `"live"` — never `"stripe"`. Deliberate: the endpoint is unauthenticated, and
  naming your payment processor to the open internet is a detail an operator should
  choose to publish, not one we publish for them.

## The guarantee, enforced in CI

"Boots and passes with zero credentials" is not a promise — it's a gate. Each
service's suite runs fully offline (mock LLM, in-memory stores, no network) and is
required green:

- **Engine** — `services/engine`: `pytest` (96% branch). `conftest.py` pins the mock
  provider and blanks `OPENAI_API_KEY` / `MONGO_DB_URL` / `REDIS_URL` / `POSTGRES_URL`
  so a coaching turn runs end-to-end with nothing external.
- **Platform** — `services/platform`: `pytest` (95%). In-memory SQLite, no SMTP; the
  deployment self-check above is asserted keyless in `tests/test_health.py`.

## Data residency & air-gap notes

- Every engine store and platform table is keyed by `org_id`; content (journals,
  moods, sleep, coaching turns) lives only in the engine, never on the platform an HR
  token reaches ("counts, never content" — a schema property, `test_wellness_account.py`).
- **Regulated mode** is on by default: emotion inference and durable person-scoring are
  refused at the store layer (EU AI Act Art. 5 alignment). Opt-out is a contract-level
  decision, not an admin toggle.
- Crisis helplines ship with a region-neutral **offline floor**, so safety works with
  no network at all.

## What still needs you (not autonomous)

Real provider keys (OpenAI/Ollama, Redis, SMTP, Stripe/Play, LiveKit) and the
production secrets (`JWT_SECRET` shared between platform and engine — base64-encoded;
a malformed value silently disables auth, so generate it carefully). See
`docs/DEVELOPING.md` for the full env matrix.
