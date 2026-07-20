# Reference parity — what to take from `ref/Zen`, and what not to

Last updated: 2026-07-16

A feature-by-feature comparison of our four client surfaces against the Zen
reference (`ref/Zen/apps/{web,admin,app,android}`), following the reference's own
`WEB_PARITY.md` / `IOS_PARITY.md` convention.

**The translation rule.** Zen is a **B2C consumer wellness** product. We are **B2B
enterprise coaching**. Most differences are deliberate, and `docs/PRODUCT.md`
§"What we deliberately do not build (v1)" + the feature matrix are the authority —
not this file. A feature is only worth taking if it survives that translation.

**The finding that shapes the list.** Our Android app is a *fork* of the reference
with the B2C screens deleted — but the client methods those screens called survived.
Most Android "gaps" are therefore **wiring against endpoints that already work**,
not new features. The same shape holds on web: the highest-value items are
backend-complete and client-missing.

**Caveat, learned the hard way (2026-07-16).** "Orphaned method" does not imply
"endpoint exists". Some orphans point at the *reference's* backend routes, which were
never ported — item 3 was filed as "Wiring" and turned out to need a whole engine
feature. Check the route exists on OUR services before trusting an Effort column here.

Verdicts: **TAKE** · **ALREADY-AHEAD** (we have better) · **CORRECTLY-ABSENT**
(deliberately dropped) · **TRAP** (copying it breaks our model).

---

## TAKE — ranked across all surfaces

Rank is by value, not by effort. "Wiring" = the server side already exists and is
tested; only a client calls it into being.

