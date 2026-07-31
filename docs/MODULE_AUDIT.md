# Module Audit Protocol

> One module at a time: look at it on a real device, score it, fix everything
> found, re-verify, commit. Invoked by naming a module ("audit Splash") — no
> further briefing needed.
>
> Born out of the 2026-07-30 device pass, which found three defects that every
> automated gate had passed: a goal row that broke across three lines, a
> full-screen bright player at bedtime, and (earlier) a fabricated Pattern
> Dashboard. The common factor is that **none of them were visible without
> looking at the screen.** This protocol exists to make looking systematic.

## The one rule

**A finding is not real until it is seen, and a fix is not done until it is
seen again.** Screenshots before and after, on hardware where possible. A
passing build is not evidence about a layout.

## Steps

1. **Locate** — list every file the module owns (screen, view-model, strings in
   both locales, routes in, routes out). State them, so scope is explicit.
2. **Read the intent** — what do PRD.md / REDESIGN.md / the design system say
   this module is *supposed* to do? A module can be flawless and still wrong.
3. **Capture** — launch it on the device and screenshot every state reachable
   without inventing data: empty, populated, loading, error, signed-out. Long
   text and Hindi are states too.
4. **Score** (below), with the reasons that cost points.
5. **Fix everything found**, hardest first. If something cannot be fixed
   without a product decision, say so and leave it — do not guess.
6. **Re-verify on the device**, then run the gates the module touches
   (`testDebugUnitTest` + `lintVitalRelease`, backend suite, `check-claims`).
7. **Commit** with the finding, the evidence, and what was deliberately left.
8. **Update** PRD/ARCHITECTURE/TODO if behaviour or a contract changed.

## Scoring — 1 to 10

Score the module as a user meets it, not as code. Start at 10 and subtract:

| Dimension | What costs points |
| --- | --- |
| **Truthfulness** | Anything shown that is not real: invented data, a control that does nothing, a claim the code cannot back. **Any fabrication caps the module at 3**, however pretty the rest is. |
| **Layout** | Clipping, overlap, text wrapping badly, tap targets under 48dp, breakage at 720px or with Hindi. |
| **Theme** | Wrong palette for the context, raw hex instead of tokens, contrast below 4.5:1, a light screen in a dark moment. |
| **State coverage** | Empty/loading/error states missing, or dishonest ("no data" rendered as zeros). |
| **Accessibility** | Missing labels, unlabelled icon buttons, nothing announced when content changes on its own. |
| **Copy** | Overclaims, jargon, catastrophising, or promises the backend does not keep. |
| **Behaviour** | Actions that fail silently, non-idempotent taps, no undo for destructive things. |

Bands: **9–10** ship as-is · **7–8** minor polish · **5–6** real defects, fix
before release · **3–4** a user would lose trust · **1–2** actively misleading
or broken.

Score honestly. A 6 that gets fixed is worth more than a 9 that was flattery.

## Module list

Android (primary target — a real device is attached):

`splash` · `onboarding` · `home` · `sleep` · `talk` · `journal` · `you` ·
`patterns` · `goals` · `safety-plan` · `programs` · `toolkit` · `crisis` ·
`premium` · `privacy` · `insights`

iOS and web mirror most of these; audit them separately, since the same module
can be right on one platform and wrong on another — the Pattern Dashboard was.

## Log

| Date | Module | Platform | Before | After | Commit |
| --- | --- | --- | --- | --- | --- |
| 2026-07-30 | splash | Android | **5** | 9 | `Splash: the first frame, fixed` |
| 2026-07-30 | onboarding | Android (+iOS/web copy) | **3** | 8 | `Onboarding: four things it told users that were not true` |
| 2026-07-30 | home | Android (+backend seed) | **5** | 8 | `Home: a bright screen at bedtime, and a plan that said its own name twice` |
| 2026-07-31 | sleep | Android (app-wide typography) | **6** | 8 | `Sleep: a row that broke a word in half, and advice printed twice` |
| 2026-07-31 | talk | Android (`Page` gains a pinned footer) | **6** | 8 | `Talk: the composer travelled with the transcript` |
| 2026-07-31 | journal | Android (+claims gate) | **3** | 8 | `Journal: you could write entries and never read one back` |
| 2026-07-31 | you | Android | **6** | 8 | `You: sign out was a caption that signed you out` |
| 2026-07-31 | crisis | Android (+API consent fix) | **4** | 8 | `Crisis: "add one in Settings" pointed at a setting that did not exist` |
| 2026-07-31 | safety-plan | Android | **6** | 9 | `Safety plan: an empty screen that could not say why it was empty` |
| 2026-07-31 | patterns | Android + backend | **3** | 8 | `Patterns: the Hide button did not exist` |
| 2026-07-31 | privacy | Android | **5** | 8 | `Privacy: a failed read drew every consent switch off` |
| 2026-07-31 | insights | Backend | **3** | 8 | `Insights: a mood reading invented from no check-ins` |
| 2026-07-31 | goals | Android | **5** | 8 | `Goals: two taps that retired a goal with no way back` |
| 2026-07-31 | programs | Backend | **6** | 8 | `Programs: leaving a journey forfeited the week` |
| 2026-07-31 | toolkit | Android | **6** | 9 | `Toolkit: the two minutes it promised in five places` |

