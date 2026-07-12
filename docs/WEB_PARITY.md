# Web Parity Audit — Android Redesign → Web App (apps/app)

> W19, produced 2026-07-12 by reading `docs/REDESIGN.md` (findings F1–F11), `docs/IOS_PARITY.md`
> (format precedent), the full authenticated web client (`apps/app/**` — every page, component
> and lib file), the shipped Android implementation (read-only), and the backend contract
> surfaces (read-only; backend + `apps/admin` are owned by a running agent — nothing modified).
> **Analysis only — no code changed.** Effort: S ≤ ½ day, M ≈ 1–2 days, L ≥ 3 days.
> Static-read only: the stack was not run, so anything marked ⚠ needs a live pass
> (`docker compose up` + the `docker-compose.e2e.yml` suite).
>
> Headline: the web app is structurally *cleaner* than pre-redesign Android (one breathing
> surface + one onboarding pacer, no mini-game bloat, honest "lives in the apps" footnotes,
> consent with optimistic-write **and rollback** on the account page — the iOS consent-wiping
> bug has **no web analogue**). Its two real problems are the opposite ones:
> **(1) crisis is effectively unreachable** — there is no crisis page at all, only reactive
> banners, and no Tele-MANAS anywhere on the web or in the backend's IN list; **(2) a cluster
> of fake surfaces** (hardcoded "recent conversations", fabricated personal insights, decorative
> search/bell) that fail the F9 credibility bar harder than anything Android had.

## B — Outright bugs & fake surfaces (fix first; F9/F10 credibility bar)

| # | Problem | File(s) | Fix | Effort |
|---|---|---|---|---|
| B1 | **Fake "Recent conversations"** — three hardcoded sessions ("Late-night worries · Yesterday · 14 min", …) shown to every user as if real history | `apps/app/app/(authed)/chat/page.tsx` (`RECENT`) | Delete the block, or derive real groups from `GET /chat` timestamps. Never fabricate a user's own history. | S |
| B2 | **Fabricated personal insights** — "Gentle patterns" are 3 hardcoded claims about the user ("Your mood tends to rise on days you log a walk"); stat tiles hardcode "▲ gentler than last week" and "Best time of day: Morning — you check in most before 9am" | `apps/app/app/(authed)/insights/page.tsx` (`PATTERNS`, stat tiles) | Ironically the honest engine already exists: `/patterns` renders real `GET /insights/patterns` statements with `basis` counts. Reuse it here (or honest empty states); compute the delta/best-time from the fetched moods or drop them. | S |
| B3 | **Fake mood charts** — when <2 real check-ins exist, both mood lines render an invented shape (`[3,4,3,4,…]`) as if it were the user's week | `home/page.tsx` (`pts` fallback), `insights/page.tsx` (`pts` fallback) | Replace fallback with an honest empty state ("your line starts after two check-ins"). | S |
| B4 | **Journal fabrications** — rail says "Most often on quiet evenings." (static, regardless of data); prompt hero claims "Today's prompt · shaped by your check-in" but the prompt is hardcoded | `journal/page.tsx` | Drop/compute the evenings line; relabel the prompt eyebrow honestly ("Today's prompt") until it actually personalizes. | S |
| B5 | **Dead chrome** — the "Search calm…" field has no handler, and the notification bell renders a *dot* (implying unread notifications) but does nothing | `components/AppHeader.tsx` | Remove both until real (search has no backend; there are no in-app notifications on web). Focusable-but-inert controls are also a keyboard-a11y failure. | S |
| B6 | **"Start live session" is not live** — pill with a pulsing `live-dot` runs the exact same `begin()` as "Type instead"; voice doesn't exist on web (the footnote even says so) | `chat/page.tsx` (`talk-actions`) | One honest CTA ("Start talking — type below"); keep the voice-is-in-the-apps footnote. | S |
| B7 | Sidebar user chip hardcodes **"Free plan"** for every user, including premium | `(authed)/layout.tsx` | Render `subscription_tier` from `/auth/me` (already fetched for the name). | S |
| B8 | Rhythm card shows **best** streak as the headline "day rhythm" number (`streak?.best ?? streak?.current`) — reads as current activity | `home/page.tsx` (rail-card) | Headline = days present this week (count `week[].active`), matching Android's presence card; keep best/current as a quiet sub-line if at all. | S |

