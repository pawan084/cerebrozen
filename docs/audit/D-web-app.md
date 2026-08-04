# Audit D — Authed web client (`apps/app`, :3002, app.cerebrozen.in)

> 2026-08-04 static audit of every route under `apps/app/app/`, `components/`, `lib/`,
> `middleware.ts`, `globals.css`, `public/sw.js`, cross-checked against the Android client
> (the parity reference), `docs/CLAIMS_MAP.md` and the backend contracts the pages call.
> Owner decision 2026-08-04 (Sleep follows the selected theme; `theme.spec.ts` pins it) is
> respected — not flagged. Line numbers are as of commit e5f0796.

## Findings

### Session / correctness (highest severity first)

1. [BUG] Visiting the Pattern dashboard with AI-memory consent OFF signs the user out — `authedFetch` treats **403 as an expired session** (refresh → retry → `clearSession()` → throw "unauthorized"), but the backend answers 403 for consent-gated memory routes (`_memory_allowed`, backend/app/api/routes/users.py:312-318). `/patterns` calls `GET /users/me/memory` on mount, so a memory-off user's session is silently destroyed (apps/app/lib/api.ts:101-105; apps/app/app/(authed)/patterns/page.tsx:61-65). Same trap for `POST /users/me/memory` — the friendly "is AI memory switched on?" catch message can never show because the thrown error is "unauthorized" (patterns/page.tsx:88-99).
2. [BUG] Oracle thread-id race: `threadId` defaults to the shared literal `"web"` until `/auth/me` resolves; a message sent in that window checkpoints into a thread key not scoped to the user (apps/app/app/(authed)/chat/page.tsx:84,96,148). Android threads default to the user id server-side.
3. [BUG] Home mood check-in fails silently and still congratulates: `pick()` shows the affirming response optimistically and swallows the POST error (`catch {}`) — the user is told "Love that…" while nothing was saved and the streak never updates (apps/app/app/(authed)/home/page.tsx:92-99).
4. [BUG] Sleep check-in save has `try{…}finally{}` with no catch — a failed `POST /sleep` is an unhandled promise rejection (console error), no user-facing error, no retry hint (apps/app/app/(authed)/sleep/page.tsx:82-90).
5. [BUG] Journal save has the same no-catch pattern — on failure the user gets silence plus a console error; the draft survives but nothing says why the entry didn't appear (apps/app/app/(authed)/journal/page.tsx:94-103).
6. [BUG] Plan "Update plan" has the same no-catch pattern — `regenerate()` rejection is unhandled and unsurfaced (apps/app/app/(authed)/plan/page.tsx:34-42).
7. [BUG] Plan page is a dead end when no plan exists: on `/plans/active` failure only "Couldn't load your plan." renders, and the generate button is inside `{plan && …}` — a user who has never had a plan can never create one from /plan (plan/page.tsx:14-16,53,84-87). Home's fallback row just links back here (home/page.tsx:232-238).
8. [BUG] Delete account claims success on failure: `deleteAccount()` clears the session and redirects to /signin in `finally` even when `DELETE /users/me` failed — the user reasonably believes their data is gone while it persists (apps/app/app/(authed)/account/page.tsx:197-205).
9. [BUG] Export can hang forever: `exportData()` has no try/catch around `authedFetch` — a network failure leaves status stuck on "Preparing your export…" plus an unhandled rejection (account/page.tsx:144-159).
10. [BUG] Crisis-region save fails silently: `saveRegion` sets state optimistically and swallows the PATCH error with no revert and no message — a safety-relevant setting can show as changed while unsaved (account/page.tsx:127-132).
11. [BUG] Two independent "Anonymous usage stats" toggles on the same Settings page (`usageStats` in Privacy choices, `statsOn` in Your data) hold separate state — flipping one leaves the other visibly stale until reload (account/page.tsx:58-59,71-72,349-370,455-470).
12. [BUG] `paywall_view` fires twice per free-user visit to Settings — once in the `/auth/me` effect and again from `<PaywallSeen />` mounting — double-counting the funnel metric the card exists to measure honestly (account/page.tsx:80-86,23-26,246-249).
13. [BUG] Programs identify the active program by **title equality**, not id: `active?.title !== p.title` — two catalogue items with the same title both lose/gain their "Start this journey" button; the API hands back `content_id` for exactly this (apps/app/app/(authed)/programs/page.tsx:181; type at 34-41).
14. [BUG] Goals week circles use UTC day keys: `d.toISOString().slice(0,10)` after local `setDate` — for IST users before ~05:30 the "today" circle and `recent_days` comparisons shift a day (apps/app/app/(authed)/goals/page.tsx:26-34).
15. [BUG] "Make this today's plan" has no busy/disabled state — a double-click POSTs `/goals/{id}/decompose` twice (goals/page.tsx:84-91), unlike every other mutating button in the app.
16. [BUG] Chat mid-stream failure loses the retry path: only exceptions thrown before/during `send()` set `failedText`; an SSE `error` event inside `consume()` renders the error text as a reply and offers no "Try sending again" chip (chat/page.tsx:125-131 vs 159-176).
17. [BUG] Safety plan "Open printable plan" opens the tab **after an await**, which Safari's popup blocker kills; the catch then shows the misleading "save a section first" message (apps/app/app/(authed)/safety-plan/page.tsx:141-154).
18. [BUG] Safety-plan offline cache stores **unsaved** text as the saved copy: `saveSection` calls `cache(values, …)` with all six textareas, including sections never PUT — the "copy saved on this device" can contain words the server never received, contradicting the screen's own comment ("the last saved plan is mirrored") (safety-plan/page.tsx:118-135, cache at 77-83).
19. [BUG] Trusted-contact "saved" note never resets: `contactSaved` stays true through subsequent edits, so an edited-but-unsaved form still reads "Trusted contact saved." (account/page.tsx:134-142,444).
20. [BUG] Home week placeholder renders wrong day letters: the fallback objects use `date: "0".."6"`, and `new Date("0")` parses (year 2000) in Chromium — `days[getDay()]` yields a real but wrong letter instead of falling through to `days[i]` (home/page.tsx:274-279).
21. [BUG] "Mood this week" plots the last 7 **check-ins**, not 7 days — five check-ins today draw a week-looking line under a "This week" label (home/page.tsx:108,270-302). Insights and Android both bucket by time; the rail card's honesty claim ("only ever the user's real days", comment at 104-107) is not what the code does.
22. [BUG] `signIn()` maps every non-OK response to "Invalid email or password." — a 500, 503 or rate-limit reads as a credentials mistake and invites password resets (apps/app/lib/api.ts:168-177; surfaced via AuthPanel.tsx:62-68).
23. [BUG] OTP and social sign-ups skip the 18+ gate entirely: `requireAgeAttest` renders only for password mode (`!useCode`, components/AuthPanel.tsx:244-254), and the onboarding resume jumps a session straight to step 6 (consent), past the Disclosure/18+ step (apps/app/app/onboarding/page.tsx:33-38) — yet `applyOnboarding` still POSTs `/users/me/attest` claiming "both were gated in the funnel" (apps/app/lib/onboarding.ts:117-121).
24. [BUG] Sign-out leaves sensitive local caches behind: `clearSession()` removes only the refresh token — the cached **safety plan** (`cbz-safety-plan`), journal draft, onboarding draft (feelings/consent answers) and ritual stay readable by the next user of a shared browser (lib/api.ts:26-29; safety-plan/page.tsx:65; journal/page.tsx:33; lib/onboarding.ts:86). Account deletion (account/page.tsx:197-205) leaves them too.
25. [BUG] `tsconfig.tsbuildinfo` is committed in `apps/app/` — a build artifact in the repo that churns on every typecheck (apps/app/tsconfig.tsbuildinfo).

