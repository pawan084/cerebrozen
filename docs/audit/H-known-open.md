# H — Session-known open items (2026-08-04)

Compiled from the live audits of 2026-08-02..04. These are FOUND and REPORTED
but not yet fixed; every item already carries its justification from the audit
that surfaced it. CTA items cite the CTA sweep; deferrals cite docs/TODO.md.

## H1. Missing/broken CTAs (CTA audit, 2026-08-04)

1. [BUG] PremiumScreen's only purchase CTA is `enabled = false` with an empty lambda — users are analytics-tracked into a paywall where nothing can be bought (Settings.kt:407).
2. [SAFETY] Journal's post-save support card states support but offers no action — no crisis or human-support route from the one card that must act (JournalScreen.kt support card).
3. [BUG] DataExportScreen reports a character count as text; no share sheet/save-to-file — the DPDP export is unreachable (Settings.kt:516,521).
4. [SAFETY] CrisisScreen has no route to the user's own Safety plan (Extras.kt:2558-2592).
5. [BUG] RemindersScreen: notification-permission denial is a silent no-op — no message, no route to system settings (Settings.kt:445-456).
6. [SAFETY] offline/CrisisGroundingScreen keeps a second emergency-contact store unlinked from TrustedContactScreen; grounding steps wrap forever (OfflineGuidanceScreens.kt:261,274).
7. [BUG] GoalsScreen "Make today's plan" creates the plan but never navigates to it; the screen has no onOpen (GoalsScreen.kt:187).
8. [CTA] PlayerScreen with nothing playing is inert — no "Browse sounds" (Extras.kt:1771-1774).
9. [CTA] BaselineScreen locks to "Saved" and strands the user — no "See your insights" (Baseline.kt:57-64).
10. [CTA] CBT reframe / One good thing / Intention completions offer no "View in journal" or Done (ToolScreens.kt:151-165).
11. [CTA] PatternScreen "Accept recommendation" resolves invisibly — no navigation, no confirmation (PatternScreen.kt:188-193).
12. [CTA] ProgramsScreen completed state offers only "Leave" — no next journey (Extras.kt:797,818).
13. [CTA] CbtI/MBCT offline programs: all modules done produces nothing (OfflineProgramsScreen.kt:58-81).
14. [CTA] GroundingScreen completion offers only "Start again" — no forward step (Extras.kt:2403-2410).
15. [CTA] BreathLoops completion returns to the picker with no what-next (BreathLoopsScreen.kt:452).
16. [CTA] SafetyPlanScreen: seven saves, no ending — no done/share/links onward (SafetyPlanScreen.kt:167-224).
17. [CTA] PatternGlowScreen has no finish control at all (Games.kt:78-211).
18. [CTA] GratitudeGarden flowers can't be read back, edited, or saved to journal (Games.kt:382-399).
19. [CTA] InsightReelScreen wraps modulo forever — no completion, no door to described tools (OfflineGuidanceScreens.kt:298).
20. [CTA] Journal Read mode has zero actions after reading an entry (JournalScreen.kt Read mode).
21. [CTA] Home's settled check-in chip ("Logged for today") is inert — natural Trends door (TodayScreen.kt settled branch).
22. [CTA] Sleep's "Your sleep" data card links nowhere — Trends is one tap away (SleepScreen.kt merged card).
23. [BUG] TrendsScreen error has no retry; empty state names "Privacy & memory" without linking there (TrendsScreen.kt:226-256,132).
24. [BUG] InsightsScreen error has no retry; populated baseline card is inert (Extras.kt:496-539).
25. [BUG] ProgramsScreen loading/error are bare text — only activeUnknown got a retry (Extras.kt:770-785).
26. [BUG] ContentList error branch has no retry — affects every catalogue section (Extras.kt:433-434).
27. [BUG] CompanionStyle and CrisisRegion revert silently on write failure (Settings.kt:189-191,238-240).
28. [CTA] PrivacyPolicyScreen is entirely inert; "read the full policy" is plain text, not a link (Settings.kt:481-495).
29. [CTA] SearchScreen no-match has no browse fallback (SearchScreen.kt:116).
30. [CTA] Wind-down guide cards have no reading view for their content (SleepScreen.kt guide section).

