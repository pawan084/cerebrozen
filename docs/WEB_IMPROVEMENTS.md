# Web improvements tracker

The 100-point improvement list from the 2026-08-03 world-class reviews, with
what happened to each. Statuses: **done** (shipped in this repo), **owner**
(needs an account, asset, or decision only the owner can supply), **decision**
(recorded in docs/TODO.md, deliberately not decided unilaterally), **later**
(real work, scoped, not started). Numbers match the original list.

## Done (shipped 2026-08-03, autonomous run)

| # | Item | Where |
|---|---|---|
| 7 (partial) | Data-handling story public | /security + /subprocessors + privacy retention table |
| 21 | SoftwareApplication + Organization JSON-LD | apps/web/app/page.tsx |
| 26 | Sitemap covers new pages, honest lastModified | apps/web/app/sitemap.ts |
| 28 | www → apex canonical redirect | deploy/Caddyfile |
| 32 | Hero image preloaded (LCP) | apps/web/app/page.tsx |
| 37 | Below-fold sections use content-visibility | apps/web/app/globals.css |
| 42 | Cache headers for brand/screens assets | deploy/Caddyfile |
| 46 | Chat stream aria-live | apps/app chat/page.tsx |
| 47 | Skip link in app shell | apps/app (authed)/layout.tsx |
| 49 | lang on translated consent notices | account + onboarding |
| 50 | Focus moves to h1 on client-side nav | components/AppHeader.tsx |
| 52 | Small-text contrast verified (≥AA by arithmetic) | no change needed |
| 53 | PWA manifest + icons, installable | apps/app public/ + layout |
| 56 | Journal drafts autosave locally | journal/page.tsx |
| 57 | Journal search + tag filters (server index) | journal/page.tsx |
| 60 | Chat retry resends the failed message verbatim | chat/page.tsx |
| 68 | Live system-theme changes | verified CSS-driven; no change needed |
| 73 | Password length guidance at signup | components/AuthPanel.tsx |
| 78 | OTP resend cooldown timer | components/AuthPanel.tsx |
| 83 | Refund/cancellation policy page | /refunds |
| 85 | Security page + RFC 9116 security.txt | /security, /.well-known/security.txt |
| 88 | Subprocessor list page | /subprocessors |
| 90 | Data-retention table in the privacy policy | /privacy |
| 91 | 18+ attest on direct signup; fresh accounts route through consent | AuthPanel + signin |
| 93 → gate | CSP floor tripwire across the three middlewares | scripts/check-csp-sync.mjs (CI) |
| 94 | Admin access token memory-only | apps/admin/lib/api.ts |
| 95 | Waitlist created_at + CSV export | backend waitlist.py + admin |
| 101 | ESLint (next/core-web-vitals) in all three apps, CI-gated | .eslintrc.json ×3, ci.yml |
| 102 (partial) | Trust-pages + age-gate + manifest e2e specs | e2e/tests/trust-pages.spec.ts |
| 108 | WEB_STYLE.md copy/design rules | docs/WEB_STYLE.md |
| — | `.legal` prose styling (policy pages rendered unstyled) | apps/web globals.css |

## Owner-blocked (code can't finish these)

| # | Item | What the owner must supply |
|---|---|---|
| 1–2 | Hero re-render without streak chip / name | brand-kit render |
| 4 | Product walkthrough video | recording |
| 9 | Team section | names, faces, bios |
| 11 | Waitlist live count | decision: show at what threshold |
| 27 | Search Console / Bing verification | account access |
| 30 | /press page | founder/company facts |
| 36 | Lighthouse CI | decision: budget + runner minutes |
| 71 | Google OAuth client id | GCP console |
| 79–82 | Stripe web billing + portal + tax lines | Stripe account config |
| 86 | Named grievance officer | a person's name (DPDP) |
| 89 | Terms jurisdiction review | counsel |
| 98–99 | Uptime monitoring, error tracking | service choice + keys |
| 100 | Backup restore drill | prod access window |

## Decisions recorded in docs/TODO.md (not decided unilaterally)

| # | Item |
|---|---|
| 13 | Hide vs qualify Premium+Human tier |
| 15–16 | Multi-currency + annual pricing |
| 54 | Offline shell (architectural: nonce'd force-dynamic HTML vs cached shell) |
| 58 | Web soundscape mixer (parity milestone, not a patch) |
| 67 | Pause-account (needs backend model + policy) |
| 76–77 | Change email/password + verification flows (backend endpoints first) |
| 93 | Admin page.tsx split (mechanical but large; do with a quiet tree) |
| 96–97 | Safety assignment + audit-log surfaces (backend work first) |
| 105–107 | Engine unification, hermetic env, /icon fix (pre-existing ledger items) |

## Later (scoped, real work, unstarted)

| # | Item | Note |
|---|---|---|
| 3, 5–6 | Web-app screenshots/demo on landing | after a screenshot pass |
| 10 | Live status table on landing | render from PRD statuses |
| 14 | More FAQ entries | with the next copy pass |
| 22, 25, 29 | Per-page OG images, hreflang/Hindi landing, breadcrumbs | i18n wave |
| 23–24 | Evidence-grounded articles | needs the citation set curated |
| 31, 33 | AVIF/WebP pipeline | needs an image toolchain in CI |
| 43–45, 48, 51 | axe-core gate, contrast variants, SR audits, captions | a11y wave 2 |
| 55 | Push-permission moment after first win | UX placement decision |
| 59, 61–66, 69–70 | Chat pagination, quota meter, insights deltas, program surfacing, printable safety plan, session-expiry warning, shortcuts, empty-state art | product waves |
| 74–75 | Sign-out-everywhere, device list UI | backend has the data; small API + UI |
| 84 | Full paywall funnel events | with billing wave |
| 87 | Storage disclosure line | one paragraph, next privacy edit |
| 92 | Public "report a concern" channel | route + inbox decision |
| 103 | Visual regression snapshots | after UI settles post-billing |
| 104 | CSP generation from one source | superseded partially by the floor gate |