Follow-up from the Home pass, shipped with the journey path: `railKindFor` treated
00:09 as morning, so at 00:14 the theme had gone Night for wind-down while the rail
offered "For this morning · Body scan" — a 10-minute meditation, to someone still
awake past midnight. The rail now reads the same clock the theme does.


### splash — Android, 2026-07-30 (5 → 9)

Six findings, all measured on an OPPO CPH2681 at 23:00 by frame-stepping a cold
launch (`am start` + an on-device `screencap` loop, then sampling pixels — a
single `adb exec-out screencap` is too slow to catch a 1.1s screen).

1. **Status-bar icons rendered near-black over the Night splash.** Clock pixels
   measured luminance 6–56 against a sky of 49: the darkest ink was *darker*
   than the background. `SyncSystemBarIcons()` sat above `forceNight = true`, so
   it read the previous theme; any phone whose system theme is light got an
   unreadable status bar for the app's entire first second. After: 56–215.
2. **`@color/night` had drifted from the palette** — `#080B22` (the iOS value)
   against Compose's `#100D2B`, so the platform splash and the app floor were
   different colours. Now gated by a test.
3. **A hard brightness cut on handoff:** flat `#100D2B` → `#3A3372` in one
   frame, ~5× the luminance, at 22:54. The splash now opens the sky from the
   exact colour the platform handed over.
4. **A retired colour in the brand glow** — hardcoded `0x668A7BF0`, the
   periwinkle replaced on 2026-07-12. Raw hex in a screen, and the wrong hex.
5. **"Soft aurora ribbons" were hard-edged stripes** — the band resolved from
   backdrop to full colour in under 4px (Δ29 luminance in one pixel). Feathered
   in overlapping passes; steepest step now Δ8.7, a continuous falloff.
6. **Reduce Motion still waited the full 1100ms** on a frame that had already
   finished. Now 450ms — less motion, not more waiting.

Not fixed, deliberately: the launch shows two brand screens back to back (the
platform splash, then ours). Collapsing them into one via
`setOnExitAnimationListener` would drop the wordmark, so it is a product call,
not a defect. The remaining point is that cost.

### onboarding — Android, 2026-07-30 (3 → 8)

Capped at 3 by the rubric: four separate truthfulness findings. Walked all eight
steps on an OPPO CPH2681 (`pm clear` is blocked by the OEM — uninstall and
reinstall for a genuine first run).

**Not true:**

1. **"Private by design — nothing is ever shared."** The second sentence a new
   user ever reads, on Android, iOS *and* the browser client. A Talk message goes
   to OpenAI or Anthropic (`services/ai.py`); voice goes to Deepgram or
   ElevenLabs (`services/voice.py`). The app's own privacy screen was always
   careful — "Support tooling sees counts and account state, never the words" —
   and the funnel flattened it into an absolute the product cannot keep. Replaced
   with three things that are true and mechanised, added to `CLAIMS_MAP.md`, and
   the phrase is now banned by `check-claims`.
2. **The age gate showed a confirmation nobody had given.** A tick, "Confirmed: I
   am 18 or older", and "Thank you" — rendered on arrival, before any input. A
   compliance surface presenting a pre-made affirmation. It now states the
   requirement; the CTA is the confirmation.
3. **"Private previews" silently turned reminders off.** It sat in the
   single-select reminder-*time* group, nothing ever read the value, no preview
   setting exists, and `applyReminderChoice`'s `else` branch meant a user who
   asked for a discreet daily nudge got none and was told nothing. Removed, and
   the invariant is now `reminderHourFor` + a test rather than a comment.
4. **"You've had your first win"** was shown to everyone — including users who
   had just pressed "Skip for now". Now conditional on actually breathing.

