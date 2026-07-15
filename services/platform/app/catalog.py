"""The rest-and-recovery content catalog — sleep scenes, soundscapes, wind-down
steps, and multi-day programs.

## Why this is static, and why it lives here

None of this is user content and none of it is licensed media. It is a small, curated
LIST that tells the app which on-device scenes exist. Every entry plays through the
phone's OWN bundled ambient beds and synthesized tones (audio_url is blank, so
MediaUrls falls back to the bundled bed — see apps/android .../audio/MediaUrls.kt), and
every scene draws generative art (image_url blank). So the lists populate and PLAY with
zero external audio: when real narrated stories are licensed, their URLs drop into these
same entries and nothing else changes.

It lives on the platform because that is the base the app already calls for `/content`,
`/media/catalog`, and `/programs/*` — and because a catalog of scene titles is app
configuration, not the personal content the "counts, never content" firewall guards.

## The field names are the CLIENT's

`duration_min`, `image_url`, `audio_url`, `today_guide` — these are what apps/android
parses (ui/screens/Extras.kt, MediaCatalog.kt). A catalog that invented its own vocabulary
would just move the translation somewhere less visible.
"""

from __future__ import annotations

from datetime import datetime, timezone


def _scene(title: str, subtitle: str, duration_min: int = 0) -> dict:
    # audio_url / image_url intentionally blank — bundled bed + generative art.
    return {
        "title": title,
        "subtitle": subtitle,
        "duration_min": duration_min,
        "premium": False,
        "image_url": "",
        "audio_url": "",
    }


# Sleep scenes — the "Rest & recovery" long-form list. Open-ended (duration 0), so the
# app labels them "Ambient"/"Story" rather than a runtime.
_SLEEP = [
    _scene("Night Lake", "Still water under a low moon"),
    _scene("Gentle Rain", "Soft rain on a quiet roof"),
    _scene("Distant Thunder", "A warm storm, far away"),
    _scene("Ocean Calm", "Slow waves, and slower breath"),
    _scene("Deep Forest", "Wind in high branches at dusk"),
    _scene("Snowfall", "Slow flakes over a sleeping town"),
    _scene("Cabin in the Rain", "Rain on the roof, warm inside"),
    _scene("Meadow at Dusk", "A cooling breeze, the day letting go"),
    _scene("Harbour at Night", "Water against old wood, lights on the water"),
    _scene("Riverbank", "A slow current, and nowhere to be"),
    _scene("Before First Light", "The quietest hour, just before dawn"),
]

# Soundscapes — the mixer/sounds hub. Each maps to a bundled ambience the phone already
# ships, so they loop immediately.
_SOUNDSCAPE = [
    _scene("Rain", "Steady rainfall"),
    _scene("Ocean", "Rolling surf"),
    _scene("Mountain Wind", "High, clean air"),
    _scene("Deep Drone", "A low, grounding hum"),
]

# Wind-down — short guided settles for the evening routine. These DO carry a duration.
_WIND_DOWN = [
    _scene("Unclench", "Release your jaw, your shoulders, your hands", 3),
    _scene("Body scan", "Head to toe — noticing, not fixing", 5),
    _scene("Tomorrow, parked", "Name the first thing, then set it down", 2),
]

_CONTENT: dict[str, list[dict]] = {
    "sleep": _SLEEP,
    "soundscape": _SOUNDSCAPE,
    "wind_down": _WIND_DOWN,
}


def content_for(kind: str) -> list[dict]:
    """The scene list for a kind. Unknown kinds are an empty list, never an error —
    the app renders a friendly 'nothing here yet', and a new kind added client-side
    must not 500 the screen while the catalog catches up."""
    if kind == "program":
        return [
            {"id": pid, "title": p["title"], "subtitle": p["subtitle"]}
            for pid, p in PROGRAMS.items()
        ]
    return _CONTENT.get(kind, [])


