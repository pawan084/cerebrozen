# services/platform — Platform API

The org/identity half of the backend (see `docs/ARCHITECTURE.md` §"Backend 2"):
auth (JWT + single-use refresh rotation with reuse detection), org/seat/
invitation lifecycle, roles (`user`/`org_admin`/`internal_admin`), privacy
(export + deletion with a no-PII ledger), and the demo-request pipeline.

Issues the JWTs the coaching engine validates: HS512, base64 `JWT_SECRET`
shared with the engine, claims carry `org_id` + `user.username` (verified
end-to-end against the engine at build time, 2026-07-14).

- Run: `uvicorn app.main:app --port 8100` — boots on SQLite with zero config.
- **Dev seed — one persona per surface** (both clients offer these as one-click
  fill on their login screens, in `next dev` only):

  | Sign in as | Password | Role | Reaches |
  |---|---|---|---|
  | `admin@cerebrozen.in` | `admin12345` | internal_admin (no org) | admin :3001 — ops tabs |
  | `hr@cerebrozen.in` | `demo12345` | org_admin of *Demo Co* | admin :3001 — HR tabs |
  | `demo@cerebrozen.in` | `demo12345` | user of *Demo Co* | app :3002 — the employee client |

  `_seed_dev_admin()` seeds only the internal admin (the test suite's baseline —
  a seeded Org would show up in every org-listing assertion); `_seed_demo_tenant()`
  adds *Demo Co* + the two members and runs on boot. Both are gated by
  `CEREBROZEN_SEED_DEV_ADMIN` **and** a dev `ENV`, and `guard_production()` refuses
  to boot a deployed env with the flag on — these accounts cannot exist in prod.
- Tests: `python -m pytest --cov` — 41 tests, 98.7% coverage (gate 95).
  Note `.coveragerc concurrency = thread,greenlet`: SQLAlchemy's asyncio
  bridge runs continuations in greenlets; without it coverage silently
  loses every line after the first awaited DB call.
- Passwords: stdlib PBKDF2-HMAC-SHA256 (600k iterations) — deliberate; native
  bcrypt/argon2 wheels are blocked on some managed dev machines.
- Alembic lands before the first post-launch schema change (docs/TODO.md);
  the schema is created at boot while greenfield.