**Also fixed:** the "Can't do" tile was a fixed `129.dp` and cut the word
*emergencies* in half — the most important limitation on the screen whose whole
job is stating limitations, truncated in the shortest locale we ship; and
`DisclosureTile.accent` was accepted and never applied, so the two tiles rendered
identically in white despite the call site passing Cyan and TextSoft.

**Behaviour:** the system back gesture fell through to the Activity and finished
it. A back swipe from any of the eight steps dropped the user on the launcher,
and relaunching restarted at Welcome with language, feeling and consent choices
gone — `rememberSaveable` cannot survive an activity that was destroyed rather
than recreated. Android's most-used navigation control was a trapdoor out of
onboarding. Now a `BackHandler` walks the funnel.

**Left deliberately:**

- **"Try a 2-minute reset" / "Two minutes of guided breathing."** The Reset preset
  has no timer — it cycles until tapped, and the same sentence then says "for a
  few cycles". The claim is imprecise rather than false, and it lives on iOS too
  with UI tests asserting the button label, so it needs one cross-client copy
  pass rather than an Android-only edit.
- Language's "Mix more than one if that's you" over single-select chips; the
  State rows' chevron (a navigation affordance on a selection control); the large
  dead space below short steps; "Continue with Google" as the visual primary
  while Google sign-in is unconfigured. All design/product calls, not defects.
- The daily reminder's own text is hardcoded English in `notify/Reminders.kt` — a
  Hindi user gets an English notification. Belongs to the `notify` module.

### home — Android, 2026-07-30 (5 → 8)

Audited signed in as the demo account at 23:43.

1. **The whole screen was Dawn at 23:43 — under a banner reading "The day is
   winding down."** REDESIGN §4.1 says "Sleep tab **and wind-down hours** always
   Night"; only the Sleep half was ever built. This is the third time the same
   harm has been found on hardware (the sleep player at 22:46 was the second).
   Now `nightFor(mode, systemDark, hour)` in AppTheme, with the resolution
   matrix as a test. It applies to **System mode only** — an explicit Dawn choice
   is a choice, and the clock does not overrule it. The 21:00 boundary is one
   shared constant with Home's banner so theme and copy cannot disagree about
   when evening is.
2. **The plan hero printed its own title twice.** The generator names a plan
   after its focus goal, so `title` and `focus` come back identical and the card
   rendered "Sleep before midnight" on two consecutive lines. `rationale` — the
   "why this, today" line — was being fetched and thrown away. Now shown.
3. **The wind-down banner truncated, and offered a door it does not open.** ~250px
   of text column next to two controls, so "The day is winding down — a quieter
   mix, or tonight's wind-down guide?" ellipsised away the entire offer. It also
   named the wind-down guide while the button calls `openMixer`. Copy, label and
   action now agree.
4. **"anxious · "** — Recent check-ins emitted the separator whether or not there
   was a note. The same line used `getString`, which throws on a null field
   inside a `runCatching`, so one null note would have made the section silently
   vanish instead of degrade.
5. **The 2026-07-04 "imagery honesty pass" never reached existing data.** It set
   `seed._IMG = ""` so clients render branded symbol wells instead of hotlinked
   stock photos — but seeding is additive by title, so only rows created after
   that date got it. Every earlier database still served Unsplash URLs: on device
   the sleep story "Rain over quiet hills" was illustrated with a sunlit desert
   canyon, fetched straight from a third-party CDN with the user's IP attached,
   on the Home screen of a privacy-first product. Backfilled, scoped to
   `images.unsplash.com` so admin-attached licensed art is never touched.

**Honest and left alone:** the presence week ring shows seven empty days, and the
backend agrees — the demo account's last check-in was three weeks ago. The client
is telling the truth; that is the state working.

**Left deliberately:**

- The "For tonight" rail holds one card and leaves half a row empty, because
  `/content?kind=sleep` genuinely has one item. Whether a one-item rail should
  render full-width or stop being a rail is a design call.
- Clients still load admin-attached art directly from whatever host the URL names
  (Coil, no proxy), so a third party can learn a user's IP and which piece of
  wellness content they were shown. Nothing seeded points off-domain any more,
  but the capability is unchanged. Proxying media through the backend is an
  architecture decision, not a Home fix.
- Plan screen shows the plan name twice too — once as the screen title, once as
  the hero title under "WHY THIS PLAN". Found from here; belongs to `plan`.
