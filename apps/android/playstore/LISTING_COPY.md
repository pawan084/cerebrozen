# Play Store listing copy — CereBro

Draft for Play Console → Main store listing. Every claim here describes something
the app actually does today. Checked against `scripts/check-claims.mjs`'s banned
phrase list (medical claims, outcome guarantees, capability overclaims, and the
cognitive-training vocabulary the FTC fined Lumosity over) — paste changes back
through that list before publishing.

---

## App name (max 30 characters)

**`CereBro`** — 7 chars. Matches `app_name` in `strings.xml`, so the launcher
label and the store name agree.

If you want the listing to say what it is at a glance:

| Option | Chars |
|---|---|
| `CereBro` | 7 |
| `CereBro: Calm & Sleep` | 21 |
| `CereBro — Mind & Sleep` | 22 |

---

## Short description (max 80 characters)

```
A calm space to check in, sleep better, and think things through.
```
65 characters.

Alternatives:
```
A quiet companion for your mood, your sleep, and the thoughts in between.
```
72 characters.
```
Check in with yourself, wind down at night, and write it out. Privately.
```
71 characters.

---

## Full description (max 4000 characters)

```
CereBro is a quiet place to look after your mind — without the noise, the
streak-shaming, or the sales pitch.

Some days you want to talk. Some days you just want to log how you feel and
close the app. CereBro is built for both.


WHAT YOU CAN DO

• Check in on your mood in seconds
  A few taps to note how you're doing. Over time you can see your own patterns
  laid out plainly — no scores, no grades, no judgement.

• Wind down and sleep
  A sleep diary, gentle bedtime routines, and layered soundscapes you can mix
  yourself — rain, waves, and quiet night air — with a timer so nothing plays
  all night. If you use Health Connect, last night's sleep can be filled in for
  you.

• Talk it through
  An AI companion that listens and asks the next useful question, any hour.
  It is here to help you think, not to tell you what is wrong with you.

• Write it out
  A private journal you can lock behind your screen lock or fingerprint. Write
  freely, or start from a prompt when the page feels too blank.

• Steady yourself in the moment
  Breathing exercises, grounding tools, and short guided resets for when your
  chest is tight and you need something to do right now.

• Build gentle habits
  Small daily intentions and reminders that nudge, never nag. Miss a day and
  nothing scolds you.


PRIVACY, PLAINLY

• Your journal, moods, and conversations belong to you.
• Export everything, or delete your account and all its data, from inside the
  app — one screen, no email chain, no retention gauntlet.
• Speech is turned into text on your device. The recording itself does not
  leave your phone.
• Cloud backup is switched off for your personal data by design.
• No advertising SDKs. No selling your data. Ever.

Read the full policy at cerebrozen.in/privacy.


HONEST ABOUT WHAT THIS IS

CereBro is wellness support. It is not therapy, not a diagnosis, and not a
medical service, and it is never a substitute for professional care. It does
not treat or cure any condition, and it will not tell you what is wrong with
you.

If you are in crisis or in immediate danger, please contact your local
emergency services or a crisis line right away. CereBro shows crisis resources
when they might help, and it will never refuse to listen to you.


Questions, or something felt wrong? support@cerebrozen.in — a person reads it.
```

Approximately 2,050 characters — comfortably inside the 4,000 limit, with room
to add store-specific lines later.

---

## Deliberate omissions

Things left OUT of the copy, each for a reason:

- **No efficacy or outcome claims.** No "reduce anxiety by X", no "proven",
  no "clinically" anything. The product is not a clinical service, so those
  would be false by construction, not merely unproven.
- **No cognitive-training vocabulary.** Nothing "trains" attention, memory, or
  flexibility. This is the exact claim class `check-claims.mjs` blocks.
- **No offline promise.** The app needs the network for chat, voice, and sync.
- **No absolute privacy phrasing.** The two "never leaves / never goes anywhere"
  absolutes on `check-claims.mjs`'s CAPABILITY list are banned for a reason: chat
  reaches a model provider and voice reaches a speech provider. The copy above
  says what is true (the audio stays on the device; the transcribed text does
  not) instead of an absolute the product cannot keep.
- **No pricing or subscription language.** The Android in-app purchase client is
  not built yet, so the first release ships as a free app. Add pricing copy in
  the same release that ships billing, not before.

---

## Also needed for the listing (not written here)

| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512, 32-bit PNG | `playstore/play-icon-512.png` ✓ |
| Feature graphic | 1024×500, PNG/JPG | Not made |
| Phone screenshots | 2–8, min 320px, 16:9 or 9:16 | Not made |
| Tablet screenshots | Optional | Not made |
| Category | Health & Fitness | Choose in Console |
| Contact email | support@cerebrozen.in | — |
| Privacy policy URL | https://cerebrozen.in/privacy | Live ✓ |
| Account deletion URL | https://cerebrozen.in/delete-account | Page written; deploy needed |
