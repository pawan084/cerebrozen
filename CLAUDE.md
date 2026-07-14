# CereBroZen — project rules

B2B enterprise AI coaching platform. **Read `docs/README.md` first** — it
indexes PRODUCT, ARCHITECTURE, DESIGN, ENGINEERING, SECURITY, and TODO.
Work follows `docs/TODO.md` phase order.

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
6. Marketing claims must map to mechanisms (`docs/SECURITY.md` table). Don't
   add a claim to `apps/web` without the backing mechanism, and vice versa.
7. Cross-stack contracts (JWT claims, SSE vocabulary, action lifecycle,
   design tokens, analytics vocabulary) change only per the protocol in
   `docs/ENGINEERING.md` — update the ARCHITECTURE.md table in the same PR.

## Layout

- `apps/web` — marketing site (Next.js 16, built; has its own CLAUDE.md).
- `apps/android`, `apps/admin` — to build (Phases 3, 2).
- `services/engine`, `services/platform` — to build (Phase 1).
- `docs/` — the plan; keep it current: PRs state which doc they touch or why none.

## Gates (build-failing; details in docs/ENGINEERING.md)

Engine 96% branch / platform 95% / Android JaCoCo 95% coverage; token-drift
and contrast checks; gitleaks; crisis red-team is a release gate.