- "Recent check-ins" carries no dates, so three-week-old entries read as recent.

**Note:** two accidental taps during this pass toggled a plan step on the demo
account. Both were reverted through the API (`PATCH /plans/steps/{id}`), and the
plan is back at 1 of 3.

### sleep — Android, 2026-07-31 (6 → 8)

Audited signed in at 00:18. No fabrications, so no cap; the cost was layout,
typography and duplication.

1. **`TimeRow` broke a word in half.** A bare Row of five children with no
   weights: at 720px the longer "Woke up around" pushed the last control off the
   end and Compose wrapped "+30m" onto two lines, one glyph on the second. Label
   now takes the slack against a fixed stepper, so the row cannot overflow and
   the two rows line up with each other.
2. **Ten paragraphs across the app were set in the small-caps eyebrow style.**
   `labelSmall` is Bold 11sp with 1.6sp tracking and no lineHeight — built for
   "WIND DOWN" and "TONIGHT", two to four words above a heading. It had drifted
   onto the Health Connect **consent boundary** disclosure, TIPP's *"if the urge
   to hurt yourself is present"* line, three privacy-policy bodies and the
   programs evidence claim: precisely the credibility and safety paragraphs that
   most need reading. Moved to `bodySmall`, and `labelSmall` now carries a
   comment saying what it is for.
3. **The wind-down guide shipped every idea twice.** Four served `wind_down`
   guides, then two hardcoded cards repeating two of them in different words — a
   user met "Bed is for sleep" twice within one screen — with the same CBT-I
   citation printed under each. The hardcoded pair was nonetheless the only copy
   that survives with no network, so it became `ContentList(fallback = …)`
   instead of being deleted: gone when the catalogue answers, there when it does
   not. One citation now covers the section.

**Honest and left alone:** "This week" says "Log a few more nights and honest
averages appear here — no guesses" because the demo account's last night was
2026-07-04. Correct empty state. "Save night" stays disabled until a rating is
picked.

**Left deliberately:** "Pre-fill from Health Connect" is Cyan while the Sleep
accent is Violet — but Cyan is the de facto secondary accent at ten call sites
across the app, so changing it here is a design-system decision, not a Sleep fix.

**Two process notes, both mine:**

- The `labelSmall → bodySmall` sweep was mechanical (any `labelSmall` within
  three lines of a >70-char string) and it took `privacypolicy_built_header`
  — "HOW CEREBRO IS BUILT", a genuine eyebrow — by proximity. Caught by reading
  the diff, reverted. Read the diff of a mechanical sweep.
- The first version of the fallback test was a Robolectric Compose class that
  polluted `Session` for every class after it in the JVM, and two of its three
  cases passed for the wrong reason: `ensureAccess()` was throwing 401 before any
  request, so "the fallback shows" was true because everything failed. Replaced
  with a pure `contentListState()` and a plain unit test. A test that cannot
  fail for the right reason is worse than no test.

### talk — Android, 2026-07-31 (6 → 8)

Audited signed in at 01:00 against a real seeded conversation. Nothing
fabricated: the assistant's "I've logged your mood as anxious" is backed by an
actual `anxious` row from the same minute, so the agentic write really happened.

1. **The composer travelled with the transcript.** The whole screen was one
   `verticalScroll`, message field and Send included, so after any real
   conversation you had to scroll to the bottom to type. Worse, the
   auto-scroll-on-new-reply targeted `maxValue` — the bottom of the *page*, which
   was the composer — so the scroll meant to reveal the reply scrolled past it.
   `Page` gained an optional `footer` rendered outside the scroll region.
2. **The free-tier cap card rendered below the Send button**, though its own
   comment said "above the composer". The one explanation of why a message was
   refused was the last thing on a scrolling page. It is now pinned above the
   field, where it is unmissable at exactly the moment it matters.
3. **Send was a full-width pill stacked under the field.** Pinned above the
   keyboard that is a lot of height for one word; it is now a 52dp circular
   control beside the field.
4. **"Save this conversation to my journal" read as a heading** — bare periwinkle
   text, the same colour and weight as the "Try together" and "Type instead"
   labels above and below it. The only way to keep a conversation looked like a
   caption. Now an outlined row with a bookmark icon.

**Fixed a regression I introduced in the same pass:** the pinned footer carried
`imePadding()`, but the window already resizes for the keyboard, so the inset was
counted twice — the composer flew to the top of the screen with an empty half
screen beneath it. Caught by typing into it on the device, not by the build.

