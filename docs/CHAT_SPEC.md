# Chat / Coach screen — the 100-point spec

Status: **proposed, 2026-07-21.** Written against the screen as it exists *on hardware*
(physical OnePlus CPH2681 / Android 14, debug build against local platform+engine), not
against the source — half of what is below was invisible until the app was in a hand.

Scope: `apps/android/.../ui/screens/CoachScreen.kt` (the Coach tab) and the same
conversation surface in `apps/app` where noted. The sibling specs are
[SPLASH_SPEC.md](SPLASH_SPEC.md) and [HOME_SPEC.md](HOME_SPEC.md); this one follows their
shape — numbered, individually verifiable, each point either shippable or explicitly cut.

Markers: ✅ already true · 🟡 partly there · ❌ not built · 🔴 **defect found on device**.

> **Two device findings opened this spec, and both are in §1.** A crisis takeover rendered
> as the literal string "…" (fixed 2026-07-21, #1), and the app calls itself an "AI
> companion" in its own compliance disclosure (#4) — the exact word the statutes attach to,
> in the one sentence written to satisfy them.

---

## §1 — Safety and honesty (nothing below this line ships without these)

1. 🔴→✅ **A scripted reply must render.** A crisis takeover emits **no token frames** (no
   model in that path), so the whole message arrives in the `done` frame under
   `response_to_user`. The client read `reply`/`text` — keys the engine has never sent — so
   every crisis takeover showed "…". Fixed + unit-pinned (`CoachReplyFallbackTest`).
   *The general rule: any client fallback on the safety path needs a test that fails when
   the contract moves.*
2. ✅ **Crisis detection is server-side and unbypassable** — the client cannot suppress it.
3. ✅ **The crisis reply carries the helpline and an AI disclosure**, in the detected
   language (engine `crisis._AI_DISCLOSURE`, 21 languages).
4. 🔴 **Stop calling it an "AI companion."** 17 English strings do (`talk_disclosure_pill`,
   `talk_eyebrow`, `ob_disclosure_sub`, `humansupport_intro`, `tour_stop3_body`, the whole
   `companion_*` feature). CA SB243 / NY GBL art. 47 attach to *companion* systems; the
   engine ships a guardrail forbidding companion framing and `/v1/governance` attests
   "non-companion by design". Our own disclosure pill contradicts the attestation.
   **Self-description must say "AI coach"**; the *feature* name ("Companion style") is a
   separate product decision.
5. ❌ **The disclosure pill must be reachable, not decorative.** Today it sits above the
   composer and scrolls with nothing; it should be persistently visible while a
   conversation is open, and its "Details" sheet should name the model tier, what is
   stored, and what is not.
6. ❌ **Re-disclose on a cadence, not on every turn** — first message of a session, and
   again after a long unbroken run (engine `pacing.py` already computes the crossing;
   surface it as a quiet inline system line, never a modal).
7. ❌ **Render the engine's support-route turn as a distinct block** (not a normal bubble)
   when `pacing` fires: a bordered card with the helpline row and "talk to a person"
   affordances, so it cannot be mistaken for coaching prose.
8. ❌ **SOS is two taps from the composer** — a small always-visible affordance in the
   input row, not buried in You → Human support.
9. ❌ **A crisis turn should offer the region's helplines as tappable rows**
   (`engine /v1/safety/helplines` already serves them per region) instead of a URL inside
   prose.
10. ❌ **Never auto-speak a crisis reply aloud** in a shared space without a beat — offer
    "read this aloud" instead of TTS firing on the most private sentence in the product.
11. ✅ **No transcript leaves the device to any employer surface** (rule 5) — keep it that
    way when adding any "share" affordance below.
12. ❌ **"Why am I seeing this"** on any inline suggestion card — one line explaining the
    intent match that produced it.

## §2 — The message list

13. ✅ Distinct bubble geometry per side (asymmetric corner radii).
14. 🟡 **Max width 86%** — correct for the phone; add a **max text measure (~68ch)** so a
    tablet or unfolded foldable does not produce 120-character lines.
15. ❌ **Selectable text.** Today a reply cannot be copied. `SelectionContainer` around the
    coach bubble, with copy on long-press.
16. ❌ **Per-message overflow menu**: copy, "save to journal", "report this reply" (#133 in
    the backlog), "regenerate" (#78).
17. ❌ **Timestamps on demand** — hidden by default, revealed on tap or beside a day
    divider. A coaching transcript with a clock on every line reads like a chat app.
18. ❌ **Day dividers** when a session resumes across a boundary ("Yesterday", "Tuesday").
19. ❌ **Session boundary markers.** A resumed session currently looks like one unbroken
    conversation; mark where a session closed and a new one began.
20. 🟡 **Grounded line** exists for retrieved material — extend it to name *what* it drew
    on when the KB can attribute it (backlog #90).
21. ❌ **Streaming cursor** — a soft caret at the end of the growing text, removed on
    completion. Today text appears with no signal that more is coming.
22. ✅ Pre-first-token typing dots, with a Reduce Motion static fallback.
23. ❌ **Stop generating.** A long turn cannot be interrupted. The button belongs where
    Send is, swapping in while `busy`.
24. ❌ **Scroll-anchor discipline**: auto-scroll only when already at the bottom. Today
    every new message yanks the view down even if the user scrolled up to re-read.
25. ❌ **"Jump to latest" pill** when scrolled away, with an unread count.
26. ❌ **Preserve scroll position and draft across process death** (the transcript is
    restored; the position and the half-typed message are not).
27. ❌ **Lazy history paging** — restore currently loads the whole session; page it and
    show a "load earlier" affordance.
28. ❌ **Empty-state variants**: first-ever session vs a returning user with no open
    thread. Today both get "What's in front of you?".
29. ❌ **Conversation starters** as tappable chips in the empty state — skills/goal
    oriented, never intimacy-oriented (backlog #73).
30. ✅ Inline action cards mirror a saved commitment into the conversation.
31. ❌ **Card states**: a saved commitment card should show its own state (open/done) and
    update in place when checked off elsewhere.
32. ❌ **Failed-message affordance.** A send that errors leaves prose in the bubble; it
    should be a retryable state attached to *the user's* message, with the text recoverable.

## §3 — The composer

33. ✅ Multiline field with placeholder, mic, and Send.
34. 🟡 **Send stays enabled-looking while empty** — disable state should be visually
    unambiguous (device: the button reads identical when it cannot act).
35. ❌ **Draft persistence** per session, restored on return.
36. ❌ **Character/length guidance** only when approaching a real limit — never a counter
    that watches you type.
37. ❌ **Enter-to-send is a setting**, default off on phone (newline), on for hardware
    keyboards; `imeAction` set accordingly.
38. ❌ **Keyboard shortcuts on tablets/desktop**: ⌘/Ctrl+Enter to send, Esc to stop
    (backlog #166 for `apps/app`).
39. ✅ `imePadding()` — the composer rides the keyboard.
40. ❌ **Composer must not cover the last message** on small screens when the keyboard is
    up (device: the last bubble sat under the input at 720×1600).
41. ❌ **Attachments are explicitly out of scope** — say so in the spec so nobody adds a
    paperclip; a coaching product that accepts screenshots inherits an OCR/PII problem.
42. 🟡 **Mic**: dictation works; add a **live waveform/level** so it is obvious the mic is
    hot, and a visible stop.
43. ❌ **Mic permission denial** needs an honest state ("dictation needs the microphone;
    everything else still works"), not a silent no-op.
44. ❌ **Voice replies (`Replies aloud`)** should show *which* message is speaking and
    allow stopping mid-utterance.
45. ✅ Plus-gated voice shows a "Plus" chip rather than failing silently.
46. ❌ **Interrupt-on-send**: sending a new message stops any in-flight TTS.

## §4 — Latency, streaming and failure

47. ❌ **Time-to-first-token budget: 1.5s p95.** Instrument it client-side; today only the
    server measures.
48. 🟡 **Status line** exists (`onStatus`) — give it a stable position and honest copy
    ("thinking", "looking something up"), never fake progress.
49. ❌ **Reconnect a dropped SSE stream** mid-turn and resume rather than erroring the
    whole turn.
50. ❌ **Offline state**: the composer should say the coach is unreachable *before* a send
    fails, using the same served-stale pattern Home uses.
51. ✅ An unreachable coach produces an honest sentence, not a raw error.
52. ❌ **Distinguish "unreachable" from "rate-limited" from "free-tier cap"** — the free cap
    (5 turns/day) currently reads as a generic failure; it should be a paywall moment.
53. ❌ **Retry with backoff** on transient failures, once, silently, before showing an error.
54. ❌ **Slow-turn reassurance** at ~8s ("still working — long answers take a moment").
55. ❌ **Token-budget guard** surfaced honestly when a turn is truncated (backlog #80).
56. ❌ **Never lose the user's text.** On any failure the message must remain editable and
    resendable.

## §5 — Accessibility (the section that decides whether this is world-class)

57. ❌ **TalkBack pass on every element.** Bubbles need a role and a speaker prefix ("You
    said", "Coach said"); today they are unlabelled text.
58. ❌ **Live region** on the streaming reply so TalkBack announces the arrival, once, not
    per token.
59. ❌ **Focus order** must move to the new reply, not back to the composer.
60. ❌ **Font scaling to 200%** — bubbles, chips and the disclosure pill are the risk spots.
61. ❌ **The disclosure pill truncates at large font sizes** (device: it already wraps to
    two lines at default). It must never clip a legal sentence.
62. ✅ Contrast is gated on real tokens (`ContrastTest`).
63. ❌ **Touch targets ≥48dp** — the mic and "Details" are close to the floor.
64. ❌ **Reduce Motion** must also quiet the streaming caret and the scroll animation, not
    only the typing dots.
65. ❌ **RTL mirroring** — bubble corners, alignment, and the mic/Send order.
66. ❌ **Keyboard-only navigation** through the transcript (tablet + `apps/app`).
67. ❌ **Copy that reads aloud well**: no "—" mid-sentence where TTS will pause oddly, no
    emoji as meaning.

## §6 — Internationalisation

68. 🔴 **210 missing Hindi strings** (`values` 770 vs `values-hi` 560) — a Hindi device
    silently falls back to English mid-screen. Add a build check that fails on a key
    present in `values` and absent in a shipped locale.
69. ❌ **The coach's language and the UI's language are separate settings** and must be
    visibly so.
70. ❌ **Per-message language tagging** so TTS picks the right voice on a code-switched turn.
71. ❌ **No string concatenation for sentences** (the "open commitment(s)" pluralisation in
    the memory chip is built by string addition — use plurals resources).
72. ❌ **Date/number formatting through the locale**, not hardcoded.

## §7 — Trust, memory and control

73. ✅ The memory chip states what the coach retains and links to Actions.
74. ❌ **"What does the coach remember?"** — a sheet listing the actual carried state
    (commitments, session goal, program), each individually forgettable.
75. ❌ **Delete a single message** and have it leave the engine's memory too.
76. ❌ **Export this conversation** as markdown (backlog #87), the user's own content only.
77. ❌ **Incognito turn** — a message explicitly not retained, marked in the transcript.
78. ❌ **Show the plan state in-conversation** when a turn is capped, not as an error.
79. ❌ **Session close is a moment**: the commit gate should end with a visible summary
    card ("here's the one step you took"), not a message that scrolls away.
80. ❌ **"This was a coaching session, not a friendship"** framing in the close card
    (backlog #70) — one line, once, at the end.

## §8 — Layout, motion and craft

81. ❌ **Landscape** — currently portrait-locked in the manifest. Either support it
    properly or state the lock as a product decision in this spec.
82. ❌ **≥600dp**: centre the column with generous gutters rather than stretching bubbles.
83. ❌ **Foldable hinge awareness** — do not render a bubble across the fold.
84. ❌ **Bubble entry motion**: a 120ms rise+fade, honoured by Reduce Motion.
85. ✅ Hour-tinted background continuity with Home/splash.
86. ❌ **Scroll performance**: the list re-composes whole bubbles per token today
    (`messages[i] = copy(...)`); key on stable ids and hold the text in a
    `mutableStateOf` per message.
87. ❌ **No layout jump** when the typing dots are replaced by text.
88. ❌ **Long-message collapse** with "show more" beyond ~40 lines.
89. ❌ **Code/markdown**: decide explicitly. Coaching replies should render **plain text**
    — no markdown parser, no link auto-detection beyond the helpline.
90. ❌ **Haptics**: one soft tick on reply-complete, off under Reduce Motion.

## §9 — Instrumentation (kind-only, never content)

91. ❌ `chat_turn_sent` / `chat_turn_completed` with latency buckets — no text.
92. ❌ `chat_turn_failed{reason}` — unreachable / rate_limited / capped.
93. ❌ `chat_stream_stalled` when TTFT exceeds budget.
94. ❌ `chat_scripted_reply_rendered{kind}` — the counter that would have caught defect #1
    on day one.
95. ❌ Surface `cerebrozen_boundary_prompted_total` and `cerebrozen_session_pacing_total`
    (engine-side, already emitted) on the same dashboard as the above.

## §10 — Tests that must exist before this is called done

96. ❌ A Robolectric golden of the transcript at default and 200% font scale.
97. ✅ `CoachReplyFallbackTest` — the non-streaming/safety path (added with defect #1).
98. ❌ A test that a `safety_flag: crisis` turn renders the reply **and** that no inline
    suggestion card is offered on that turn.
99. ❌ A test that the disclosure string does not contain the word "companion" — the
    mechanical half of §1.4, so it cannot regress once fixed.
100. ❌ A device checklist appended to [ANDROID_QA.md](ANDROID_QA.md) §2 for the items no
     JVM test can reach: TalkBack order, TTS interrupt, keyboard overlap, RTL.

---

## Sequencing

**Ship first (safety + honesty):** 4, 5, 7, 8, 99 — the naming defect and the crisis
surfacing. **Then correctness:** 15, 23, 24, 32, 49, 52, 56 — the ones a user hits weekly.
**Then craft:** §8 and the a11y block, which is where "good" becomes "world-class" and
which no amount of visual polish substitutes for.

Nothing here needs a key, a store account, or legal sign-off; §1.4 needs a product
decision on the *feature* name only.
