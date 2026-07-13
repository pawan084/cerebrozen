# Calm Audio & Ambient-Motion Study (W26)

Competitive teardown of how Calm uses background sound, music, and animation, compared against
CereBro's current Android audio/motion stack. Analysis only — no code changes. Companion to the
motion/audio rules in `.claude/skills/mobile-design/SKILL.md` (§4) and `docs/REDESIGN.md`.

**Verification note.** Calm's Help Center blocks direct fetching (HTTP 403), so support-page facts
below were confirmed via search excerpts of those articles, cross-checked against the
[Usability Geek case study](https://usabilitygeek.com/ux-case-study-calm-mobile-app/) and the
[ScreensDesign showcase](https://screensdesign.com/showcase/calm). Items I could not confirm are
marked **[unverified]**.

## 1. What Calm does

### 1.1 Ambient scenes — the signature move
- A **Scene** is a paired unit: a full-screen looping nature video **plus matching ambient audio**
  ("Rain on Leaves", "Mountain Lake") that plays on the home screen and continues during
  meditations ([Scenes guide](https://support.calm.com/hc/en-us/articles/115002474307)).
  The home screen *is* the scene; users swipe/pick to change it, and switching updates the visual
  backdrop and the soundscape **simultaneously** ([ScreensDesign](https://screensdesign.com/showcase/calm)).
- Scene audio starts from app open and persists across navigation — reviewers note "the app began
  to play nature sounds while I was interfacing with it" ([Usability Geek](https://usabilitygeek.com/ux-case-study-calm-mobile-app/)).
  Exact crossfade durations between scenes **[unverified]**, but transitions are described as seamless.
- **Mix control is explicit**: a Scene Volume slider (slide to mute), and during sessions the scene
  keeps playing *under* the content — Breathe settings expose independent "Breathe" and "Scene"
  volumes. Sessions duck/override rather than kill the scene, with one exception: **Sleep Stories
  replace the scene entirely** ("each story has its own unique sound design")
  ([Fall-asleep guide](https://support.calm.com/hc/en-us/articles/115002582908)).
- Escape hatches: **silent scenes** ("Silent Earth", "Silent Clouds") for users who never want
  ambient audio, and a **"play sounds outside the app" duration** (0 min → 1 hour) controlling how
  long the scene keeps playing after the app closes or a session ends.
- **Zen Mode**: rotate to landscape (or 15 s idle on web) and all chrome fades away, leaving only
  the living scene ([Zen Mode](https://support.calm.com/hc/en-us/articles/360008619874)) — the
  scene is content in its own right, not decoration.

### 1.2 The Breathe Bubble
- A single circle that **expands on inhale, pauses on hold, contracts on exhale** — you breathe
  with the shape instead of counting. Pace is user-selectable: **4, 6, or 8 breaths per minute**
  ([Breathing exercises](https://support.calm.com/hc/en-us/articles/360000069973)).
- **Haptics are a first-class toggle** (vibration marks phase changes, followable eyes-closed),
  and guidance audio has its own volume separate from the scene. Phase text ("Breathe in") paces
  alongside the bubble. Whether the guidance audio is a chime vs. spoken cue per phase **[unverified]**.

### 1.3 Player / session UX
- The player is famously **subtractive**: play/pause and stop; no free scrubbing, rewind only in
  15-second steps — deliberately removing "incessant fast-forwarding" from a meditation context
  ([Usability Geek](https://usabilitygeek.com/ux-case-study-calm-mobile-app/)). Later versions add
  playback speed and share, still minimal ([ScreensDesign](https://screensdesign.com/showcase/calm)).
- **Background playback + sleep timers** everywhere: stories/music fade out gracefully with the
  timer; the app closes when the music timer ends. Timed meditations **end with a gentle bell**,
  toggleable in settings ([Meditation timer](https://support.calm.com/hc/en-us/articles/1260802102610)).
- Music has loop/shuffle/autoplay settings; music tracks show **static artwork**, not visualizers
  ([Music settings](https://support.calm.com/hc/en-us/articles/360002398833)).

### 1.4 Motion restraint
- The only continuous motion is the scene loop itself — slow water, drifting clouds, rain. UI
  chrome is still; motion lives where the content is. Low-stimulus visual strategy is the point
  ([Raw.Studio on calm aesthetics](https://raw.studio/blog/the-aesthetics-of-calm-ux-how-blur-and-muted-themes-are-redefining-digital-design/)).
- **No audio-reactive visuals anywhere observed**: no waveforms, no EQ bars, no beat-synced
  animation, in any teardown or Calm's own marketing **[absence — cannot fully verify]**.
- Behavior under OS Reduce Motion / low battery **[unverified]** — no public documentation found.

### 1.5 What Calm deliberately does NOT do
- No EQ/waveform theatrics; no gamified sound UI (sound is never a toy or a score).
- No scrubbing through meditations; no dense transport controls.
- No visual "reward" for listening; session end is a bell and silence, not confetti.
- Scene motion is photographic and slow — never particles bursting, never synced to audio.

## 2. Side-by-side: Calm ↔ CereBro (Android)

| Calm pattern | CereBro today | Gap |
|---|---|---|
| Scene = video + matched ambient audio from app open, persists across nav | `AuroraBackground.kt`: three drifting blurred orbs, 11 s loop, section-tinted; **silent** — audio starts only when the user plays something | No launch-time ambience; visuals and audio are unlinked |
| Scene picker changes visual + soundscape together | Section accent changes the aurora; `SoundscapeMixer` (Rain/Ocean/Wind/Drone) changes audio — independently | No coupling: aurora ignores what's playing |
| Sessions duck the scene; independent volume mix | `AmbientService` ducks the bed to 0.28× under the voice companion; bed vs narration are one slot | Ducking exists but only for voice; no user-facing mix between "scene" and content |
| Scene keeps playing after app close (user-set duration) | Foreground services (`AmbientService`, `SoundscapeService`) play until stop/timer | Equivalent-or-better (explicit stop + timer); no "N min after close" concept needed |
| Breathe: pace choice (4/6/8 bpm), haptics toggle, guidance volume | `Breathe.kt`: one engine, fixed 4-4-4-4 (Reset 4-4), per-phase haptic (0.5/0.3), per-second count, reduce-motion static | No pace options, no chime/audio guidance, haptic not user-toggleable |
| Session-end bell (toggleable) | Narration ends → `stopAll()`; timer ends → 10 s fade → stop | Fade exists; no end-of-session sound or settling moment |
| Sleep timers with graceful fade | 15/30/45/60 cycle on player + mixer; 10 s fade (`AmbientService.fadeOut`), mixer countdown label | At parity |
| Static artwork on music; motion only in the scene | `ContentArt.kt` W24 "alive" heroes: 22 s imperceptible loop, tiles static; but `EqBars` + a 7-bar decorative equalizer animate in `Extras.kt` | Hero art matches Calm's restraint; EQ bars are the one thing Calm would not do |
| One audio surface at a time | Player ↔ Mixer exclusivity (REDESIGN §3.4), audio-focus handling, headphone-unplug pause | At parity; but engine switches are hard cut, no crossfade |

## 3. Recommendations (ranked)

Effort: **S** ≤ half day · **M** 1–3 days · **L** > 3 days. All must respect skill §4 (Reduce
Motion contract; looping ambience only where it *is* the content) and §2 (soundscapes are
comfort scaffolding, not therapy — never dress them up as outcomes).

1. **Adopt — audio crossfades between surfaces (S–M).** Calm's seamlessness is mostly audio
   continuity. Extract `ToolAmbience.rampVolume` into a shared helper; use it for
   Player↔Mixer handoff (fade out ~600 ms before the other starts), play/pause ramps, and
   narration→bed fallback. Cheapest single move toward "floating" feel; no new assets.
2. **Adapt — playing-content-aware aurora tint (S).** Calm couples scene visual+audio; CereBro's
   analogue: while something plays, drift the primary orb's accent toward the playing kind's
   `artAccent(kind)` (soundscape→Violet, sleep narration→ThumbBlue…). Reacts to **what** is
   playing, never the waveform — an animated color lerp, zero extra motion, Reduce-Motion safe.
3. **Adapt — mixer presets (S).** Named volume vectors over the existing four layers:
   "Monsoon night" (rain .8 / wind .3), "Shoreline" (ocean .7 / wind .2), "Still air" (drone .5).
   One-tap chips above the sliders; sliders remain the power path. India-resonant names are a
   small localization win. No new audio.
4. **Adapt — breathe pacing options + optional chime (M).** Add 4/6/8 bpm speeds to the one
   engine (presets stay; scale phase seconds), a Settings toggle for the phase haptic, and an
   **optional** soft chime at phase change (one tiny bundled asset, off by default — CereBro users
   haven't consented to surprise audio). Calm treats these as accessibility (eyes-closed use);
   so should we.
5. **Adapt — session-end ritual (S–M).** When narration/timer completes: gentle bell (toggleable,
   reuse the chime asset) + the existing fade + a quiet settling line ("notice how you feel")
   instead of an abrupt `stopAll()`. Maps to Calm's end-bell without adding ceremony.
6. **Evaluate, opt-in only — ambient welcome from app open (M).** Calm's launch-time scene audio
   is its identity, but auto-playing sound is also its top support complaint ("background sounds
   won't stop") and battery/data cost is real on Indian mid-range devices. If done: an explicit
   Settings opt-in ("ambient welcome"), starts the bed at low volume on foreground, never on
   first launch, always killed by the exclusivity rule. Default **off**. Don't chase this before 1–5.
7. **Reject — music-reactive / waveform-synced visuals.** Calm's restraint is evidence that a calm
   product doesn't need them; they read as stimulation, not soothing, and violate skill §4's
   "motion is calm and purposeful." Corollary: consider **demoting our own EQ bars** — `EqBars`
   (3 bars) and the 7-bar player visualizer in `Extras.kt` are fake-reactive decoration, the one
   current element Calm would cut. A single slow-breathing dot would say "playing" more honestly. (S)
8. **Reject — full-screen looping video scenes.** Tens of MB per scene, licensing, battery, and it
   would bury CereBro's differentiator (deterministic generative art that works offline at zero
   asset cost). The aurora + soundscape pairing in #2 delivers the same "living room" at ~0 bytes.

## 4. Do not copy

- **Auto-playing audio without consent** — surprise sound on first launch conflicts with our
  consent posture (skill §8) and DPDP "specific and informed" spirit; Calm's own help center
  documents users fighting to turn it off.
- **Mandatory sign-up → immediate paywall** (Calm's onboarding per ScreensDesign) — fails the
  OECD subscription audit we hold ourselves to.
- **Celebrity narration as credibility** — CereBro's differentiator is evidence provenance
  ("why this works"), not star power; renting fame undercuts it.
- **Daily streaks/badges around listening** — banned outright by the design system
  (presence framing only; gamification is null for outcomes).
- **Video scene asset model** — see rejection #8: battery, data, and offline behavior on our
  target devices; our generative-art bet is deliberate.
- **Positioning ambience as sleep therapy** — Calm markets scenes as helping you sleep; our
  evidence line is that soundscapes are comfort content. Keep them labeled as sounds, never
  as treatment (PRD honesty rule).

## 5. Sources

- [Calm Scenes Guide (Help Center)](https://support.calm.com/hc/en-us/articles/115002474307) ·
  [Zen Mode](https://support.calm.com/hc/en-us/articles/360008619874) ·
  [Breathing Exercises](https://support.calm.com/hc/en-us/articles/360000069973) ·
  [Fall Asleep / Sleep Timers](https://support.calm.com/hc/en-us/articles/115002582908) ·
  [Music Settings](https://support.calm.com/hc/en-us/articles/360002398833) ·
  [Meditation Timer](https://support.calm.com/hc/en-us/articles/1260802102610) ·
  [Stop Background Scene Sounds](https://support.calm.com/hc/en-us/articles/115002582948)
- [Usability Geek — UX Case Study: Calm](https://usabilitygeek.com/ux-case-study-calm-mobile-app/) ·
  [ScreensDesign — Calm UI breakdown](https://screensdesign.com/showcase/calm) ·
  [Raw.Studio — Aesthetics of calm UX](https://raw.studio/blog/the-aesthetics-of-calm-ux-how-blur-and-muted-themes-are-redefining-digital-design/)
- CereBro code read for §2: `apps/android/.../ui/screens/AuroraBackground.kt`, `ContentArt.kt`,
  `Breathe.kt`, `Extras.kt` (Sounds hub, mixer UI, NowPlayingBar/EqBars), `apps/android/.../audio/`
  (`AmbientService.kt`, `SoundscapeMixer.kt`, `SoundscapeService.kt`, `ToolAmbience.kt`, `Player.kt`).
