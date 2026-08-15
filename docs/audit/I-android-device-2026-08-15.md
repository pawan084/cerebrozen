# Audit I — Android, on hardware (2026-08-15)

Screenshot review of the running app on the OnePlus CPH2681 (720×1604, Android 14,
ColorOS), debug build against a local backend over `adb reverse`, walked in **guest
mode**. Every point below was seen on a device, not inferred from code — where a
cause is named, the code was then read to confirm it.

> **Fix pass, same day.** 22 of 26 closed, each re-verified on the handset
> (screenshots `fix-0*.png`). Still open: **#4** (blocked — TalkBack needs
> `WRITE_SECURE_SETTINGS`, which ColorOS refuses over adb; needs the owner to
> switch it on by hand), **#24** (found to be *documented intent* — the chip is
> cut by the screen edge, not the card, as the deliberate "there's more this
> way" affordance; owner may still overrule), **#25** (emoji scale — a design
> call needing real glyph work, not a patch), and **#20's** contract question is
> resolved in code (Tele-MANAS leads; the dialable immediate-danger banner keeps
> 112 first for the emergency case) but remains reversible if the owner rules
> the other way. Fix-pass notes worth keeping: the Today hero's `HeroKind` enum
> (PLAN_STEP/PLAN_DONE/FALLBACK) was computed and **never rendered** — every
> state showed one hardcoded mock, while all the honest strings and helpers
> (`heroWhyRes`, `today_hero_why_fallback`, `planStepRoute`) already existed
> unused; #9's "empty heading" was really the scroll fold landing between a
> label and its chips (fixed by making them one unsplittable line); and #18's
> "Call" pill already used ACTION_DIAL (mis-tap opens the dialler, never places
> a call) — the real defect was a ~33px target on the most consequential control
> on the screen.

Format: `N. [TAG] finding — evidence, then the likely site.`
Tags: `HONESTY` copy that outruns the product · `SAFETY` crisis/■ surfaces ·
`A11Y` · `BUG` defect · `UI` visual/consistency · `IA` information architecture.

**Severity is marked, because 26 points are not 26 priorities.** `[P1]` ships wrong
or unsafe today · `[P2]` real defect, contained · `[P3]` polish.

---

## Today

1. **[P1][HONESTY] The next-step card cites a check-in that cannot exist.** As a
   fresh guest the hero read *"A three-minute grounding practice chosen from your
   recent evening check-in."* There was no evening check-in — the session was
   minutes old, the greeting on the same screen said "Good morning", and guest mode
   saves nothing. The product's whole claim is that suggestions come from *your*
   data; a fabricated provenance line is the one sentence that must never be
   decorative. Either state the real reason or drop the clause when there is no
   check-in to cite. `TodayScreen.kt`

2. **[P3][UI] The hero title breaks badly.** "Make room / around / loud thoughts" —
   three lines with a single orphaned word on line 2. Reduce the display size a step
   at ≤720px, or allow the title two lines and truncate. `TodayScreen.kt`