### Feature parity vs Android (web claims parity via WEB_PARITY waves; Android is the reference)

26. [PARITY] No time-aware Sleep ordering: the page is a fixed column (hero → rhythm → education → ritual → soundscapes → stories → check-in) at every hour; Android reorders morning vs evening around the tested `checkInLeadsAt(4..16)` boundary (apps/app/app/(authed)/sleep/page.tsx:100-205 vs apps/android/.../SleepScreen.kt:231,621-623).
27. [PARITY] Sleep diary is not editable: history nights are read-only rows; Android taps a night into the form and upserts by date (`editDate`, SleepScreen.kt:326-328,718-746). Web can only re-save *today* (sleep/page.tsx:81-90) — "edits welcome" (line 202) is only true for the current morning.
28. [PARITY] Sleep save hardcodes `awakenings: 0` — because the backend upserts by date, a web re-save silently zeroes an awakenings count recorded from another client (sleep/page.tsx:86).
29. [PARITY] Sleep "edit" doesn't prefill quality: bedtime/wake prefill from the latest night but `quality` resets to 0, so the promised edit forces re-answering "how rested" from scratch — and the prefill uses `nights[0]` even when that entry is days old (sleep/page.tsx:73-76).
30. [PARITY] Chat has no "Start fresh": Android exposes `talk_start_fresh` (TalkScreen.kt:870); on web the only way to reset the companion is the destructive full memory wipe on /patterns, three clicks away.
31. [PARITY] Chat has no presence line: Android's PresenceHeader states offline/hearing/thinking/speaking (TalkScreen.kt:824-848); the web's only signal is "…" inside the Send button (chat/page.tsx:352-354).
32. [PARITY] Chat has no memory-state line: Android's disclosure dialog appends whether memory is on or off (`memoryLine`, TalkScreen.kt:939-944); the web ai-note says nothing about memory (chat/page.tsx:275-279).
33. [PARITY] No auto-fallback send: when the Oracle stream fails, Android silently re-sends the same words through deterministic `/chat` so the message is never lost (TalkScreen.kt:523-541); the web shows an error bubble and makes retry a manual chip (chat/page.tsx:159-176). Crisis lines *are* dialable via CrisisLines — that half of the parity set is genuinely done.
34. [PARITY] Toolkit has no recents: Android remembers recently used tools as a pick-up-again chip (Extras.kt:917); the web Toolkit renders the same static order every visit (apps/app/app/(authed)/games/page.tsx:66-140).
35. [PARITY] Journal has no word count: Android shows a live `journal_word_count` plural while writing (JournalScreen.kt:188,330-333); the web composer gives no length feedback (journal/page.tsx:160-165).
36. [PARITY] No localized mood display — moods render the raw stored English string and the whole client is `lang="en"` hardcoded UI; Android maps mood names through string resources (`moodLabelResFor`, TodayScreen.kt:260-273, values-hi/). The only localized surface on web is the consent notice (13 languages), which makes the gap visible: a Hindi-notice user gets an English product (app/layout.tsx:59; home/page.tsx:12-18).
37. [PARITY] Journal search is submit-button-only; Android debounces live at 350 ms (JournalScreen.kt:246). Minor, but the same server index is behind both (journal/page.tsx:171-186).
38. [PARITY] Chat suggestion chips ignore their `action`: web sends the label back as a user message; Android carries `(label, action)` pairs and routes them (chat/page.tsx:331-337 vs TalkScreen.kt:534-541). Only the `crisis` action is special-cased (155-157).
39. [PARITY] Mood intensity is hardcoded `3` for all five moods (home/page.tsx:96); Android's mood taxonomy carries per-mood intensity (TodayScreen.kt:111) — web check-ins flatten a dimension the insights engine reads.
40. [PARITY] Premium subscribers cannot play premium narration on web: Sleep/Library/Programs fetch `/content` **unauthenticated** (`fetch`, not `authedFetch`), so the backend's entitlement-gated `playback_url` sees `user=None` and strips `audio_url` from premium items even for paying users (sleep/page.tsx:77-78; library/page.tsx:56; programs/page.tsx:73; backend/app/services/media.py:225-251).
41. [PARITY] Soundscape tiles are inert decoration: non-interactive `<div>`s with play-suggesting art next to stories that do play — no handler, no href, no disabled semantics (sleep/page.tsx:155-162). The footnote is honest about the mixer, but a grid of card-styled tiles that do nothing on click reads as broken, not absent.
42. [PARITY] No search surface at all: Android ships SearchScreen.kt; web removed the fake header search (correct) but offers no way to search content/library (components/AppHeader.tsx:6-9).