*Positive finding:* the iOS `ConsentScreen.onAppear` consent-wiping bug (IOS_PARITY #13) has
**no web equivalent** — the onboarding draft persists to localStorage across Back/forward, and
account-page consent does optimistic writes with revert-on-failure (`account/page.tsx
toggleConsent`). Web is currently the best-behaved client on consent mechanics.

## P1a — Safety (F7/F8: the differentiator; web currently fails "≤2 clicks")

| # | Android change | Web file(s) | Port instructions | Effort |
|---|---|---|---|---|
| 1 | Crisis reachable **≤2 taps from anywhere**; dedicated crisis screen leads with **Tele-MANAS 14416** (call + WhatsApp), then 112, KIRAN, findahelpline | **No web crisis page exists.** Crisis appears only *reactively*: chat banner (`chat/page.tsx`), journal post-save banner (`journal/page.tsx`). `(authed)/layout.tsx` has no Support affordance | Create `app/(authed)/crisis/page.tsx` (or better: a public route outside `(authed)` so it works signed-out) — static content, no data fetches, so it renders even when the API is down: Tele-MANAS `tel:14416` + WhatsApp `https://wa.me/9114416` → `tel:112` → KIRAN `tel:18005990019` → `https://findahelpline.com/in`, honoring the account `region` when signed in. Add a calm persistent "Support" link to the sidebar + the mobile tab bar overflow (2 clicks from anywhere) and a card on `/account`. Copy source: Android `strings.xml` `crisis_line_*` / `Screens.kt`. | M |
| 2 | Every crisis surface leads with Tele-MANAS | `chat/page.tsx` fallback resources (112 + KIRAN + findahelpline — **no Tele-MANAS**), `journal/page.tsx` banner (same) | Prepend "Tele-MANAS — call or WhatsApp 14416" to both; make numbers tappable `tel:` links (currently plain text in chat — useless on mobile web). **Contract care:** server-driven `crisis.resources` come from `backend services/crisis.py`, whose IN list is *also* still 112 + KIRAN (verified) — Android leads with Tele-MANAS client-side, so the backend has drifted behind. Backend is agent-owned: coordinate the `services/crisis.py` IN update in the same wave, don't touch it from this task. | S |
| 3 | Risk signal ⇒ pathway attached | `journal/page.tsx` entry list marks elevated/crisis entries with a 😔 emoji — a residual risk marker with no route | Either drop the risk-derived emoji or make the card link to the crisis page. Keep "writing is never blocked" (already true — parity with the safety rule). | S |
| 4 | HumanSupport stubs → real links (Tele-MANAS, iCall 9152987821, findahelpline.com/in) | No web surface at all (neither stub nor real) | Add a "Talk to a human" card on `/account` (and link from the crisis page) with the three real links — copy from Android `Settings.kt: HumanSupportScreen`. No fake booking UI, ever. | S |

## P1b — Consent, credibility, copy

| # | Android change | Web file(s) | Port instructions | Effort |
|---|---|---|---|---|
| 5 | Onboarding consent renders **all 6 DPDP categories** | `app/onboarding/page.tsx` — `CONSENT_KEYS` deliberately shows 5, with a comment parking `model_training` on the account page | Follow Android: add the `model_training` toggle (off by default; `consentNotice.ts` already carries all 6 keys × 13 languages — no contract change). DPDP "specific and informed" is better served by showing the category than by silently defaulting it. The set-all "Remember my patterns" row should keep excluding `voice_storage`/`model_training` (sensitive opt-ins stay individual — matches Android). | S |
| 6 | **Credibility layer**: "Why this works" provenance footers; "How CereBro is built" honesty cards | none on web — `games/page.tsx`, `programs/page.tsx`, `sleep/page.tsx`, `account/page.tsx` | Port `WhyThisWorks(text)` as a small shared component (one-line provenance + expandable source, hardcoded per surface — copy from Android `Common.kt:593` call sites): box breathing on `/games`, program cards, sleep content ("comfort content, not therapy"). Add the three "How CereBro is built" cards (evidence labeled / what CereBro is not / professional involvement) to `/account`, mirroring Android `Settings.kt:371`. | S |
| 7 | Games+Tools → **Toolkit** (credibility framing) | `(authed)/layout.tsx` nav "Games", `games/page.tsx` ("Calm play", "Games to settle the mind") | Web has no game bloat to kill (only box breathing — already the post-redesign shape). The gap is *framing*: rename nav + route copy to "Toolkit / Small ways to steady", add the crisis footer row (item 1), and grow sections *Ground · Breathe · Reframe · Settle* as tools arrive (5-4-3-2-1 grounding is an S add — pure client). Keep the `/games` URL redirecting if renamed. | S (copy) / M (with grounding + CBT reframe) |
| 8 | Onboarding 10 → 8 steps: **kill fake Plan preview**, merge Age into Disclosure | `app/onboarding/page.tsx` (`FirstPlan` renders static `PLAN_STEPS` under "Made around you" — same anti-pattern Android killed), `AgeGate`, `lib/onboarding.ts` (`PLAN_STEPS`, `STEP_NAMES`, `PROGRESS`) | Delete `FirstPlan` (replace with one line in Welcome); fold the 18+ confirm + underage exit into `Disclosure` (Android precedent — merged step named `disclosure`). Update `PROGRESS` fractions. **Funnel contract:** web currently emits *no* analytics (see item 14), and `STEP_NAMES` is exported but unused — keep it aligned with the canonical vocabulary anyway (backend `metrics.ONBOARDING_STEPS` stays unchanged; `age_gate`/`first_plan` simply never fire from web). e2e signs up via `/signin`, so no test re-record. ⚠ verify resume logic (`hasSession() → setStep(8)`) still lands on consent after renumbering. | M |
| 9 | Streak → **presence** forgiveness framing | `home/page.tsx` rhythm card | Mostly at parity already — "Gentle and consistent — no streaks to break" is exactly the F5/F6 posture, week bars dim rather than scold. Remaining: B8 (count days-present), and never surface `best` as a target. Presentation-only; `GET /users/me/streak` computation is a cross-stack contract — don't touch. | S |
| 10 | `onegoodthing`/`intention` → Journal quick-entry chips | `journal/page.tsx` (`REVISIT` prompts are static, non-interactive text) | Make the three revisit prompts + two new chips ("One good thing", "Tonight's intention") clickable: open the composer prefilled with prompt-as-title + tag (Android `JournalScreen.kt` pattern). | S |
| 11 | Premium: OECD dark-pattern checklist (F10) | `(authed)/layout.tsx` (always-on "Unlock Premium" sidebar card), `account/page.tsx` (`upgrade()` → Stripe Checkout, 503-honest when unconfigured) | Posture is already decent: full-price disclosure happens on Stripe's page, no preselected plan, no guilt copy, billing degrades honestly. Before web billing goes live: add a cancel-path row (Stripe billing-portal link) next to the tier line, and soften the *permanent* sidebar upsell (an always-visible upsell in a wellness app leans on the OECD "nagging" indicator). Document the checklist result in RELEASE_PLAN. | S |

## P2 — Flagship

| # | Android change | Web file(s) | Port instructions | Effort |
|---|---|---|---|---|
| 12 | Sleep "track" → **"improve"**: CBT-I stimulus-control cards + "Your rhythm" consistency insight (F2) | `sleep/page.tsx` — today: content lists + morning check-in only; fetches `/sleep?limit=1` | Add (a) a "Your rhythm" card: fetch `/sleep?limit=7`, compute bedtime spread **including the midnight wrap** (port Android's unit-tested `rhythmPrinciple(spreadMin)` math — times here are `"HH:MM"` strings, convert to minutes-since-midnight first) with "consistency beats duration" copy; (b) 2–3 stimulus-control micro-education cards (bed = sleep · out of bed if awake · same wake time — hardcoded, non-diagnostic per SLEEP_TRACKING framing); (c) shift hero copy from mood-lighting to improvement framing. Phase-2 backend (personal window, CBT-I program) stays roadmap for all clients. | M |
| 13 | **`today_guide`** per-day program guide (additive field, W15) | `programs/page.tsx` + `home/page.tsx` — neither renders it; the `Active`/`Program` types omit the field | Backend `GET /programs/active` already carries `today_guide {title, body}` when the program has `day_guides` (verified in `routes/programs.py`; ARCHITECTURE table). Add it to both active-program cards, null-safe exactly like Android `Extras.kt parseTodayGuide` (omit when absent/blank — older servers simply don't send it). This is the cheapest real-utility win on the list. | S |
| 14 | Analytics funnel (web equivalent of "analytics before consent") | nothing — `apps/app` posts **zero** events to `/events` (verified: no track/analytics call anywhere) | Two-sided: privacy-wise this is the cleanest client; product-wise the admin funnel is blind to web (`routes/events.py` `source` enum already accepts `web`/`app`, so no backend change needed). If events are added, (a) use the post-merge canonical step names (merged step = `disclosure`; never emit `first_plan`), (b) resolve the same product/legal decision Android has open in TODO (events are anonymous-by-design — random install id, no auth — but DPDP posture should be decided once, cross-client). Decision-gated; don't add silently. | S–M (gated) |
| 15 | Talk offers structured exercises ("Try together" chips) — rule-based-first (F3) | `chat/page.tsx` empty state (`STARTERS` only) + `WIDGET_LINKS` | Add a "Try together" rail in the empty state and below suggestions: Box breathing → `/games`, One good thing → `/journal` (prefilled, item 10), 5-4-3-2-1 grounding once item 7 ships it. Client-only links, no LLM dependency. | S |
| 16 | **Widget kinds routing** (cross-stack) | `chat/page.tsx` `WIDGET_LINKS` maps only `mood_check`/`mini_journal`/`journal`/`sleep_checkin`; everything else shows "This one lives in the iOS app" — including `breathing`, which the web *does* have at `/games` | Extend: `breathing → /games`, `one_good_thing`/`intention_set → /journal` (with prompt prefill), `grounding → /games` once shipped; leave `dbt_skill` on the honest iOS-note until a TIPP surface exists. Mirror of Android `TalkScreen.kt widgetRoute`. Never *remove* kinds — unmapped kinds must keep the honest fallback. | S |
| 17 | **Dawn/Night dual theme** | `app/globals.css` (651 lines, Night-only: fixed `--night`/`--night-top`, **no** `prefers-color-scheme` anywhere) + many inline hex gradients inside TSX (`home` `STEP_COLORS`/`JUMP`, `sleep` `SOUND_BG`/`STORY_BG`, `programs` `THUMBS`, hero backgrounds) | The web analogue of iOS item 16, smaller because tokens are already CSS vars: (1) add a Dawn var set under `@media (prefers-color-scheme: light)` + a `data-theme` override (System/Night/Dawn picker on `/account`, localStorage); (2) sweep the inline literal gradients into classes/vars first — they're the web's "44 one-off tokens"; (3) `/sleep` + onboarding stay Night (scope by route class). Remember `globals.css` is hand-duplicated per app (middleware comment) — decide whether admin/web follow. ⚠ needs a full two-theme visual pass; contrast ratios unverified (`--muted` #a9a3d0-ish on night surfaces looks ≥4.5:1 by eye, not computed). | L |

## Hygiene

| # | Item | Status / action | Effort |
|---|---|---|---|
| 18 | Reduce-motion | Onboarding orbs are gated (`prefers-reduced-motion` ×3 in globals.css) but `checkin-orb`, `talk-orb`, `hero-orb`, `live-dot` animations are not — extend the media query. | S |
| 19 | Breathing duplication | Two implementations (onboarding `FirstReset` 4-2-4, games `BoxBreather` 4-4-4-4) — acceptable (iOS keeps two as well), but if Toolkit grows, extract one `<Breathe preset>` component like Android's engine. | S (optional) |
| 20 | Gamification posture | Already clean: no badges/points/celebration overlays anywhere; box-breathing "rounds complete" counter is neutral. No action. | — |
| 21 | Adjunct-not-treatment copy (F1) | Strong: onboarding Disclosure can/can't panel, chat `ai-note` ("not a therapist or crisis service"), guided-tour stop 3, no cure claims found in any page copy. Keep the bar on new crisis/credibility copy. | — |
| 22 | PWA | `public/` holds only `sw.js` (push). No manifest — the app is not installable; fine for now, note if "web app on the home screen" ever becomes a goal (the SW would then also need fetch handlers, which interact with the CSP `worker-src 'self'`). | — |
| 23 | SSR posture for new work | Every page is `"use client"` + client fetch; middleware forces dynamic rendering for the CSP nonce. New crisis page (item 1) should be the exception: static JSX, zero fetches, so it works with a dead API and slow networks. | — |

## Cross-stack contracts — must NOT drift (ARCHITECTURE.md §contracts)

| Contract | Web rule for this backport |
|---|---|
| Consent keys (6 flags) | Item 5 adds a missing *toggle* only. `lib/consentNotice.ts` is the hand-synced 13-language mirror of `ConsentNotice.swift` / android `ConsentNotice.kt` — don't touch strings; change all three together or none. |
| Widget kinds (`WIDGET_LINKS` ⇄ `services/activities.py` ⇄ iOS `ActivityDestination` ⇄ android `widgetRoute`) | Item 16 adds mappings, never removes kinds; unmapped kinds keep the honest "lives in the app" fallback. |
| Onboarding funnel step names (`lib/onboarding.STEP_NAMES` ⇄ `metrics.ONBOARDING_STEPS`) | Backend list unchanged; after item 8 the merged step is `disclosure`, `age_gate`/`first_plan` never fire from web (Android precedent). Web currently emits nothing, so drift risk is documentation-only — until item 14. |
| Crisis regions/hotlines (`services/crisis.py` ⇄ clients) | **Live drift found:** Android leads with Tele-MANAS; backend IN + web are still 112/KIRAN. Web hardcodes fallbacks in two places (chat, journal) — when backend updates, web fallbacks must match in the same wave. Backend is agent-owned: coordinate, don't edit. |
| Streak rules | Items 9/B8 are presentation-only; `GET /users/me/streak` semantics untouched. |
| `today_guide` | Additive/optional by design — item 13 must tolerate absence (older servers omit it). |
| Assessment taxonomy | `lib/onboarding.FEELINGS` mirrors iOS `StateCheckScreen` motivation/goal mappings — onboarding trim (item 8) must not alter the six mappings. |

## Deliberate divergences (recommended)

- **Keep the desktop dashboard IA** — Android's Home de-densify (11→6 blocks) doesn't map: web Home is a two-column dashboard with the check-in already first; the program card on Home earns its place on a wide viewport. Only B3/B8 change.
- **No sounds-consolidation port** — web has one honest `<audio>` surface per catalogue item and openly defers the mixer to the apps; nothing to unify.
- **`/patterns` is the model** — the transparent-AI-memory page is the most credibility-aligned surface in the whole product; B2 should copy it, not replace it.

## Honesty ledger — unverifiable from this pass

1. Nothing was run: no `docker compose` stack, no e2e, no browser — all items are static reads; ⚠ items (8, 17) plus every UI change need `docker-compose.e2e.yml` green (`e2e/tests/app.spec.ts` asserts current copy: "Guided tour · 1 of 4", "Type instead", "Gentle patterns", "Games" nav — items B6, B2, 7, 8 all require spec updates in the same commit).
2. Contrast ratios (item 17) and mobile tap-target sizes are uncomputed.
3. Whether the backend owner already has the Tele-MANAS `services/crisis.py` update in flight — only the current file state (112/KIRAN) is certain.
4. Stripe checkout page contents (OECD price-disclosure claim in item 11) — inferred from the code path, not observed.

## Suggested landing order

1. **Wave A — kill the fakes (all S):** B1–B8, item 3 — pure deletions/honest states, e2e copy updates ride along.
2. **Wave B — safety:** items 1, 2, 4 (crisis page + Tele-MANAS + human links), coordinated with the backend `crisis.py` IN update.
3. **Wave C — credibility & consent:** items 5, 6, 7(copy), 10, 16, 13, 15, 9.
4. **Wave D — flagship:** items 12 (Sleep CBT-I), 8 (onboarding trim), 11, 14 (decision-gated), 18.
5. **Wave E — theme:** item 17 (token sweep first, then Dawn).
