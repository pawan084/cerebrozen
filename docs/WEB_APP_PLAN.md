# CereBro — Web App & Admin Plane Plan

> Scope + architecture for the browser client (`app.cerebrozen.in`) and the admin
> expansion, from the 2026-07-03 platform audit + deep-research pass. Companions:
> [ARCHITECTURE.md](ARCHITECTURE.md), [INVESTOR_READINESS.md](INVESTOR_READINESS.md),
> [TODO.md](TODO.md) (task breakdown).

## 1. Decision: slim authenticated web app, not mobile parity

- **The market leader's bar is a subset, not parity** (verified): Headspace ships a real
  authenticated web app (my.headspace.com behind Auth0) but its own help center says
  "some content may only be available on your mobile device" and steers members to the
  app — flagship features (Ebb AI voice) are mobile-only
  ([Headspace help](https://help.headspace.com/hc/en-us/articles/11405444126875-Can-Headspace-be-accessed-on-my-phone-and-or-desktop)).
- **B2B/employer sales presuppose a web surface** (employer dashboards, demos without a
  TestFlight invite) — see INVESTOR_READINESS.md gap #7.
- **The backend is already ~90 % ready** (audit 2026-07-03): Bearer-JWT REST+SSE API with
  CORS configured; auth, moods, journal, chat, plans, insights, content, assessment,
  Oracle streaming and voice STT/TTS endpoints are all client-agnostic. The work is
  almost entirely frontend + session hardening.

**Form:** a full Next.js app (new `apps/app`, port 3002, `app.cerebrozen.in`) — same
stack as web/admin, matching repo conventions. Not a PWA-first play: we need routed,
crawl-safe, login-gated pages and Stripe checkout later; installability can be layered on
afterwards with a manifest if wanted.

## 2. v1 scope

**In (all API-backed today):** email + Google sign-in · onboarding-lite (one-tap state
check via `/assessment`) · mood check-in + history · journal (prompts, tags, AI
reflection) · text chat with widgets/starters (Oracle SSE when enabled, `/chat`
fallback) · daily plan (view, generate, step completion) · weekly insights · content
catalogue (`/content`) · sleep diary + trends (after the module in
[SLEEP_TRACKING.md](SLEEP_TRACKING.md) ships) · account: consent toggles, crisis region,
trusted contact, export, delete · crisis banner + resources on chat paths (same
never-blocks contract).

**Out of v1 (deliberate):** voice loop (MediaRecorder capture is possible; defer for
polish) · soundscape mixer (needs web-hosted audio + Web Audio port of `SoundscapePlayer`)
· games · offline-first · Apple sign-in (needs a Services ID — `/auth/apple` audience is
currently the app bundle id) · payments (§4).

## 3. Architecture decisions

| Decision | Choice | Why |
| --- | --- | --- |
| Session | Access token **in memory** + refresh token via `POST /auth/refresh` rotation; refresh token in `localStorage` v1 with a strict CSP (we load no third-party scripts) | No cookies → no CSRF surface; matches the API's Bearer design. v2 option: Next.js BFF proxy with httpOnly cookies if threat model tightens. Fix admin the same way (it currently dies at 30 min with no refresh). |
| CORS | Add `https://app.cerebrozen.in` to `CORS_ORIGINS` | Prod guard already forbids wildcard. |
| Streaming | Oracle SSE-over-POST consumed via `fetch` + ReadableStream (not `EventSource`) | Endpoint shape already fixed by iOS. |
| Streaks | v1 computes from `/moods` history client-side with iOS's rules; later unify behind `GET /users/me/streak` | Streaks have **no backend endpoint today** (iOS computes locally) — a server endpoint is the long-term contract fix. |
| Design tokens | Extract shared token CSS (`packages/tokens` or a checked-in shared css) consumed by web/admin/app | The palette is currently copy-pasted per app; a third copy is where drift starts. |
| Nudges/push | No Web Push in v1; web-only users get email nudges (SMTP service exists) | VAPID/web-push is a later, separate integration. |
| Caddy | New `app.cerebrozen.in` site block (same security-header snippet) | One-line infra change; also finally decide the reserved `mcp.cerebrozen.in` (TODO). |
| Testing | Playwright specs in the existing `e2e/` stack (login, check-in, journal, chat happy path, plan toggle) | Infra already runs web+admin against a hermetic backend. |

## 4. Billing (post-v1, before B2B pilots)

Web subscriptions via **Stripe Checkout** on `app.cerebrozen.in`: new backend
`stripe.py` service + webhook mapping to the same `users.subscription_tier` the App
Store path sets (products stay the cross-stack contract). Purchases made on the web
never owe Apple commission. Anti-steering state as of mid-2026 (single-pass research,
cited): on the **US storefront**, apps may link out to web checkout commission-free
since the Apr–May 2025 Epic v. Apple contempt order; the Ninth Circuit affirmed the
contempt on 2025-12-11 but partially reversed the remedy (Apple may later charge a
"reasonable, non-prohibitive" link-out commission — rate remanded)
([opinion](https://cdn.ca9.uscourts.gov/datastore/opinions/2025/12/11/25-2935.pdf)), and
SCOTUS granted Apple cert 2026-06-30, so the zero-commission status quo holds but isn't
final. **India has no such relief**: digital goods on the Indian storefront must use IAP
and steering links remain prohibited (guideline 3.1.1; EU/Japan/Brazil are the regulated
carve-outs). Net: the iOS app can link US users to web checkout, must not steer Indian
users — Indian web subscriptions have to originate on the web itself (which is exactly
what Sleep Cycle does globally via web-only checkout). Keep iOS IAP untouched; web
billing is additive.

## 5. Competitor web ground truth

Headspace row is adversarially verified (deep-research pass); the rest is single-pass
cited research (2026-07-03, companies' own help centers — Calm/Wysa help pages block
bots, so those quotes come from search-indexed excerpts of the cited support URLs).

| | Consumer web app | What web does | Web billing | B2B web surface |
| --- | --- | --- | --- | --- |
| Headspace | **Yes** (my.headspace.com, Auth0) | Reduced subset; "some content may only be available on your mobile device"; Ebb AI voice is app-only | Yes | Admin portal: aggregate engagement reporting + license management ([source](https://organizations.headspace.com/employers)) |
| Calm | **Yes** — "log in and listen" at calm.com ([support](https://support.calm.com/hc/en-us/articles/35795753391003-Available-Calm-Apps)) | Full premium library of meditations/sleep stories/music on web; check-ins documented mobile-only; downloads app-only | Yes — web free trial, purchase + cancel ([support](https://support.calm.com/hc/en-us/articles/360003084493)) | Partner Portal: sign-up/engagement reporting, eligibility-file upload ([FAQ](https://support.calm.com/hc/en-us/articles/1260801520609)); Calm Health ships app.calmhealth.com |
| Wysa | **Yes** — web.wysa.io; "available on Android, iOS or over the web" ([FAQ](https://www.wysa.com/faq)) | AI chat (the core product) on web | Unknown | Employer dashboards: anonymized population trends ([for-employers](https://www.wysa.com/for-employers)) |
| BetterSleep | **Yes** — "Web at bettersleep.com" ([support](https://www.bettersleep.com/support/en/articles/4597014-where-can-i-access-bettersleep)) | Sounds player at my.bettersleep.com/sounds; feature-split undocumented | Login exists; specifics unknown | "For Work" employee sleep program |
| Sleep Cycle | **No** | Phone + watch only ("just your phone") | **Web checkout only** — FastSpring merchant of record ([support](https://support.sleepcycle.com/hc/en-us/articles/207392935-How-To-Upgrade-To-a-Premium-Subscription)) | — |
| Youper | No evidence (likely none) | iOS/Android only | Unknown | — |

**Reads:** (1) an authenticated web player/chat is the category norm — 4 of 6 ship one —
and always as a *subset*; (2) every B2B/employer motion runs through a web admin surface
(cohort analytics + eligibility) even when the consumer product is mobile-first;
(3) even the one no-web-app player needed web for billing. This confirms the §2 scope:
slim consumer web app + employer-grade reporting later, player/chat first.

## 6. Admin plane v2

Today's admin is one client-side SPA (tabs: overview/users/content/safety/waitlist) with
a 30-minute localStorage session. Planned, in order:

1. **Session refresh** (shared fix with the web app's auth client).
2. **First-party analytics tab** — aggregate cohort metrics from our own Postgres
   (D1/D7/D30 actives, check-in counts, funnel steps, plan completion; no third-party
   SDKs) — this is INVESTOR_READINESS.md gap #1 made visible. Needs new
   `/admin/metrics/*` endpoints (SQL aggregates; no per-user browsing of private data —
   journal/chat bodies stay out of admin reach, consistent with the privacy promise).
3. **Per-user support view** — account status, subscription tier, consent state, safety
   events for that user (metadata only), enable/disable + password-reset trigger. Needs
   `GET /admin/users/{id}`.
4. **Nudge authoring** — create/schedule nudge templates (backend has scheduling +
   dispatch; authoring endpoints are missing).
5. **Subscription state view** — tier counts + recent webhook events (App Store now,
   Stripe later).
6. **Content CMS upgrades** — audio-asset URL field + ordering/rails metadata as the
   iOS `Dummy` → `/content` migration lands (sleep module step 3).

## 7. Rollout order

1. ✅ 2026-07-03 — Infra prep: CORS origin (dev default + env examples), Caddy
   `app.cerebrozen.in` block, dev/e2e/prod compose services, CI typecheck,
   `apps/app` scaffold. Tokens NOT extracted (per-app Docker contexts) — third
   CSS copy for now, extraction tracked in TODO.
2. ✅ 2026-07-03 — Auth (signup/signin, in-memory access + refresh rotation) +
   shell + mood check-in + journal (with crisis-support banner) + Playwright spec.
3. Chat (SSE) + plans + insights + account/consent/export/delete + Google sign-in.
4. ✅ 2026-07-03 — Sleep diary page (check-in, honest summary, history). Content
   catalogue pages still open.
5. Admin v2: ✅ item 1 (session refresh, 2026-07-03); analytics + user support open.
6. Stripe billing + employer/B2B reporting exploration.