### Sequence / placement / discoverability

43. [SEQ] Morning check-in — the page's only data-producing action — is the **last** section of Sleep, below two content rails, at every hour including morning (sleep/page.tsx:189-204). Follows from #26 but is a placement problem in its own right.
44. [SEQ] `/plan` and `/library` skip `AppHeader` and hand-roll a bare `h1` — losing the shared eyebrow/title pattern *and* the SPA focus-management fix that AppHeader carries, so SR users navigating to these two pages keep hearing the old page (plan/page.tsx:46-50; library/page.tsx:66-68; AppHeader.tsx:20-23).
45. [SEQ] Goals & habits is unreachable on mobile: it exists only in the desktop sidebar (layout.tsx:19); no Home tile, jump card, or mobile tab links `/goals` (grep: zero `href="/goals"` outside the sidebar), and the sidebar is `display:none` under 960 px (globals.css:552-553).
46. [SEQ] No sign-out on mobile: the only sign-out control is the sidebar footer icon (layout.tsx:139-146); the mobile "You" tab lands on /account, which has no sign-out — a phone user cannot end their session (account/page.tsx has no signOut usage).
47. [SEQ] Mobile navigation cliff: Insights, Plan, Programs, Library, Toolkit, Safety plan and Patterns are absent from the 6-tab mobile bar (layout.tsx:35-42) and reachable only via Home tiles/links (Toolkit only as "Games"/"Breathe" tiles); a user landing on Talk or Sleep has no path to Insights at all without going Home first.
48. [SEQ] "See plans" (sidebar upsell, free-limit card) leads to /account where there is no plans page — just a single "Upgrade to Premium" button with no tier, price, or comparison (layout.tsx:120; chat/page.tsx:211; account/page.tsx:246-251).
49. [SEQ] Programs page's primary hero CTA ("Begin with today's plan") navigates **away** from Programs to /plan, while the actual enroll affordance is a low-contrast borderless text link inside each card (programs/page.tsx:95-101,182-188).
50. [SEQ] Settings order contradiction: the India-hardcoded "Talk to a human" card (Tele-MANAS/iCall, tel: links) sits directly **above** the "Crisis resources region" selector that says region matters — a US/GB user reads India-only numbers first (account/page.tsx:373-407 vs 409-417). "Find a therapist… near you" also hardcodes `/in` (account/page.tsx:396).
51. [SEQ] Home "Today's plan" shows an empty hole while loading and when a plan has zero steps — the `sec-head` renders, then nothing, then "Jump back in"; only the *failure* case gets a fallback row (home/page.tsx:206-239).
52. [SEQ] Library has no loading or empty state: a slow or empty catalogue renders just the heading and the mixer footnote — indistinguishable from broken (library/page.tsx:64-106).
53. [SEQ] /support back link sends signed-out visitors into the auth loop: "← Back to CereBro" → /home → guard → /signin?next=/home (support/page.tsx:19; layout.tsx:59-66). The page is deliberately public; its exit isn't.
54. [SEQ] Helpline URL drift inside the one file that exists to prevent drift: `CRISIS_LINES` uses `https://findahelpline.com`, `HUMAN_SUPPORT` uses `https://www.findahelpline.com/in`, /support's footnote says "findahelpline.com", account hardcodes a third copy (lib/crisis.ts:18,25; support/page.tsx:31-33; account/page.tsx:396).