| # | Feature | Surface | Ref source | Why it survives the B2B translation | Effort |
|---|---|---|---|---|---|
| 1 | ~~**Crisis screen in the coach**~~ **DONE 2026-07-16** | `apps/app` | `lib/oracle.ts`, `chat/page.tsx:76,146` | The engine already detects a crisis, takes over deterministically, and streams a scripted reply — and the web client renders it **as ordinary chat text**. `safety_flag` ships on the `done` frame; `crisis_region` is resolved on `/users/me`; `/v1/safety/helplines` is deliberately ungated *specifically to back this screen*. PRODUCT.md matrix: crisis takeover = v1 ✔. | Wiring |
| 2 | ~~**Export + delete as product functions**~~ **DONE 2026-07-16** | `apps/app` | `account/page.tsx:117-131,159` | `GET /v1/privacy/me/export` and `DELETE /v1/privacy/me` are built and tested; the client never calls them. The marketing site **sells** "deletion is a product function" — a rule-6 claim with no client mechanism. | Wiring |
| 3 | ~~**Memory/pattern transparency + "delete what it learned"**~~ **DONE 2026-07-16** | `apps/android` | `ui/screens/PatternScreen.kt` | **Was misclassified as "Wiring" here — it was not.** The orphaned `Api.patterns()`/`Api.deleteMemory()` pointed at the REFERENCE backend's routes (`/insights/patterns`, `/users/me/memory` on the platform), which were never ported: every call would have 404'd. Built for real — engine `stores/patterns.py` (4 consent-gated rules, each with its basis) + `GET /v1/wellness/patterns`, and `erasure.forget_user` + `DELETE /v1/privacy/me/memory` as a strict subset of the erasure registry. | Engine feature + screen |
| 4 | ~~**Member deactivate / reactivate**~~ **DONE 2026-07-16** | `apps/admin` | `admin.py:160`, `page.tsx:373` | `/orgs/me/people` is **GET-only**: an org_admin cannot offboard a leaver, and the seat stays consumed (`_seats_used` gates invites). The core B2B HR operation, absent. Needs a platform route. | Small route + UI |
| 5 | ~~**CSP on `apps/app` and `apps/web`**~~ **DONE 2026-07-16** | both | `apps/{web,admin,app}/middleware.ts` | The reference ships CSP on **all three**; we have it on admin only, because our TODO said "Admin: nonce-CSP" and under-specified it. `apps/app` holds the transcripts. **Don't copy the ref for web** — a nonce forces dynamic rendering and our marketing site is static; use `next.config.ts` `headers()` with a static CSP there, and `proxy.ts` (Next 16) for the app. | Small |
| 6 | ~~**Security headers on the marketing site**~~ **DONE 2026-07-16** | `apps/web` | `vercel.json` | We send **zero** — no CSP, XFO, nosniff, Referrer-Policy — while hosting a 257-line `/security` page. A prospect's CISO runs securityheaders.io before the demo call. | Trivial |
| 7 | ~~**Daily check-in + calm progress on Today**~~ **DONE 2026-07-16** | `apps/android` | `TodayScreen.kt:241,341,385` | `Api.streak()` / `Api.moods()` orphaned; `Api.checkIn` has **exactly one caller — onboarding**. One write at signup, never again. PRODUCT.md ships check-ins v1 and promises "progress shown calmly"; the affordance has no surface. (A week-ring is not a coin — not the Dropped item.) | Wiring |
| 8 | ~~**Tenant seat / crisis_region editing**~~ **DONE 2026-07-16** | `apps/admin` | `page.tsx` Users tab | Seats + crisis region are now editable inline (regions fetched from the engine, so a region it cannot localise cannot be picked). **`regulated_mode` deliberately stays read-only** — SECURITY.md calls its opt-out "a contract-level decision with counsel sign-off, not an admin toggle", so exposing the switch this row originally implied would have contradicted our own policy. | Trivial |
| 9 | ~~**Access token in memory, not localStorage**~~ **DONE 2026-07-16** | `apps/app` | `lib/api.ts:5-23` | The ref keeps **access in memory**, refresh in localStorage — "XSS can't lift it from storage". We persist both. SECURITY.md names the "Zen pattern" as our auth commitment; we took the coalesced-refresh half and not this one. | Small |
| 10 | ~~**Coach thread survives app kill**~~ **DONE 2026-07-17** | `apps/android` | `TalkScreen.kt:150` | Another mislabelled "Wiring": `Api.chat()` pointed at `/chat` on the PLATFORM, which never existed there. Repointed at the engine's real `/v1/sessions/resumable` + `/{id}/history`. **Uncovered three server bugs that made session history impossible for every tenant** — see the warts section. (`Api.starters()` is also mislabelled here: it calls `/assessment/topics`, not starters.) | Wiring + 3 engine bugs |
| 11 | ~~**In-conversation AI disclosure pill**~~ **DONE 2026-07-17** | `apps/android` | `TalkScreen.kt:1056` | The strings **already ship** (`strings.xml:84` `talk_disclosure_pill`) and nothing renders them. We disclose once, at onboarding. | Trivial |
| 12 | ~~**Journal read view on Android**~~ **DONE 2026-07-17** | `apps/android` | `JournalScreen.kt` | `Api.createJournal` fires from the breathing tools, but `Api.journal()` has no caller — **you cannot read your journal on the phone** — and `BiometricGate.kt` gates nothing. | Wiring |
| 13 | ~~**Roster search + pagination**~~ **DONE 2026-07-17** | `apps/admin` | `page.tsx:364-425` | `/orgs/me/people` returns every row unbounded. A 2,000-seat tenant renders 2,000 rows. B2B rosters dwarf a B2C ops list. | Small |
| 14 | ~~**Prompt version history + rollback**~~ **DONE 2026-07-17** | `apps/admin` | `page.tsx:837-948`, `admin.py:458-524` | We have a monotonic version counter and an audit line to stdout; no list-versions, no activate-old, no revert-to-default. **A bad prompt save is unrecoverable from the console.** | Engine route + UI |
| 15 | ~~**Safety escalation acknowledge/resolve**~~ **DONE 2026-07-17** | `apps/admin` | `admin.py:359` | Our queue is read-only, so it never drains and re-notifies forever. Ack is a status, not content — stays inside rule 5. (Take the *ack*, never the excerpt — see TRAPS.) | Engine route + UI |
| 16 | ~~**Session-expiry message**~~ **DONE 2026-07-17** | `apps/admin` | `page.tsx:145` | After a failed refresh we throw a raw 401 into a `Failed` card with a Retry that cannot work. The ref says "Session expired — reload to sign in." | Trivial |
| 17 | **Workbook upload button** — **NOT trivial; re-scoped 2026-07-17** | `apps/admin` | — | `POST /v1/prompts/upload` exists, but it is **S3-only**: it validates, backs up the current S3 object, replaces it, and its own comment says the reload is "effective when PROMPT_SOURCE=s3". Every deployment here runs `PROMPT_SOURCE=codebase`, so a button wired to it would upload into a bucket that isn't configured and change nothing locally — while telling an operator the global workbook was replaced. Attempted and REVERTED: the rejection path worked end-to-end (a non-workbook is refused in the server's own words), the success path could not be demonstrated. **The real work is a decision + a codebase-source write path**, not a button. Exactly the caveat at the top of this file: an existing route is not a working one. | Engine work + a decision |
| 18 | ~~**Nudge dispatch button**~~ **DONE 2026-07-17** | `apps/admin` | `page.tsx:164` | `POST /v1/nudges/dispatch` exists; the Nudges tab never calls it. Zero backend work. | Trivial |
| 19 | **Guided tour** | `apps/android` | `TodayScreen.kt:1036` | `GuidedTourOverlay` is defined at `GuidedTour.kt:55` and **never called**. Enrolled enterprise users need orientation *more* than self-selected consumers. | Trivial |
| 20 | **DPDP s.5(3) consent-notice languages** | `apps/app` | `lib/consentNotice.ts` | English + 12 Eighth-Schedule languages with a picker. Our six labels are English literals. Statutory; deadline 13 May 2027; Indian employees are exactly the population. | Medium |
| 21 | **FAQ + FAQPage/Organization JSON-LD** | `apps/web` | `page.tsx:33-249` | Zero `ld+json` anywhere in our site. The demo-blocking objections ("what does HR see?", "where is data hosted?", "DPDP?") are FAQ-shaped and already answered in SECURITY.md. Free reach for a site whose only job is earning the demo. | Small |
| 22 | **Branded OG/Twitter images** | `apps/web` | `opengraph-image.tsx` | Ours point at `/hero.jpg` — a stock photo with no product name. Matters exactly when a champion pastes `/security` into Slack for their CISO. | Small |
| 23 | **Web push / nudges** | `apps/app` | `lib/push.ts`, `public/sw.js` | PRODUCT.md v1 ✔. Ship **title-only / generic bodies** — see TRAPS. | Medium |
| 24 | **`viewport`/`themeColor`, sitemap detail, robots host** | `apps/web` | `layout.tsx:41-43`, `sitemap.ts` | Minor, one-line each. | Trivial |