3. **[P3][UI] The notification bell floats inside the greeting's text column**,
   overlapping the optical block of "take it gently." It reads as a stray element
   rather than a control anchored to the header. Same pattern as Explore's search
   icon (#8). `TodayScreen.kt`

4. **[P2][A11Y] The guest sign-in card announces as two nodes.** `uiautomator` shows
   one clickable node with an empty label and one labelled node that is not
   clickable, at identical bounds. Four fixes were attempted and none merged them
   (see `docs/TODO.md`). **Unproven, not fixed** — needs a TalkBack pass, which
   ColorOS blocks over adb. `Common.kt: SectionCard / GuestSignInCard`

## Explore

5. **[P2][UI] The four "Start by need" cards are vertically misaligned.** "Calm now"
   sits ~34px higher than "Sleep" because its subtitle wraps to two lines and the
   content is bottom-anchored. Row two aligns only because both subtitles fit one
   line — so the bug is invisible until copy changes. Top-align the content or
   reserve two subtitle lines. `ExploreScreen.kt`

6. **[P3][UI] Two of the four category glyphs read as placeholders.** "Calm now" is
   a small hollow circle and "Thoughts" a faint squiggle, beside a confident
   crescent and music note — and beside the filled lotus/calendar icons directly
   below. `ExploreScreen.kt`

7. **[P3][IA] A ~300px decorative panel sits between the page title and the first
   tappable thing**, on a screen whose entire promise is "find a suitable tool
   *quickly*". It is the single largest element above the fold and carries no
   information. `ExploreScreen.kt`

8. **[P3][UI] "Watch and learn" is a different species from its two siblings** —
   tinted fill, no icon, and a `+` where the others have a chevron — while sitting
   in the same list. Either it is a list row or it is not. `ExploreScreen.kt`

## Talk

9. **[P2][BUG] "Try together" is a section heading with nothing beneath it.** It sits
   directly above the composer with no chips. An empty labelled section reads as
   content that failed to load. Render the heading only when it has children.
   `TalkScreen.kt`

10. **[P2][UI] Three microphone affordances on one screen** — the header icon, the
    orb, and the composer button — with no indication which one records. The orb
    says "Tap the orb to talk it through", so the other two are unexplained.
    `TalkScreen.kt`

11. **[P2][BUG] The "What's on your mind?" card shows an empty dark thumbnail.** A
    rounded square with no art, no glyph and no fallback, top-left of the card. It
    reads as a broken image. `TalkScreen.kt`

12. **[P3][UI] The disabled send button is heavier than the enabled mic button** —
    send is a filled grey circle, mic is a white outline, so the *unavailable*
    control looks like the primary one. `TalkScreen.kt`

13. **[P3][HONESTY] A guest is given a fully enabled composer that cannot send.**
    The failure is now handled well (a "Sign in / Sign up" chip, fixed this session),
    but the anticipation is not: nothing says so until after they have typed and
    tapped. `TalkScreen.kt`

## Journal

14. **[P2][IA] Two primary actions for one job.** The prompt card's outlined "WRITE"
    and the full-width filled "New entry" immediately below both open the composer.
    `JournalScreen.kt`

15. **[P3][UI] "Try another" is cyan** — the only cyan interactive text seen in the
    app, where every other action is purple/periwinkle. Check it against the palette
    rather than the eye. `JournalScreen.kt`

16. **[P3][UI] ~250px of dead space** between "Your last entry will appear here" and
    the tab bar, with the empty-state sentence stranded mid-page rather than
    centred in the space it owns. `JournalScreen.kt`

## You

17. **[P2][BUG] A guest's profile row is titled "You".** The name slot renders the
    screen's own name, over an empty avatar — it reads as a placeholder that shipped.
    Say "Guest" (or "Not signed in") and make the row the sign-in door. `Screens.kt`

18. **[P1][SAFETY] The support row has two different tap targets, one of which
    dials.** "Support, any time · Tele-MANAS … 14416" carries both a "Call" pill and
    a row chevron. On the one row where a mis-tap either places a call to a crisis
    line or merely navigates, the two outcomes are 90px apart and equally
    unlabelled. Make the row's own tap explicit, or drop one of the two.
    `Screens.kt`

19. **[P3][UI] The "Companion style" icon well is grey-blue** where every other well
    on the page is purple-tinted — it looks unstyled rather than deliberate.
    `Screens.kt`

## Urgent support

20. **[P1][SAFETY] 112 leads this screen, not Tele-MANAS.** "Call emergency services"
    is the only red-filled card and sits above "Call Tele-MANAS". The documented
    contract — REDESIGN §2.3, and the ordering that `scripts/check-crisis-lines.mjs`
    asserts across backend/iOS/Android — is **Tele-MANAS first on every crisis
    surface**. The gate passes because it reads the *directory*, not this screen's
    layout. Either this screen is a deliberate exception (immediate danger first),
    in which case the contract should say so, or it is a drift the gate cannot see.
    **[decide]** `Extras.kt` / crisis screen

21. **[P2][SAFETY] "Contact my trusted person" is offered to someone who has none.**
    In guest mode no trusted contact can exist, yet the card is presented identically
    to the two that work. A crisis surface should not offer a door that opens onto a
    setup form. Label it "Set one up" when unset.

22. **[P3][UI] Tele-MANAS and "my trusted person" share one heart icon** — a public
    helpline and a named individual are not the same kind of thing, and on this
    screen the difference matters.

## Sleep

23. **[P2][IA] The Health Connect explainer buries the question it belongs to.**
    Seven lines of bordered prose sit between "How rested do you feel?" and the
    chips that answer it. It is also set in a letter-spaced style used elsewhere for
    short accents, which makes a paragraph of it hard going. Collapse it behind the
    row, or move it under the chips. `Extras.kt` / sleep screen

24. **[P2][UI] The rested-quality chips clip mid-emoji at the right edge.** The fifth
    option is cut through the middle with no scroll affordance, so it reads as a
    rendering fault rather than a scrollable row.

25. **[P3][UI] Emoji as the sleep-quality scale** (😴 😟 😐 🙂) is the only emoji in
    an app that otherwise draws its own glyphs, and it renders differently on every
    OEM — on this ColorOS handset they are noticeably rounder and flatter than the
    app's own iconography.

26. **[P3][A11Y] The ±30m time steppers are bare text**, not buttons: no fill, no
    border, and visually smaller than the 48dp target the rest of the app keeps.

---

## Checked and found good (recorded so the next pass does not re-derive)

The 18+ gate and its under-18 branch (refuses to create an adult account, offers
urgent support); Tele-MANAS-first in the You support row with the correct Indian
number on an en-GB handset (SAF-11 holding); "In for four, out for six" — the
breathing contract that has been reverted five times; the active breathing session
(ring, phase, countdown, pause, end); Journal's honest empty history; the guest
banner "nothing is saved yet" on both Today and Talk; the disabled "Save night"
with its reason stated beneath; and the sign-in screen's IME behaviour, password
placeholder, and absent Google button.