# ── the keyed one-shot / loop catalogue (mixer, game SFX, scene beds) ─────────
#
# Every key the app knows (apps/android .../audio/MediaCatalog.kt). url blank = "server
# has no bytes yet" → the phone uses its bundled loop or synthesized tone, which is the
# whole point: the catalogue exists (so nothing 404s and MediaCatalog.loaded is true),
# and the audio is the on-device fallback until real files replace these urls.
_LOOPING = {
    "ambience.rain", "ambience.ocean", "ambience.wind", "ambience.drone", "ambience.bed",
    "scene.night_lake", "scene.dawn",
}
_MEDIA_KEYS = sorted(_LOOPING | {
    "breathe.inhale", "breathe.hold", "breathe.exhale",
    "game.pad.0", "game.pad.1", "game.pad.2", "game.pad.3",
    "game.pattern.success", "game.pattern.reset", "game.ripple", "game.bloom",
    "chime.timer_bell",
})


def media_catalog() -> list[dict]:
    return [{"key": key, "url": "", "loop": key in _LOOPING} for key in _MEDIA_KEYS]


# ── multi-day programs ───────────────────────────────────────────────────────
#
# Evidence-informed (CBT-I / attention-training), authored as plain text — no licensing.
# One `today_guide` per day; the current day is computed from the enrollment date, so
# there is no "advance the day" endpoint to get out of sync (see program_payload).
PROGRAMS: dict[str, dict] = {
    "better-sleep": {
        "title": "Better Sleep in 7 Days",
        "subtitle": "CBT-I fundamentals — one small change a day",
        "days": [
            {"title": "A fixed wake time", "body": "Pick one wake time and keep it — even "
             "tomorrow, even on the weekend. A steady morning anchors the whole night. "
             "Set it now."},
            {"title": "Light, first thing", "body": "Within an hour of waking, get daylight "
             "on your face for ten minutes. It sets the clock that decides when you'll feel "
             "sleepy tonight."},
            {"title": "The wind-down cue", "body": "Choose one calm thing you'll do every "
             "night before bed — the same thing, in the same order. Tonight, try the "
             "wind-down routine in Rest & recovery."},
            {"title": "Bed is for sleep", "body": "If you're awake and restless for more "
             "than twenty minutes, get up, sit somewhere dim, and come back when you're "
             "drowsy. You're teaching the bed to mean sleep."},
            {"title": "Caffeine's long tail", "body": "Caffeine has a six-hour half-life. "
             "Draw a line in the early afternoon and keep the evening clear."},
            {"title": "Park tomorrow", "body": "Before bed, write down the first thing you'll "
             "do tomorrow. On the page, it can stop circling in your head."},
            {"title": "Notice what held", "body": "Look back at the week. Keep the one or two "
             "changes that helped, and let the rest go. Consistency beats perfection."},
        ],
    },
    "steady-focus": {
        "title": "Steadier Focus",
        "subtitle": "Five days to a calmer attention span",
        "days": [
            {"title": "One thing at a time", "body": "Pick the single most important task "
             "for today and start there, before anything else opens. Attention is a "
             "muscle; single-tasking is the rep."},
            {"title": "The 20-minute block", "body": "Work in one 20-minute block with "
             "nothing else in reach. When it drifts, notice, and come back. That returning "
             "IS the training."},
            {"title": "A home for distractions", "body": "Keep a scratch note beside you. "
             "When a thought pulls, park it on the note and return to the task. It'll be "
             "there later; it doesn't need you now."},
            {"title": "Rest on purpose", "body": "Between blocks, rest without a screen — "
             "look out a window, stretch, breathe. Attention refills in the gaps, not "
             "under more input."},
            {"title": "Protect the first hour", "body": "Guard the first hour of your day "
             "for the work that matters most, before the inbox sets the agenda. Make it a "
             "standing appointment with yourself."},
        ],
    },
    # The three the Journeys tab already promises ("feedback, delegation, influence").
    # Workplace-coaching, not therapy: every day is one concrete rehearsal, in the
    # CereBroZen voice — calm, second person, one small step.
    "the-feedback-conversation": {
        "title": "The Feedback Conversation",
        "subtitle": "Five days to prepare a hard conversation well",
        "days": [
            {"title": "Name the one thing", "body": "Pick the single behaviour you want to "
             "address — not the person, and not five things at once. Write it in one "
             "sentence: what happened, and what it affected."},
            {"title": "Separate fact from story", "body": "Write down what a camera would "
             "have seen, apart from what you concluded about it. You'll open with the "
             "facts; the story is yours to check, not to deliver as truth."},
            {"title": "Lead with the why", "body": "In one line, name why this matters — to "
             "the work, to them, to the team. People take in feedback when they understand "
             "the stakes, not just the fault."},
            {"title": "Ask before you tell", "body": "Plan your opening question. The best "
             "of these is a conversation: find out how they saw it before you lay out how "
             "you did. You may be missing half the picture."},
            {"title": "Have it", "body": "Pick a private moment, open with the fact and the "
             "why, then stop and listen. One thing, said plainly, and the room to respond. "
             "You've prepared enough — go."},
        ],
    },
    "delegation-that-sticks": {
        "title": "Delegation That Sticks",
        "subtitle": "Five days to hand work off and leave it gone",
        "days": [
            {"title": "Pick what to let go", "body": "List what only you can do, and what "
             "you're holding onto out of habit. Choose one thing from the second list to "
             "hand off this week."},
            {"title": "Hand over the outcome", "body": "Write the result you need and why it "
             "matters — then stop. Handing someone your exact method removes the one thing "
             "that would make them own it."},
            {"title": "Name the guardrails", "body": "Decide the two or three things that are "
             "non-negotiable — a deadline, a budget, a standard — and say them plainly. "
             "Freedom inside clear limits is what delegation actually is."},
            {"title": "Set the check-in, then step back", "body": "Agree when you'll next "
             "talk, and leave the space between it alone. Checking in early and often is "
             "just taking the task back one question at a time."},
            {"title": "Debrief, don't grade", "body": "When it's done, ask what they'd do "
             "differently and offer one thing you noticed. That's how the next hand-off "
             "needs less of you, not more."},
        ],
    },
    "quiet-influence": {
        "title": "Quiet Influence",
        "subtitle": "Five days to move a decision without authority",
        "days": [
            {"title": "Find the one you need", "body": "Name the single person whose yes "
             "would move this forward. Influence is rarely a whole room; it's usually one "
             "conversation you haven't had yet."},
            {"title": "Understand their stakes", "body": "Write what this decision costs or "
             "gives THEM, not you. You can't move someone whose position you can't state "
             "better than they can."},
            {"title": "Trade in their currency", "body": "Reframe your ask in terms of what "
             "they already care about. The same proposal lands differently when it answers "
             "their question instead of yours."},
            {"title": "Ask, don't announce", "body": "Plan to open with a question that "
             "invites them in, not a pitch that puts them on the defensive. People support "
             "what they helped shape."},
            {"title": "Have the corridor conversation", "body": "Go to them, one to one, "
             "before the meeting where it's decided. The room ratifies; the corridor "
             "persuades. Have it there first."},
        ],
    },
}


def program_payload(program_id: str, started_at: datetime | None) -> dict | None:
    """The `{program: {...}}` body for GET /programs/active.

    The current day is DERIVED from the enrollment date — not stored, not advanced by an
    endpoint — so it can never drift out of step with reality. Day 1 is the day you
    enrol; it climbs by one each calendar day and stops at the last.
    """
    program = PROGRAMS.get(program_id)
    if program is None or started_at is None:
        return None
    days = program["days"]
    total = len(days)
    if started_at.tzinfo is None:
        started_at = started_at.replace(tzinfo=timezone.utc)
    elapsed = (datetime.now(timezone.utc).date() - started_at.date()).days
    day = max(1, min(total, elapsed + 1))
    completed = elapsed + 1 > total
    guide = days[day - 1]
    return {
        "id": program_id,
        "title": program["title"],
        "subtitle": program["subtitle"],
        "day": day,
        "days": total,
        "completed": completed,
        "today_guide": {"title": guide["title"], "body": guide["body"]},
    }