### Docs to adapt (unblocks four open TODO items)

`ANDROID_RELEASE.md` · `PRIVACY_LABELS.md` · `ANDROID_QA.md` (TalkBack sweep,
130/200% font scaling, RTL, contrast) · `BREACH_RUNBOOK.md`. Our TODO says "adapt"
exactly these; they were unavailable until the reference landed on this machine.

---

## TRAPS — present in the reference, must not be ported

| Trap | Ref source | Why it breaks us |
|---|---|---|
| **Safety queue "Excerpt" column** | admin `page.tsx:1057` | Renders the flagged journal/chat text. Direct violation of CLAUDE.md rule 5 and SECURITY.md ("no HR/admin/API surface exposes content — endpoints don't exist"). Our signal-only queue is the correct answer. |
| **Per-user detail view** | admin `page.tsx:309` | Per-person mood/journal/chat counts. Even as *counts* it is an individual record — PRODUCT.md: "no transcripts, no individual records, ever". Cohort floors exist precisely to prevent this; a drill-down is the k-anonymity bypass. |
| **Hardcoded helplines** | app `chat/page.tsx:154` (KIRAN `1800-599-0019`) | The exact bug fixed on Android 2026-07-16. Clients must not hold a country's numbers; build from `/v1/safety/helplines` + resolved `crisis_region`. |
| **Third-party script injection** | app `lib/social.ts:31-46` | Injects `accounts.google.com/gsi/client` at runtime — breaks nonce-CSP and the air-gapped posture. B2B is seats/SSO anyway. |
| **Onboarding draft in localStorage** | app `lib/onboarding.ts:92-106` | Persists free-text to disk on a possibly shared machine. This is the disk-persistence risk — *not* the service worker, which caches nothing. |
| **Push body carries content** | app `sw.js:14-18` | Passes server `data.body` straight to `showNotification` — a nudge would paint coaching content on a lock screen. RFC-8291 encrypts the wire, not the screen. |
| **Emergency contact + `notify_consent`** | app `account/page.tsx:266-282` | In a workplace deployment this edges toward the disclosure our model forbids (escalation sends "ids + timestamp, never the disclosure content"). Do not port without counsel. |
| **Broadcast nudge authoring** | admin `page.tsx:951` | An HR admin push-blasting employees' coaching app is a coercion surface, not a feature. Our nudges are commitment-derived. |

