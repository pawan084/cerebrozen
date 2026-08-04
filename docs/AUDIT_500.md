# The 500-Point Register — feature placement, sequence, and justified bugs

**Date:** 2026-08-04 · **Target:** ≥500 justified points · **Delivered: 679**

Every point is numbered inside its section file under `docs/audit/`, cites
file:line evidence, and carries its justification (the "relevant point" the
target demanded). Sections were compiled by eight parallel deep audits, one
per domain, each verified against current code on `main`.

## Sections

| § | File | Domain | Points |
|---|------|--------|-------:|
| A | [audit/A-android-ia.md](audit/A-android-ia.md) | Android IA — feature sequence & placement | 76 |
| B | [audit/B-android-screens.md](audit/B-android-screens.md) | Android sub-screens — behavior bugs | 94 |
| C | [audit/C-backend.md](audit/C-backend.md) | Backend — API, security, data integrity | 118 |
| D | [audit/D-web-app.md](audit/D-web-app.md) | Web app (app.cerebrozen.in) | 80 |
| E | [audit/E-web-landing-admin.md](audit/E-web-landing-admin.md) | Landing + admin dashboard | 66 |
| F | [audit/F-ios.md](audit/F-ios.md) | iOS client | 80 |
| G | [audit/G-crossstack.md](audit/G-crossstack.md) | Cross-stack contracts & parity | 97 |
| H | [audit/H-known-open.md](audit/H-known-open.md) | Session-known open items (CTA sweep, ledger, decisions) | 68 |
|   | | **Total** | **679** |

## The ranked top 20

Severity-first; section·number references point into the files above.

1. **iOS does not build** — `HomeView.swift:152` constructs the deleted
   `GamesHubView`; nothing ships until this is fixed (F).
2. **Oracle thread hijack** — `/oracle/messages` and `/oracle/confirm` accept a
   caller-supplied `thread_id` with no ownership check; another user's paused
   `save_journal` can be approved by an attacker (C1, `oracle.py:158,215`).
3. **One StoreKit receipt → unlimited premium accounts** — receipts are never
   bound to the caller and transaction ids are never deduped (C2,
   `users.py:119-139`).
4. **App Store webhook replayable** — no `ProcessedWebhook` guard, so a
   re-delivered `EXPIRED` downgrades a paying subscriber (C3, `webhooks.py:34-61`).
5. **Crisis numbers are region-blind on Android** — quick-dial pills and the
   directory hardcode India's 14416/112 regardless of the You → Crisis region
   setting (G, corroborated independently by A and B; `Extras.kt:2552`,
   `TalkScreen.kt:917`, `Screens.kt:166-173`).
6. **Safety-plan edits silently lost** — all seven sections live in plain
   `remember`; activity recreation (rotation, low memory) discards them (B,
   `SafetyPlanScreen.kt:62`).
7. **Web app signs users out on any 403** — consent-gated routes return 403,
   which `lib/api.ts:101-105` treats as session death (D).
8. **Push deep links dropped** — `MainActivity` never reads the intent URI, so
   every server nudge lands on Home regardless of its promise (A,
   `Push.kt:122-124`).
9. **Six finished offline surfaces are orphaned** — CBT-I, MBCT, body scan,
   crisis grounding, insight reel, offline guided imagery are registered routes
   with zero inbound links (A, `CereBroApp.kt:589-594`).
10. **Premium users can't play premium narration on web** — the unauthenticated
    `/content` fetch strips `audio_url` before entitlement is ever consulted (D).
11. **Memory/path games score only the first cell** — a span-6 sequence passes
    with one correct tap (B, `GameSession.kt:443`, `GameEngine.kt:175-216`).
12. **`voice_storage` / `model_training` consents enforced nowhere** — two of six
    DPDP itemized categories are collected, exported, shown in admin, and used at
    zero call sites (C5, `models/consent.py:19-25`).
13. **Idempotency written after the write, in a second transaction** — the
    offline queue's concurrent retry still double-inserts; the exact duplicate
    the mechanism exists to prevent (C4, `moods.py:53-67`).
14. **`.crisis` oracle events swallowed on iOS** — the stream's default branch
    drops them, so iOS users miss the escalation card the backend sent (F,
    `BackendService.swift:447-469`).
15. **Journal's elevated-risk card shows risk without a pathway** — Tele-MANAS
    named as plain text, no dial action, rendered below recent entries (A + H,
    `JournalScreen.kt:634-643`).
16. **PremiumScreen's only purchase CTA is `enabled = false`** — users are
    analytics-tracked into a paywall where nothing can be bought (H1,
    `Settings.kt:407`).
17. **DPDP data export unreachable on Android** — DataExportScreen prints a
    character count; no share sheet, no file (H3, `Settings.kt:516,521`); and
    the export payload is cached in the pref-backed cache until sign-out (B,
    `net/Session.kt:526-549`).
18. **Reminder hour silently resets to 9 AM** — any toggle or reboot reschedules
    with the default; the onboarding "evening" choice is unrecoverable, no
    screen sets it back (A, `Settings.kt:446,455`, `Reminders.kt:46,90-98`).
19. **Admin has no audit log and a one-click all-user broadcast** — the disable
    reason is dropped at `admin.py:165`; safety-classifier prompt rollback is
    one click (E).
20. **Dummy data seeds real iOS installs** — demo journal/chat entries are
    inserted outside any reset/test gate (F, `CereBroApp.swift:181-182`).

## Cross-section corroboration (deduped in the total? No — see note)

Some defects were found independently by multiple auditors. Each section
counts its own finding because each cites a different surface or consequence
of the same root cause; the overlap is corroboration, not double-counting of
one point — but for planning purposes treat each cluster as ONE fix:

- **Region-blind crisis numbers**: G (contract violation, incl. pills added in
  the 2026-08-04 chat wave), A (CrisisScreen placement), B (CrisisRegion
  false-success), H (CTA sweep). One fix: a region→numbers map consumed by
  every dial surface.
- **Journal support card action-less**: A (placement) + H (CTA). One fix.
- **Safety plan gaps**: B (data loss) + H16 (no ending CTA) + A (not linked
  from CrisisScreen). One rework.
- **Offline crisis-grounding duplication**: A (orphaned route) + H6 (second
  contact store). One consolidation.
- **Premium path**: C2/C3 (receipt/webhook) + H1/16 (dead CTA) + D (web
  entitlement) — three independent breaks in one funnel.

## Method

Each auditor rated only what it could justify: a point needed (a) a file:line
citation, (b) the expected behavior and why (platform convention, house rule,
clinical-safety rule, or an explicit contract in docs/ARCHITECTURE.md), and
(c) the observed divergence. Already-correct behaviors that looked suspicious
were verified and excluded (each section lists its exclusions where relevant).

Nothing in this register has been fixed. The next step is prioritization —
the top 20 above is the recommended order, with the five safety/security
items (1–6) and the sign-out bug (7) ahead of everything else.