**Left deliberately — app-wide, needs its own pass:** `Page` puts its 28dp
vertical padding *inside* the scroll region, so on any screen with enough content
the scrolled text passes under the transparent status bar. Talk shows it plainly
(a message bubble behind the clock) because its transcript is long enough to
scroll; most screens are too short to reveal it. The fix is a status-bar inset on
`Page`, which shifts every screen's header and needs the whole app re-verified —
not something to slip into a Talk audit.

### journal — Android, 2026-07-31 (3 → 8)

Capped at 3 by one truthfulness finding, on the screen least able to afford it.

1. **"Safety scanning … never blocks or shares your writing."** On the Private
   mode / *Your privacy* screen — the one screen whose whole job is explaining
   that scan. The "never blocks" half is true and is a hard rule. The "never
   shares" half is not: `scan_and_record` → `safety.classify` →
   `ai.complete_json` sends the entry body to OpenAI or Anthropic. Same falsehood
   the Welcome screen carried, in a second place, found the same way. Rewritten to
   say what happens, added to `CLAIMS_MAP.md`, and the phrase family is now
   blocked by `check-claims`.
2. **You could write journal entries and never read one back.** History rows were
   not tappable, so the only view of your own writing was a 120-character,
   two-line preview. iOS has had `JournalDetailView` from the start; Android was
   missing the second half of its own feature. Tapping a row now opens the entry
   in full — verified against a 600-character entry that rendered untruncated.
3. **The mood chips were `chunked(3)` + `Row`** — a fixed three-per-row grid with
   no way to give. Now a `FlowRow`. Stated honestly: this one is hardened by
   construction, not from a screenshot, because this device would not let me
   reproduce it (below).

**Honest and left alone:** search appears only above three entries, which is the
right call and still matches "searchable" on the door; the history card's third
line really is the entry body ("Written"), not a stray label; Tele-MANAS 14416 is
correct (free, 24/7, Government of India).

**Test data:** a long entry was created through the API to verify the reader and
deleted afterwards (`DELETE /journal/{id}` → 204, one entry left, as before).

### you — Android, 2026-07-31 (6 → 8)

Audited signed in at 02:13. Nothing fabricated; the cost was that the two
account-destroying controls were the two that looked least like controls.