---

## CORRECTLY-ABSENT — deliberately dropped, do not "fix"

> **SUPERSEDED IN PART — 2026-07-19: B2C self-serve is now in scope** (deliberate
> product decision, not drift). CereBroZen is now **B2B *and* B2C**: individuals can
> self-serve into a personal org-of-one and optionally buy **CereBro Plus** (freemium).
> This REVERSES the "no self-serve signup / no consumer billing / no acquisition
> funnel" items below for personal accounts only — enterprise remains seat-licensed
> and demo-gated exactly as before. Shipped: `POST /auth/signup` + personal orgs,
> the `Subscription` model + `/billing` (mock provider, keyless). Pending: app
> paywall/gating, web pricing page, real Stripe/Play adapters, consumer ToS (draft at
> `docs/legal/CONSUMER_TERMS_DRAFT.md`). The safety model is unchanged and still
> load-bearing — see the B2C entry in `docs/TODO.md`. Items still correctly absent for
> BOTH models: coins/badges/leaderboards, video emotion-analysis, ElevenLabs (v2).

Premium/paywall/billing (~~PRODUCT.md: seats instead~~ — **now: seats for B2B, Plus for B2C**) ·
coins/badges/leaderboards · ~~waitlist +~~ public pricing (**now shipped for B2C**;
enterprise still demo-gated via `DemoForm`) · social sign-in (**app has OTP/Google UI;
backend pending**) · onboarding acquisition funnel (**now in scope for B2C**) · content
library + media catalogue (v2) · ElevenLabs narration (voice = v2) · App Store badges ·
B2C retention cohorts (now a valid unit for personal accounts) · ref `/support` page
(`/contact` covers it).

Android hygiene: `Settings.kt:296` still defines an unreachable `PremiumScreen` +
`premium_*` strings; ~102 strings for removed screens still ship. Four of those
groups go live again if items 3/11/12 are built.

---

## ALREADY-AHEAD — we are better; do not regress toward the reference

- **Real RBAC**: two server-enforced roles vs the ref's single `is_admin` bool.
- **Cohort floors / k-anonymity**: the reference has none anywhere.
- **Multi-tenancy**: orgs, seats, invitations, accept flow — the ref has no org concept.
- **Per-region helplines from the engine** (`data/Helplines.kt`) — the ref hardcodes India.
- **Stricter CSP**: `'strict-dynamic'`; the ref's middleware has none.
- **403-as-consent-refusal** (never burns a refresh) + `adoptTokens` on consent rotation.
- **Agent-flow canvas**, Console, workbook validation — no ref equivalent.
- **Skeleton + Failed/Retry** everywhere; the ref has no loading states at all.
- **Demo credentials proven dead-code-stripped** via `NODE_ENV`-only folding.
- **Mobile nav + `SignInMenu`** with full ARIA — the ref's own audit rates its missing
  mobile nav *Critical*.
- **`api/demo/route.ts`**: dual delivery, honeypot, field caps, honest 503.

---

## Server bugs found while building, and fixed

Item 10 could not be built until these were. All three were **silent** — the writes
succeeded, the reads simply returned nothing, and no error surfaced anywhere:

1. **The SSE worker dropped the tenancy key.** `_sse_response` re-stamps the correlation
   ids into its worker thread (`request_id`, `user_id`, `session_id`) but not
   `ctx_org_id` — which is not a correlation id but the key every store writes with. So
   `current_org()` fell back to `DEFAULT_ORG` and **every streamed turn recorded its
   conversation under org "default"** instead of the caller's. Reads use the real org, so
   nothing could ever find them. SECURITY.md calls tenancy "the sharpest inherited edge"
   and says it is "tested with cross-tenant access tests" — those tested the STORES, not
   the SSE path (`test_sse_tenancy.py`).
2. **`PgCollection.find_one()` did not accept `sort=`.** Three live callers pass it
   (`latest_resumable`, and the NBI/DISC profile reads); every one raised TypeError into a
   broad `except` and was logged as a warning. Postgres is the DEFAULT store.
