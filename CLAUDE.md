# CereBroZen — project rules

AI coaching platform, sold **B2B (enterprise seats) and B2C (freemium)** since
2026-07-19. **Read `docs/README.md` first** — it indexes the whole doc set.
`docs/TODO.md` is the phase plan and an append-only build log (read it
chronologically); `docs/IMPROVEMENT_BACKLOG.md` is the live tracker for what is
being worked on now.
**To run or test anything: `docs/DEVELOPING.md`** — ports, seeded logins, gates,
and the traps (`stack:down` wipes **both** services' data — under compose the
platform shares the engine's Postgres volume; the suite runs on mongomock while
Postgres ships).

## Hard rules

1. `ref/` is **read-only reference material** (`ref/Agent` = coaching-engine
   reference, `ref/Zen` = Android/admin/platform reference). Never edit it,
   never run it, and **never load or reuse any `ref/**/.env*` file or
   credential — secrets found there are treated as compromised.**
2. Secrets are never committed. `.env*` is git-ignored everywhere;
   committed `.env.example` files hold placeholders only.
3. Everything degrades without keys: every service boots and every test
   suite passes with zero external credentials (mock LLM provider).
4. Safety is code, not content: crisis text and takeover routing never move
   into the editable prompt workbook.
5. "Counts, never content": no admin/HR surface or API exposes transcripts,
   journals, or commitment bodies.
6. Marketing claims must map to mechanisms. The **CI-enforced** table is
   `docs/CLAIMS_MAP.md` (claim → mechanism → test, gated by
   `scripts/check-claims.mjs`); `docs/SECURITY.md` carries a second, broader
   compliance table that is *not* gated. Don't add a claim to `apps/web`
   without the backing mechanism, and vice versa.
7. Cross-stack contracts (JWT claims, SSE vocabulary, action lifecycle,
   design tokens, analytics vocabulary) change only per the protocol in
   `docs/ENGINEERING.md` — update the ARCHITECTURE.md table in the same PR.

## Layout

All surfaces are **built and gate-green**; the work is backlog burn-down, not
scaffolding.

- `apps/web` — marketing site (Next.js 16; has its own CLAUDE.md).
- `apps/app` — web client · `apps/admin` — HR portal + ops admin.
- `apps/android` — employee/consumer app (5 tabs; see `docs/ANDROID_QA.md` for
  what is device-verified vs only CI-verified).
- `services/engine` — coaching graph + safety · `services/platform` — auth,
  orgs, entitlements, billing. Each carries its own `docs/` (a `docs/X.md`
  reference inside one means *that* tree).
- `docs/` — the plan; keep it current: PRs state which doc they touch or why none.

## Gates (build-failing; details in docs/ENGINEERING.md)

Engine 96% branch / platform 95% / Android JaCoCo 95% coverage; token-drift
(`sync-tokens --check`) and the Android contrast test; the marketing-claims
gate (`scripts/check-claims.mjs`); gitleaks. Crisis red-team is a release gate.