1. **Sign out was a caption that signed you out.** A bare `TextButton` in
   `TextMuted` — no border, no icon, no container — on a screen where every other
   action is a bordered card, and it signed you out on the first tap.
   `Session.signOut()` clears the local store, so a mis-tap took the cached reads
   and any unsaved draft with it. Now a proper row with a subtitle, and a dialog
   that says exactly what is and is not lost ("Your account and everything in it
   stays safe. This device forgets its copy — including anything you have typed
   but not saved").
2. **"Delete account · Permanently erase everything" was pixel-identical to
   "Privacy policy · How we handle your data"** — same card, same periwinkle
   icon, same chevron. The most irreversible action in the app looked exactly
   like a link to a document. `NavRow` gained a `tint`; the row is now coral.
   Its destination was already safe (two-step confirm, `DangerButton`) — this is
   about the doorway, not the room.
3. **Two different chevrons on one screen.** The Support card used a literal "›"
   glyph at a different size and colour from the `AutoMirrored` icon every NavRow
   uses — and a literal glyph does not mirror in RTL, which this app needs (the
   DPDP notice renders Urdu).

**Honest and left alone:** "Companion style" is the only cyan row, but that is
`emphasis = true` and deliberate. The Support card's subtitle wraps "24/7" onto a
second line — cosmetic, and shortening a helpline's availability line to make it
fit is the wrong trade.

### crisis — Android, 2026-07-31 (4 → 8)

Audited at 05:11. The directory itself is in good shape — Tele-MANAS 14416,
emergency 112, KIRAN 1800-599-0019 and findahelpline.com are all correct, all
dial through `ACTION_DIAL` (never auto-call), and the removed WhatsApp row stays
removed. The cost was everything around the one editable thing on the screen.

1. **"Not set — add one in Settings" pointed at a setting that did not exist.**
   The backend has had full CRUD for a trusted contact since the beginning, iOS
   has an editor, the browser client has an editor, and the Android API layer
   already had `setTrustedContact` — with no caller anywhere. So the crisis
   screen instructed the user to go somewhere that was not there. Built
   `TrustedContactScreen`, reachable from the crisis card (now a door, not an
   inert notice) and from You.
2. **`setTrustedContact` hardcoded `notify_consent = true`.** Naming someone
   would have silently agreed to messaging them at the worst moment of your life.
   It is now the user's switch, defaulting **off**, and the backend only
   escalates when it is true (`services/escalation.py::on_crisis`). The existing
   endpoint test asserted `assertTrue(notify_consent)` — it had pinned the bug in
   place — and now asserts both answers reach the server unchanged.

Verified end to end on the device: saved a contact with the switch off and
confirmed the server stored `notify_consent: false`; reloaded and confirmed it
reads back on both the crisis card and the editor; removed it and confirmed the
server returned to `null`. No test data left behind.

**Left deliberately:** `openSupportTarget` wraps `startActivity` in `runCatching`
and swallows the failure. Not crashing a support surface is right; doing nothing
visible is not — on a device with no dialer, tapping "Tele-MANAS 14416" is a dead
tap on the one screen where a dead tap matters most. The fix (copy the number to
the clipboard and say so) needs a device that can actually reproduce it, and this
one has a dialer.

### safety-plan — Android, 2026-07-31 (6 → 9)

The one claim on the door — **"works offline"** — is true, and now verified on
hardware rather than assumed. With `adb reverse` removed, the plan renders from
the encrypted GET cache with an honest "saved on this device" banner. The two
findings were both about what happens when it *cannot*.

1. **A failed load was indistinguishable from an empty plan.** `onFailure` set
   `values = emptyMap()` and said nothing — `error` was declared but that path
   never set it. So a user who opened the screen offline before it had ever
   cached was shown seven blank boxes where their safety plan should be, with no
   explanation. The only available conclusion is that it is gone. There is now an
   explicit card — *"Nothing has been deleted — your plan is still on your
   account"* — and a Try again.
2. **The "showing the copy saved on this device" banner read a GLOBAL flag.**
   `Session.servedStale` is about the last GET anywhere in the app, so with Home
   having served stale the safety plan announced it was showing a saved copy
   while displaying seven empty boxes — two contradictory banners at once, on
   this screen of all screens. Found only because fixing (1) put them side by
   side. The banner now reflects this screen's own read.
3. **Save failures rendered at the top of a seven-section page while successes
   rendered beside the button.** Edit the sixth section, fail to save, and the
   only notice is off-screen above. Errors are keyed by field now.

Verified on device across all three network states: offline with a warm cache
(plan + banner), offline with no cache (explicit failure card, no banner),
and Try again with the network restored (plan returns, no banner).

**Left alone:** the plan is saved per section rather than all at once. That reads
oddly next to a single Save, but it means a half-filled plan is never lost to one
failed write — the right trade on this screen.

### patterns — Android + backend, 2026-07-31 (3 → 8)

The screen the fabricated Pattern Dashboard was found on. That fix has held: the
empty state reads "Patterns only appear once a few weeks of real check-ins
support them — no guesses, ever", the statements are computed per request and
never stored, and every one carries its own basis ("7 of your 7 difficult
check-ins landed there"). Capped at 3 anyway, because the section's own promise
— **"Hide one and it stops being shown or used"** — was false twice over.

1. **The Hide control did not exist.** `MemoryRow` accepted an `onHide` lambda,
   the caller passed a working one wired to `POST /users/me/memory/
   suppress-pattern`, the string `patterns_hide` was already translated in both
   locales, the KDoc said "it can be hidden, which is the honest equivalent" —
   and the composable body rendered nothing that could ever call it. A capability
   the backend supported, the client had wired, the copy advertised, and the user
   could not reach. It stayed invisible because the row only renders when
   patterns exist, and patterns need weeks of check-ins.
2. **Hiding did not retract what the pattern had already justified.**
   `compute_patterns` honours the tombstone, so no NEW suggestion is seeded from
   a hidden pattern — but one seeded earlier keeps `reason` set to the statement
   verbatim, and the dashboard renders it as "Because: …". Seen on device: the
   pattern vanished from the top of the screen and went on justifying a live
   suggestion halfway down it. Suppressing now dismisses **pending**
   recommendations with that reason. Only pending: a practice the user accepted
   is theirs, and withdrawing it because they tidied away the observation behind
   it would be taking something they chose.

Hide is two-tap, because it does not come back — the tombstone's source is not in
`EDITABLE_SOURCES`, so the server refuses to delete it and the only undo really
is clearing all memory, exactly as the copy says.

**Method note:** the demo account has no patterns, so none of this was reachable
on it. Verified instead on three throwaway accounts seeded through the API until
a real pattern fired, then deleted (`DELETE /users/me` → 204 each). The demo
account ends the pass exactly as it started: 0 patterns, 1 hidden.

### privacy — Android, 2026-07-31 (5 → 8)

The consent surface. Much of it is careful already: the toggle write is
optimistic but reconciled — verified on device that flipping one really reaches
the server, and the code reverts and says so if the write fails — and "Anonymous
usage stats · counts only, never your content or account" matches what
`net/Analytics` actually sends.

1. **A failed consent read drew every switch OFF, silently.** The load was a bare
   `runCatching` with no failure branch: if the GET threw and no cached copy
   existed, the map stayed empty, all six switches rendered off, and the screen
   said nothing. On a consent surface that is not a blank state — it is a false
   statement about what the user has agreed to, and the obvious reaction
   (re-toggling) writes consents they already had. There is now an explicit card
   — *"The switches below are not showing what you have agreed to… Nothing has
   changed. Try again before you touch them"* — and a retry. Verified on device
   by signing out to clear the cache and opening the screen with no network; the
   user's stored consent was untouched throughout, then confirmed byte-for-byte.
2. **Thirteen unlabelled language chips filled the first screen.** Nothing said
   what they changed, so they read as the app's language rather than the notice's
   — on the screen where DPDP s.5(3) makes that distinction the point. Labelled.

**Left deliberately — needs a translator, not a coder:** two of the six category
hints describe the DEFAULT rather than the category ("Voice storage · Off by
default", "Model training · Separate opt-in only"). They are true but tell the
user nothing about what the data is for, and on a settings screen they are
stale the moment someone turns one on. The fix is 26 strings inside a
hand-shipped 13-language DPDP notice — legally-operative text — and rewriting it
in twelve languages I cannot check is a worse risk than a weak hint.

**Also noted, not changed:** the notice caption ends "Change any of this later in
Settings" while being read *in* Settings. Odd, not false, and it lives in the
same 13-language notice.

### insights — backend, 2026-07-31 (3 → 8)

The Android screen renders the server payload faithfully — nothing invented
client-side, and "First-party — computed on your own data, never sold or shared"
is true and now in `CLAIMS_MAP.md`: `services/insights.py` imports no AI module
at all, the weekly read is pure SQL over the user's own rows. The fabrication was
one line deeper.

1. **A mood reading invented from no check-ins.** `stability` defaulted to `0.7`
   when `avg_intensity` was None — above the "Steady" threshold — so a user who
   had logged nothing all week was told **"Mood stability: Steady"** under a 70%
   bar. Worse, `use_moods` gates the query, so the same thing was shown to a user
   who had explicitly switched mood history **off**: a conclusion presented back
   from data they had withheld. Sleep already models this correctly ("No diary
   yet"); mood stability now reads "No check-ins yet" with an empty bar.

   This is the rule the Pattern Dashboard states out loud — *"patterns only
   appear once real check-ins support them, no guesses, ever"* — broken on the
   screen next door.

2. **Two existing tests had pinned it.** `test_weekly_insights_respect_itemized_
   consent` asserted `Mood stability == "Steady"` with the comment *"neutral
   default, not derived"* — in a test whose name is about respecting itemized
   consent. Now asserts the withheld category reads as withheld, bar included.

3. **"A few calm sessions this week" was said for exactly one.** One session now
   gets its own headline.

Also fixed a straggler from the Sleep pass: the Insights privacy footer was still
in the small-caps eyebrow style — it is 62 characters, and that sweep only
caught strings over 70.

### goals — Android, 2026-07-31 (5 → 8)

Nothing fabricated. The screen keeps its own promise well — "no streak to break",
a seven-day window rather than a chain, and "letting a goal go is an outcome, not
a failure" is carried through to a real `released` status rather than a delete.
The cost was that two of its three buttons were one-way doors.

1. **"Done" and "Let it go" retired a goal with no confirmation and no way
   back.** Both sit one tap from "Make today's plan" in the same wrapped row, and
   both made a user-authored goal vanish from the app — while the server kept it
   and had always accepted `?include_resolved=true`, which the client never
   asked for. The same shape as the Journal reader and the trusted contact: the
   backend had the capability, the client used half of it.

   Fixed with **undo rather than a confirm dialog**. Retiring a goal is usually
   deliberate, so the right move is to make a mis-tap cheap, not to interrogate
   everyone who means it. There is now a "Finished and let go" section — *"Nothing
   here is lost"* — with a Bring it back on each entry.

   The demo account proved the point immediately: it had an `achieved` goal from
   a previous session that had been invisible in the app the whole time, and it
   appeared the moment the section existed.

2. **No boundary between goal entries.** `goals.forEach` drew them straight into
   one card, which is unreadable when two share a title — and this account has
   two called "Sleep before midnight". A hairline now separates them.

**Test-data disclosure:** exercising the flow changed two goal statuses on the
demo account. `54b95ca7` (released by me) was restored to `active`. A mis-tap on
a "Bring it back" also moved `e484cbe9` from a resolved state to `active`; the
pre-audit read proves it was not active before, but not which resolved state it
held, so it was left `active` and flagged rather than guessed at.

### programs — backend, 2026-07-31 (6 → 8)

Nothing fabricated. The day count derives from the start date with nothing to
advance or fail; the evidence line is properly scoped ("evidence-informed — built
on CBT and sleep-science techniques", not "clinically proven"); and the journey
path added earlier this session renders correctly in Dawn as well as Night.

**"Leave this journey" forfeited the week.** One unconfirmed tap inside the hero
card. Leaving only flips `active` — the original `started_at` survives in the
table — but enrolling always minted a fresh enrollment, so tapping away from a
wind-down program on a bad evening silently reset day 5 of 7 to day 1, with the
real start date sitting there unused.

Fixed as Goals was, by making the mistake cheap rather than interrogating the
intent: re-enrolling now RESUMES the prior enrollment while its window is still
running. Only while running — rejoining something abandoned months ago must start
over, not drop the user at "day 92" clamped to the last day and instantly
complete, which would be its own lie.

Verified on the demo account: day 2 of 7 → leave → rejoin → day 2 of 7.

### toolkit — Android, 2026-07-31 (6 → 9)

A strong hub. The 5-4-3-2-1 grounding widget completes correctly through all five
senses on device (see → feel → hear → smell → taste, then Start over / Back);
"Pop gently — no timer, no losing" is the product's voice and is true; and the
crisis door is present here as it is everywhere.

**The two minutes it promised in five places.** "Two-minute reset" (toolkit and
the breathe screen), "Try a 2-minute reset" (onboarding), "Fast anxiety-stress
reset — 2 minutes" (Talk), "Two minutes of guided breathing" (onboarding) — and
nothing measured or marked two minutes. The Reset preset is an open-ended in/out
cycle that runs until you tap away. I had already found this during the
onboarding pass and left it, because the copy also lives on iOS where UI tests
assert the button label; that was the wrong call twice over — a claim on five
surfaces is not a copy nit, and deleting it would throw away information a user
genuinely wants when deciding whether they have time.

So the claim is now **true** instead of removed: `twoMinutesReached` derives
elapsed seconds from completed cycles at the user's chosen pace (Classic 4s,
Gentle 6s, Slow 8s) and marks the moment once. Deliberately not a timer and not a
stop — this product tells people there is "no streak to break", and putting a
clock on a calming exercise would be the same mistake in miniature. The line
reads *"That's two minutes. Stop here, or keep going — the rhythm holds either
way."*

Verified in real time on hardware: the mark appeared at 18 calm breaths on the
Classic pace, and the breathing carried on. Every iOS string is untouched, so no
iOS test moves. Now in `CLAIMS_MAP.md`.

## Gotchas this device adds

The OEM (OPPO ColorOS) blocks more than `pm grant`:

- `pm clear` — `SecurityException: CLEAR_APP_USER_DATA`. Uninstall + reinstall
  for a genuine first run.
- `settings put system font_scale` — `WRITE_SETTINGS`. Large-font states cannot
  be reproduced here.
- `wm density` — `WRITE_SECURE_SETTINGS`. Neither can narrow-width states.

So layout robustness against big text has to be argued from construction
(FlowRow over fixed grids) and said to be argued that way, not claimed as seen.

## Device commands

```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
adb reverse tcp:8000 tcp:8000                       # phone → host API
cd apps/android && ./gradlew installDebug -PapiBaseUrl=http://localhost:8000
adb exec-out screencap -p > shot.png                # look at it
adb shell uiautomator dump /sdcard/ui.xml           # exact tap coordinates
adb logcat -d -s CereBroApi:D                       # what it actually called
```

Gotchas already paid for: `adb shell input keyevent 4` closes the *screen*, not
just the keyboard — tap the target directly while the keyboard is up. `input
swipe` does not drive Compose `PullToRefresh`. The OEM blocks `pm grant`.