## H2. Ledgered deferrals (docs/TODO.md, all justified there)

31. [API] DELETE /sleep/{date} missing — diary can edit but not delete a night, on all clients.
32. [API] PUT /journal/{id} missing — entries readable but not editable on any client.
33. [UX] You page density/collapsed header rework — owner call before touching shared components.
34. [UX] Talk conversation search — needs a history-surface design.
35. [VOICE] Compact-orb ripple missing (indication = null, haptic only).
36. [VOICE] In-session mic mute missing in the voice overlay (End/Text only).
37. [VOICE] Voice-overlay caption caps at 180 chars with no full view.
38. [VOICE] No TTS voice preview anywhere (server picks silently).
39. [VOICE] Presence-label flicker: transcribe→send flips states <1s without debounce.
40. [UX] Talk page has no width cap on tablets (shared Page component change).
41. [UX] Bubble partial text selection unresolved (SelectionContainer vs long-press-copy conflict).
42. [UX] CBT reframe not seeded from the conversation (route-arg design needed).
43. [A11Y] Chip rails lack collection semantics (device pass needed).
44. [A11Y] RTL bubble alignment/corners untested (device pass).
45. [SOUNDS] Favourites have no recency order (store keeps a bare set).
46. [SOUNDS] Favourites of renamed server titles are never pruned.
47. [SOUNDS] Premium rows lack an upsell path (no client entitlement signal).
48. [SOUNDS] Named saved mixes absent (single persisted mix only).
49. [IA] "Activity sounds" is an app-wide setting living inside the Mixer pane (owner call).
50. [DEVICE] Mixer loop seams unverified by ear on hardware.
51. [DEVICE] Server-asset supersede path for mixer layers untested with a real upload.
52. [UX] Sleep collapsing header undecided (design call).
53. [DEVICE] Haptics feel, TTS quality, TalkBack traversal — device-only checks outstanding.
54. [I18N] ~123 safety-critical Hindi strings deliberately English pending clinical review.

## H3. Queued owner decisions (docs/TODO.md)

55. [DECIDE] Fifth "Okay" mood — the most common human answer is missing from the check-in; cross-stack taxonomy change.
56. [DECIDE] Merge Trends/Insights/Patterns into one progress hub — three overlapping analytics doors.
57. [DECIDE] Crisis screen always-dark — now that appearance is global, does the crisis surface follow theme or keep Night?
58. [DECIDE] Configurable breathing rounds (fixed per pattern today).
59. [DECIDE] Home search scope — what the Library pill should actually index.
60. [DECIDE] Journal voice entry (dictation + privacy copy).
61. [DECIDE] Premium door placement on You.
62. [DECIDE] Trusted-contact "what gets sent" preview before consent.

## H4. Sequence/placement observations (main tabs, post-wave)

63. [SEQ] Tab order Sleep-before-Talk: Talk is the flagship companion but sits center; fine — yet Sleep earns slot 2 only in the evening; no data reorders tabs by hour (deliberate — note, not defect).
64. [PLACE] Baseline lives inside Insights, but Insights markets itself as read-only analysis — first-run measurement belongs in onboarding or Home's empty state.
65. [SEQ] Home: when no plan exists the doors climb (shipped), but the banner slot still outranks the check-in for PROGRAM status — status above the primary action inverts priority for enrolled users.
66. [PLACE] Breathing surfaces live in four places (Toolkit doors, Talk chips, Sleep guide, breathe/* routes) with three different pickers — one canonical breathe entry with context args would collapse the maze.
67. [SEQ] Onboarding asks language before showing any value; consider value-first ordering (industry pattern: 1-2 wins before preferences).
68. [PLACE] Insights teaser door on Home and Weekly insights on You both open the same screen with different subtitles — same feature, two names' worth of copy drift risk (naming now aligned, subtitle logic differs).