### State / hydration / console-error risks

55. [BUG] Chat loads the entire conversation history unpaginated (`GET /chat`, no limit) on every visit — long-lived accounts pay an unbounded fetch and render (chat/page.tsx:97). Every other list in the app passes `?limit=`.
56. [BUG] Chat auto-scroll fires on the initial history load (`endRef.scrollIntoView` on any `messages` change), yanking the page to the composer the moment history arrives (chat/page.tsx:103-105).
57. [BUG] Onboarding "Enter CereBro" (`finish`) fires `track("onboarding_done")` *after* `applyOnboarding` awaits three PATCHes — with `keepalive` unset on those, a user who closes the tab at the last screen loses both the writes and the funnel terminal event; no failure surface exists if any of the three writes matter (onboarding/page.tsx:62-68; lib/onboarding.ts:117-142 swallows all).
58. [BUG] The sleep re-fetch after save is fire-and-forget with a swallowed catch, so the "Your rhythm" card can silently show pre-save data next to the "Saved" confirmation (sleep/page.tsx:88).
59. [BUG] Web-push unsubscribe leaves the server believing failure states: if the server `DELETE` fails, `finally` still unsubscribes locally — the backend keeps POSTing to a dead endpoint until its own cleanup (lib/push.ts:62-73). Acceptable-if-deliberate; deserves a comment or retry.

