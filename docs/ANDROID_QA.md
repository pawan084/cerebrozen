# Android — QA & Accessibility

Companion to [TECHNICAL.md](TECHNICAL.md). Tracks what the Android app has
**automated coverage** for, what still needs a **real device**, and the
**accessibility** status. Emulator verification is extensive (see TODO.md), but
some things only a physical device + TalkBack can confirm — those are listed
honestly here rather than claimed as done.

## Automated coverage (runs in CI: `:app:testDebugUnitTest`)

| Suite | Tests | Covers |
| --- | --- | --- |
| `SessionTest` | 6 | auth: sign-in stores tokens; 401 → one rotation → retry; **network blip during refresh does NOT sign out**; GET cached + served offline; sign-out wipes refresh token + cache; expired refresh signs out |
| `ScreenLogicTest` | 6 | sleep time math (`hhmm` 24h-wrap + zero-pad, `minutesToLabel`); `greetingFor` hour buckets; parsers (`parseNights`, `parseChat`, `parseEntries`) |

Testable seams: `Session` has an injectable `Store` (SharedPreferences ↔ map) and
`http` transport; screen logic is `internal` so the same-module test source set
can exercise it without Android or the network.

**Not unit-tested** (needs Android framework / a device): `VoiceEngine`
(SpeechRecognizer/TTS), `Player`/`AmbientService` (MediaPlayer), reminder
scheduling (AlarmManager), and Compose UI itself (no instrumented tests yet).

## Accessibility — done

- **Play/pause** on content rows are real `Icon`s with `contentDescription`
  (`"Play <title>"` / `"Pause <title>"`) — verified via `uiautomator dump`.
- **Voice orb** carries an `onClickLabel` (`"Talk to CereBro"` / `"Stop listening"`).
- **Nav bar** items expose `contentDescription = <tab label>` and a visible
  selected state (lavender pill).
- **Touch targets**: `PickChip` is pinned to `heightIn(min = 48.dp)`;
  `PrimaryButton` ≈ 50dp; nav items use the Material 56dp default.
- Decorative icons/images (quick-tile glyphs, hero/thumbnail `AsyncImage`,
  brand mark) pass `contentDescription = null` intentionally — the adjacent
  label/title is the accessible name.

## Accessibility — needs a physical device + TalkBack

A raw `uiautomator` dump is misleading for Compose (it doesn't reflect the
semantic *merging* TalkBack applies to `clickable` wrappers and their children),
so these must be walked with TalkBack on hardware:

- [ ] TalkBack: swipe through **every screen**, confirm reading order and that
      each control announces a meaningful name + role.
- [ ] Font scaling at 130% / 200% — no clipped text or overlapping rows
      (heroes, chips, the check-in card are the risk spots).
- [ ] Display size / density scaling.
- [ ] Contrast: measure `TextMuted2`/`TextMuted` on the dark ground and the
      dark-text-on-lavender CTA against WCAG AA (4.5:1 body, 3:1 large).
- [ ] RTL layout mirroring.

## Real-device QA checklist (hardware only)

**Voice** — mic permission prompt; real transcription accuracy; TTS playback;
behaviour where no recognition service exists (must degrade to text).
**Audio** — background playback continues when app is backgrounded; lock-screen
transport (play/pause) works; audio focus yields on a call / another player;
Bluetooth + wired output.
**Notifications** — the daily reminder actually fires at the set time; tapping it
opens the app; `POST_NOTIFICATIONS` prompt on Android 13+.
**Offline** — airplane-mode cold start serves cached reads; reconnect refreshes;
no spurious sign-out (the `SessionTest` case, confirmed on real network drops).
**Auth** — email OTP receipt end-to-end; Google (once the web client id is set);
session survives reboot.
**Performance** — cold-start time; scroll jank on Home/Sleep; image loading on a
throttled network; memory over a long session.
**Form factors / OS** — small phone, large phone, tablet, foldable; minSdk 26 →
latest.

## How to run

```bash
cd apps/android
./gradlew :app:testDebugUnitTest        # unit tests (also runs in CI)
./gradlew :app:assembleDebug            # build
# TalkBack: Settings → Accessibility → TalkBack (on device), then walk each tab.
```