3. **`PgCollection` had no `insert_one`.** Every store predating `prompt_versions` writes
   with `update_one(..., upsert=True)`, so the shim quacked like a MongoClient only as far
   as the calls already in use. A new store used the plainest write and it raised
   AttributeError on Postgres — the DEFAULT backend — was swallowed, and the feature was a
   silent no-op **while its own tests passed**, because they run against mongomock, which
   has `insert_one`. A unit test against mongomock proves nothing about the shim.
4. **`find_one`'s primary-key guess turned a miss into "absent".** `_key` guesses `_id`,
   else `user_id`, else `session_id`, because collections key differently. Conversations
   key on `session_id`, so `find_one({"user_id": ...})` looked up `_id = <user_id>`, found
   nothing, and reported a document that plainly exists as absent. Writes filter on
   `session_id`, so they were fine — which is exactly why it hid.

Net effect before the fix: `/v1/sessions`, `/v1/sessions/resumable` and the transcript were
empty for every real tenant. PRODUCT.md ships "session history with generated titles" as v1.

Found later, while building item 15 (the escalation ack) — same root, the shim:

5. **`_project` dropped `_id` even when asked for it.** mongomock returns `_id` by default;
   the shim filtered it out of the projection and never put it back. The safety queue has
   to NAME a record to acknowledge it, so every row would have carried an empty id on
   Postgres and the Resolve button would have had nothing to send.
6. **`insert_one` would have destroyed a safety record.** Fixing (3) flipped the
   `hasattr(coll, "insert_one")` branch in `escalate()`, whose `update_one` fallback had
   carefully built a unique `_id`. The naive `insert_one` reached for `_key`'s guess, which
   resolves an escalation to its **`user_id`** — so a person's second crisis would have
   overwritten their first, silently, destroying exactly the record an incident review
   needs. `_persist` now names its own `_id` and `insert_one` mints a uuid rather than
   guessing; a duplicate `_id` raises instead of upserting.
7. **A cold start crashed under concurrent first use.** `CREATE TABLE IF NOT EXISTS` is not
   atomic in Postgres: two creators racing on a NEW table both pass the existence check and
   the loser dies with `UniqueViolation` on `pg_type_typname_nsp_index` — an error naming
   the catalog, not the table. Only possible on a table's first use, so it is invisible
   against any database that already has its schema and arrives exactly once: on a fresh
   deployment's first concurrent requests. Found by wiping the dev volume and sending one
   crisis turn — which 500'd. `_ensure` now locks within the process and tolerates the
   cross-process race by SQLSTATE.
8. **A legacy `_id` could render but never resolve.** Escalations written before `_persist`
   named its own `_id` carry a Mongo ObjectId: it is not JSON-serialisable (500s the whole
   queue) and it never matches the string the console sends back (Resolve 404s forever).
   The rows an operator most needs to clear are the oldest ones.

The through-line in 2, 3, 5, 6: **Postgres is the default store and essentially nothing
tested it** — the whole engine suite runs on mongomock. Every one of these passed a green
suite while being broken on the backend that ships. `tests/conftest.py` now carries the
`pgdb` fixture and `requires_pg` marker so store-behaviour tests can run against the real
thing; CI should run the suite with a Postgres available.

## Both ops queues were permanently empty — FIXED 2026-07-17

**The safety queue and the nudge queue can never show a row, for anyone.** Not a
regression; true since the ops queues were org-scoped. Measured against the composed
stack, not reasoned about:

- `escalate()` stamps the record with the **customer's** org (`_org_id()` → the JWT's
  `org_id`). A demo member's crisis wrote `org_id = 6da49ab5…` ("Demo Co").
- `GET /v1/safety/escalations` requires **`internal_admin`** — CereBroZen's own operators,
  whose platform-issued token carries `org_id = "internal"`, because they belong to no
  customer org.
- `list_escalations` reads through `scoped({})`, which filters to `current_org()` =
  `"internal"`. It matches nothing, ever. Verified: one row in `crisis_escalations`, and
  `GET /v1/safety/escalations` returns `count: 0` for the only role allowed to call it.
- `notifications.py:166` scopes the nudge queue identically — same result.

Unit tests miss it because they set `ctx_org_id` to the same org `escalate()` stamped, or
run as the default org, where `_LEGACY_DEFAULT` matches. Only a real platform-issued
operator token exposes it.