### Accessibility

60. [A11Y] GuidedTour is `role="dialog" aria-modal="true"` with **no focus trap, no initial focus, no Escape** — keyboard/SR focus stays in the background page the overlay visually blocks (components/GuidedTour.tsx:61-108).
61. [A11Y] The tool-confirm card is `role="alertdialog"` but focus is never moved to it — an SR user can miss that the companion is waiting for permission to write to their account (chat/page.tsx:309-319).
62. [A11Y] `aria-live="polite"` sits on the **entire** conversation section, so every streamed token mutates the live region — screen readers re-announce the growing reply on each chunk instead of once when settled (chat/page.tsx:281-283, streaming node 304-308).
63. [A11Y] AuthPanel uses `role="tablist"/"tab"` with no `tabpanel`, no `aria-controls`, and no arrow-key handling — the tabs ARIA pattern is claimed but not implemented; plain toggle buttons would be more honest (components/AuthPanel.tsx:162-181).
64. [A11Y] Sleep quality uses `role="radiogroup"/"radio"` on buttons with no arrow-key navigation or roving tabindex — SR users are told it's a radio group that doesn't behave like one (sleep/page.tsx:192-197).
65. [A11Y] Home's mood sparkline is `aria-hidden` with no text equivalent — sighted users get a trend, SR users get only a "Details" link; the Insights chart at least pairs with the drift sentence (home/page.tsx:288-302).
66. [A11Y] No `<main>` landmark in the authed shell: content lives in `<div className="app-main" id="main">`, and the skip-link targets that non-focosable div without `tabIndex={-1}` — /support and /crisis use real `<main>`, the app pages don't (layout.tsx:150; support/page.tsx:18).
67. [A11Y] The Urdu consent notice renders LTR: `lang` is set but never `dir="rtl"` for `ur`, so the one RTL script in the 13-language set lays out backwards (account/page.tsx:310; onboarding/page.tsx:418; lib/consentNotice.ts اردو entry).
68. [A11Y] State-check auto-advances 450 ms after selection (`setTimeout(onContinue)`) — motion/timing the user didn't request, no chance to review the choice, and the Continue button that implies review is bypassed (onboarding/page.tsx:266-270).
69. [A11Y] "Start talking" removes itself and renders the composer without moving focus — keyboard focus falls to `<body>`; the input should be focused on `begin()` (chat/page.tsx:191-194,338-355).
70. [A11Y] Pause/Resume buttons always show the **play** icon: `Icon.play` renders beside "Pause" in guided imagery and PacedBreath — icon contradicts label (games/imagery/page.tsx:150-156; components/RitualSteps.tsx:87-93).

### Security / CSP

71. [SEC] The production CSP breaks social sign-in the day it's configured: `script-src 'self' 'nonce-…'` (and `connect-src 'self' API`) has no allowance for `accounts.google.com` / `appleid.cdn-apple.com`, which `lib/social.ts` injects at runtime — Google/Apple buttons will throw script-load failures in prod once `NEXT_PUBLIC_GOOGLE_CLIENT_ID` lands (middleware.ts:28-47; lib/social.ts:26-39,46,72-74). Either allow the provider origins when configured, or document the constraint next to the env vars.
72. [SEC] The middleware sets only CSP — no `X-Content-Type-Options`, `Referrer-Policy`, or `Permissions-Policy`; those exist solely in Caddy's prod snippet, so dev/e2e/direct-port deployments (and any future non-Caddy host) run without them, and the three Next apps have already diverged from the API's header set (middleware.ts:50-55; deploy/Caddyfile).
73. [SEC] Refresh token in `localStorage` is the documented v1 tradeoff (WEB_APP_PLAN §3) — restating here as accepted residual risk: any XSS is a durable session steal; the planned BFF/httpOnly-cookie v2 is the fix. No action beyond keeping the CSP strict, but the plan's own "fix admin the same way" is done while the BFF follow-up has no TODO entry.

