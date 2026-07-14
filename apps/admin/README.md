# apps/admin — HR Portal + Ops Admin

Next.js 16, single-page, role-gated tabs (the `ref/Zen` admin pattern). Zero
runtime deps beyond next/react; hand-written CSS on the shared design tokens
(synced from `design/tokens.css`, CI drift check).

- `org_admin`: Overview (seats, regulated mode, region) · People (identity
  only — **counts never content**) · Invite (token shown once; email
  delivery is a Phase 2 wiring).
- `internal_admin`: Tenants (create/toggle; new tenants start regulated-ON) ·
  Demo requests (pipeline from the marketing form).

Run: `npm run dev` (port 3001) with the platform API on
`NEXT_PUBLIC_API_URL` (default `http://localhost:8100`; dev login
`admin@cerebrozen.in` / `admin12345`). `lib/api.ts` coalesces token refresh —
concurrent 401s share one rotation, because a duplicate refresh trips the
platform's reuse detection (by design).

Still to come (docs/TODO.md): nonce-CSP middleware before production,
Prompt-workbook and Safety-queue tabs (engine APIs), HR analytics with
cohort floors.