This is the failure `escalation.py` argues against in its own docstring — "a
silently-unconfigured safety feature is worse than an absent one" — one level up: the
feature is configured, the record is written, and the queue that exists so a human gets
involved shows nothing. The `armed`/`classifier_enabled` pills still read healthy.

The fix was a **tenancy-contract decision, not a bug fix** (CLAUDE.md rule 7 — the answer
changes who can see which employees are in crisis), so it was put to the owner rather than
made unilaterally. Two alternatives were declined: an **org selector** in the console
(deliberate and auditable per view, but a queue you must go looking for per-tenant is a
queue nobody watches — the wrong shape for crisis response), and a **customer-owned
responder role** (closest to escalation.py's own "the client's programme, not our feature",
but nobody responds until a customer staffs it).

**Chosen: `internal_admin` reads across tenants.** Our operators are the responders, so
they see every tenant's rows; each row carries `org_id` and the console shows a Tenant
column. Opt-in per call (`all_orgs=True`), passed only by a route that has already run
`require_internal_admin` — which is what makes that dependency load-bearing rather than
decorative. No other engine read may do it. Still signal-only.

The cost, stated rather than buried: **CereBroZen's own operators can see which employees
at which customer tripped the crisis screen.** No customer role can — an `org_admin` (their
HR) gets 403 on both the read and the ack, verified against the composed stack. Recorded in
SECURITY.md (the tenancy claim now names its exception) and ARCHITECTURE.md.

## Export and erasure on Postgres — FIXED 2026-07-17 (and it was worse than a crash)

Found by running the e2e suite against the composed stack while verifying item 15.
Measured, not reasoned about:

    GET    /v1/privacy/me/export  ->  500   psycopg UndefinedColumn: column "doc" does not exist
    DELETE /v1/privacy/me         ->  400

Both are **statutory DPDP paths**, and PRODUCT.md ships export + delete as v1 functions.

Root cause — a name collision the shim cannot see. `privacy/erasure.py:_locations()` registers
`checkpoints` and `checkpoint_writes` as if they were Mongo collections. On Postgres those
are **LangGraph's own tables**, with LangGraph's schema (`thread_id, checkpoint_ns,
checkpoint_id, …`) — not the shim's `(_id TEXT, doc JSONB)`. `_ensure`'s
`CREATE TABLE IF NOT EXISTS` sees a table of that name, skips it, and the first
`SELECT doc FROM checkpoints` fails. Same family as bugs 2/3/5/6: mongomock has no
LangGraph tables, so the suite is green.

The erasure path **fails closed** (400, refusing to claim a partial wipe succeeded) rather
than reporting a success it did not achieve — that part of the design works, and it is why
this is a bug and not an incident.

Worse, and separate: **`checkpoint_blobs` is not in the registry at all.** The registry's
own comment says checkpoints hold "the entire graph state, including the message history",
but on the Postgres checkpointer the payload lives in `checkpoint_blobs`, keyed by
`thread_id`. So even once the two registered locations are readable, an erasure would
leave the message history behind. Verify the whole checkpointer schema
(`checkpoints`, `checkpoint_writes`, `checkpoint_blobs`) against the registry — do not
just fix the crash.

**The crash was the lucky part.** Fixing it surfaced the real bug: the checkpointer keys
threads by `"<org>:<session_id>"`, and erasure searched for the **bare `session_id`**. It
matched nothing, deleted nothing, then re-scanned with the same wrong filter, found nothing
"remaining", and reported **`verified: True`** — on EVERY backend, Mongo included. A
statutory erasure returning verified success with the entire message history on disk, which
is the precise failure this module's own docstring warns about ("it reported deletions it
never made"). The org prefix tenanted the checkpointer; erasure predates it and was never
told. The unit tests inserted bare `thread_id`s, so they encoded the same wrong assumption
and stayed green. Measured against the live database, where the same id sat in both columns.

Fixed:
* `tenancy.thread_id_for()` is now the ONE place the key is built — the engine writes it and
  erasure searches for it, so they cannot drift again. `thread_ids_for()` also returns the
  bare id, so pre-tenancy threads stay reachable.
* `_checkpoint_backend()` mirrors `get_checkpointer()`'s precedence. Only the MongoDB saver
  stores checkpoints as readable documents; Postgres/SQLite/memory get a
  `_CheckpointerCollection` — a pymongo-shaped view over the saver's own
  `list`/`delete_thread`, the same trick `PgCollection` plays. The saver owns its schema, so
  it is the only thing that can honestly answer.