### Honest-copy (cross-checked against docs/CLAIMS_MAP.md)

74. [BUG] Sidebar upsell promises "the full sleep library, and deeper insights" — no mechanism: the web enforces no premium content gating surface of its own (and #40 means premium audio is *less* available), "deeper insights" doesn't exist on any client, and CLAIMS_MAP has no row for either phrase; only "unlimited talks" is backed (`services/usage.py`) (layout.tsx:117-122 vs docs/CLAIMS_MAP.md §3).
75. [BUG] "2-minute reset" copy on web has no two-minute mechanism: the CLAIMS_MAP row pins `twoMinutesReached` in Android `Breathe.kt` + `BreathePacingTest` only — the web funnel's "Try a 2-minute reset" CTA, Home's "Want a 2-minute reset?", and the chat starter "Just two minutes to reset" route to breathers with no elapsed-time mark at all (onboarding/page.tsx:174; home/page.tsx:17; chat/page.tsx:15; games/page.tsx:20-61 vs docs/CLAIMS_MAP.md §3 row "Two-minute reset").
76. [BUG] Sleep check-in claims "one entry per morning, edits welcome" — the backend upsert makes the first half true, but the form's date is always *today* (`todayISO()`), so a morning entry describes last night while an evening save overwrites the same row; with no per-night editing (#27) the "edits welcome" promise is a single mutable row, not an editable diary (sleep/page.tsx:81-90,202).

### Performance

77. [PERF] No client-side cache of any kind: `lib/api` is raw fetch with no SWR/stale-while-revalidate, so every tab switch refetches everything (Home alone fires 6 requests) and every page flashes its empty state before data pops in — the cz-in entrance animation partially masks a loading-state gap that exists on Home, Sleep, Journal, Insights and Chat (lib/api.ts; home/page.tsx:81-90).
78. [PERF] Library downloads the entire catalogue and filters client-side even when `?kind=` is present, though the server supports `?kind=` (Sleep uses it two files away) (library/page.tsx:55-62 vs sleep/page.tsx:77-78).
79. [PERF] `body { background-attachment: fixed }` on a gradient forces repaint-on-scroll on mobile browsers — a known Android-Chrome jank source on exactly the low-end devices the mobile layout targets (globals.css:121-126).
80. [PERF] Chat mounts fire three serial-ish requests (`/auth/me`, `/chat` unbounded, `/oracle/status`) before the first message can safely send (thread id, #2 depends on the first) (chat/page.tsx:95-101).

## Positive verifications (checked, not flagged)

- Sleep theme scoping follows the 2026-08-04 owner decision; `.theme-night` re-scopes ground correctly and `e2e/tests/theme.spec.ts` exists.
- Crisis surfaces: Tele-MANAS-first ordering, every number a `tel:` target, single `CrisisLines` implementation, static /support and /crisis render without JS/auth/network (lib/crisis.ts, components/CrisisLines.tsx).
- Refresh-rotation race is correctly deduped (`refreshInFlight`, lib/api.ts:53-80); FreeLimitError vs IP-429 disambiguation is right (api.ts:117-140).
- `?next=` open-redirect handling (`safeNext`) and the `?kind=` prototype-pollution guard (`Object.hasOwn`) are both correct.
- Reduced-motion coverage of the cz* vocabulary is thorough (globals.css:885-899); per-request CSP nonce plumbing is sound.
- Honest empty states for charts/patterns ("no invented line") match the no-guessing claims; the deleted fake mood fallback stayed deleted.