* That also picks up **`checkpoint_blobs`**, which the registry never listed and which is
  where Postgres actually puts the message history.
* SQLite was the worst case and is now covered too: the checkpoints live in a file the Mongo
  client cannot see, so the delete and the re-scan both found nothing and erasure reported
  success. Silent, not loud.

Verified on the composed stack, not just in the suite: 12 threads / 406 blobs before,
`verified: True` with `checkpoints: 12` deleted, **0 threads / 0 blobs** after. The
pg-backed test fails with "THE CONVERSATION STATE SURVIVED ERASURE" against the old filter.
e2e: 61 passed, 0 failed.

## Warts found while building, not yet fixed

- **`{time}` is a real missing input, and it makes the coach guess the hour.**
  `coaching_intake_agent` says, five times: *"Greet the user based on `{time}` — add a
  relevant human touch based on whether it's early morning, late afternoon, or late
  evening."* Nothing in the stack knows the user's local time — there is no timezone column
  on the platform's user model and no time value in the engine's prompt context — so the
  resolver blanks it and the model reads *"Greet the user based on ``"* while still being
  told to vary by time of day. It will invent one. "Good evening" at 9am is a small,
  visible, avoidable defect, and it fires on the returning-user path (ALL-13-populated →
  greet → skip), which is the common one.
  Two ways out, and it needs a decision rather than a drive-by: **(a)** carry a timezone —
  platform column → token/profile → engine context → register `{time}` (cross-stack, so
  rule 7 applies); or **(b)** drop the time-varying greeting from the prompt (content, so
  the coach owns it). Doing nothing is the current state and it is the worst of the three.
  Pinned as the one known gap by `test_no_shipping_prompt_has_a_placeholder_nothing_can_resolve`,
  so it cannot quietly grow company.

- **A test leaves an injection artifact in the dev database.** `\dt` on the dev Postgres
  shows a table named `convDROPTABLEusers` — a collection name like
  `conv'; DROP TABLE users;--` run through `PgCollection.__init__`'s sanitiser
  (`isalnum() or "_"`), which is the sanitiser doing its job. But the table outlives the
  test that made it, so the evidence reads like a successful injection to anyone who looks
  at the schema later. Give that test the `pgdb` harness (it drops what it creates) rather
  than leaving the artifact behind.
- **A missing store reports "disabled for this tenant" (409).** `wellness.add_mood`
  returns `None` both when the tenant flag is off AND when no store is reachable, and
  `routers/wellness.py` maps that to `_REFUSED`. A developer with no `POSTGRES_URL` is
  told their tenant configuration is wrong. Harmless in prod (a store always exists),
  misleading everywhere else — the two cases want different answers.

## Shared gaps — in neither app, worth doing anyway

- **Tenant list is client-filtered, not paged.** `/orgs` returns every tenant in one
  response; the Tenants filter (2026-07-17) narrows what is SHOWN, not what is fetched, and
  hides itself under 6 tenants. Fine at today's scale, wrong at 500 — the fix is a server
  query + paging like `/orgs/me/people` already has, not a better filter.
- **Admin audit log**: neither console has one; regulated-mode-by-default makes "who
  changed seats / deactivated whom / edited which prompt" table stakes. Ours only
  writes prompt saves to stdout.
- **`not-found.tsx` / `error.tsx`** on web and app — a stock Next 404 on an
  enterprise site is a cheap credibility leak (the ref's own audit flags it).
- **Skip-to-content link, `:focus-visible`** — absent in both. Inherit the fix, not the bug.
- **`prefers-reduced-motion`**: our `apps/app` has **0** guards (`Reveal` on web has one;
  `Counter` and `TestimonialCarousel` do not — the carousel also autoplays with no
  pause control, WCAG 2.2.2).
- **`Counter` renders `0` server-side** (`Stats.tsx` → `+<span>0`): with JS off or to a
  crawler every headline stat reads **+0%**. On a page whose proof *is* the numbers.
  (`Results.tsx` uses static values and is fine.)
- **Analytics**: neither has client analytics. `WEB_PARITY.md` treats this as a
  deliberate privacy-posture decision — needs an owner call, not a silent add.
