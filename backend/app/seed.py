"""Seed the database with an admin user, a demo user, and the content catalogue.

Idempotent: safe to run on every startup. Mirrors the iOS app's dummy content.
"""
from __future__ import annotations

import logging

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import hash_password
from app.models.consent import Consent
from app.models.content import ContentItem
from app.models.user import User
from app.services import nudges

logger = logging.getLogger("cerebro.seed")

# Imagery honesty pass (2026-07-04): no stock photo URLs — clients render
# their branded symbol wells for empty image_url; admins can attach real
# licensed art per item via the CMS when it exists.
_IMG = ""

_CONTENT = [
    ("Rain over quiet hills", "Sleep story · 18 min", "sleep", "moon.stars", 18, False),
    ("Ocean breathing", "Breathwork · 5 min", "breath", "waveform", 5, False),
    ("Deep night drift", "Soundscape · 45 min", "soundscape", "moon.zzz", 45, True),
    ("Morning calm", "Start your day · 6 min", "meditation", "sun.max", 6, False),
    ("Soft focus", "Deep work · 12 min", "meditation", "scope", 12, False),
    ("Body scan", "Release tension · 10 min", "meditation", "figure.mind.and.body", 10, True),
    ("Ease work stress", "7-day plan · Breathing + journaling", "program", "leaf", 0, False),
    ("Sleep deeper", "10-day wind-down program", "program", "moon.stars", 0, True),
    ("Stop overthinking", "5-day CBT focus", "program", "brain", 0, False),
    # Sleep Reset (docs/REDESIGN.md §3.2 Phase 2/3): the CBT-I-informed weekly
    # program as a first-class Program. The schema has no per-day structure —
    # a program is one row and the journey card derives "day N of 7" from the
    # enrollment start date — so the seven day themes live in the narration
    # script below (server-persisted, admin-editable) until a per-day model
    # exists. Day themes: 1 steady wake time · 2 bed is for sleep · 3 wind-down
    # hour · 4 the 20-minute rule · 5 caffeine, light and timing · 6 your sleep
    # window · 7 keeping it.
    ("Sleep Reset", "7-day plan · One steady sleep habit each night", "program", "moon.zzz", 0, False),
    # CBT-I-informed wind-down guide (docs/SLEEP_TRACKING.md) — awareness copy,
    # never diagnosis/treatment claims. Mirrored as the iOS offline fallback.
    ("Keep a steady wake time", "Anchors your body clock — even after a rough night", "wind_down", "alarm", 0, False),
    ("Dim the inputs", "Screens down and lights low, 30 minutes before bed", "wind_down", "moon.haze", 0, False),
    ("Bed is for sleep", "Awake 20+ minutes? Get up, reset gently, return sleepy", "wind_down", "bed.double", 0, False),
    ("Slow the body first", "Two minutes of soft breathing before lights out", "wind_down", "wind", 2, False),
]

# Narration scripts for the items where a spoken voice IS the experience
# (stories, breathwork, meditations, wind-down guidance). Soundscapes and
# programs deliberately have none — ambient audio is correct there — with
# one exception: Sleep Reset's script is the program's week guide (the
# canonical server-side home for its seven day themes; see _CONTENT note),
# and doubles as a calm spoken overview if an admin ever narrates it.
# Same copy rules as the catalogue: calm, second-person, non-clinical.
# Admins generate the audio per item via POST /admin/content/{id}/narrate.
_SCRIPTS: dict[str, str] = {
    "Rain over quiet hills": (
        "Welcome. This is a story for the end of the day. There is nothing to do "
        "now, and nowhere to be. Let your body settle into the bed, and let the "
        "bed hold your whole weight. If your eyes want to close, let them.\n\n"
        "Somewhere far from here, evening is arriving over a valley of quiet "
        "hills. The light is low and soft, the color of amber turning to slate. "
        "A narrow path winds between the hills, worn smooth by years of "
        "unhurried footsteps. Tonight, it is yours alone.\n\n"
        "The first drops of rain begin — not a storm, just a gentle, steady "
        "rain, the kind that asks nothing of anyone. It taps on the leaves of "
        "the old oaks along the path. It darkens the stones, one by one. You "
        "are warm and dry under the wide eave of a small stone cottage, "
        "watching the valley soften behind curtains of rain.\n\n"
        "Inside, a lamp is lit. There is a chair by the window with a heavy "
        "blanket, and you settle into it, pulling the blanket over your knees. "
        "The rain thickens a little, and the sound deepens — a low, even wash "
        "over the hills, like the valley breathing out.\n\n"
        "You notice your own breath has slowed to match it. In... and out. "
        "There is nothing in this valley that needs you tonight. The sheep are "
        "folded in the lower field. The lamps in the far farmhouses blink out, "
        "one window at a time. The hills hold everything, the way they have "
        "held everything for ten thousand years.\n\n"
        "The rain goes on, patient and unhurried. Each wave of it carries a "
        "little more of the day away — the conversations, the small worries, "
        "the lists. They wash down the path, into the stream at the valley "
        "floor, and are carried gently out of sight.\n\n"
        "Your eyes are heavy now. The lamp glows dimmer. The chair holds you, "
        "the blanket warms you, and the rain keeps its soft promise: it will "
        "go on falling, steady and kind, whether you listen or not. So you "
        "let go of listening. The hills darken to sleep. The valley breathes. "
        "And you drift, warm and safe, into the quiet middle of the night."
    ),
    "Ocean breathing": (
        "Find a comfortable position, sitting or lying down. Let this be easy.\n\n"
        "We're going to breathe the way the ocean moves — long, slow waves, "
        "arriving and receding. There's no need to force anything. If your "
        "breath wants to be shallower today, that's okay too.\n\n"
        "Begin by breathing in slowly through the nose... feeling the breath "
        "rise like a wave gathering... and now let it fall away, slowly, "
        "through soft lips... like the wave sliding back over the sand.\n\n"
        "Again. In... two... three... four... and out... two... three... "
        "four... five... six. Let the out-breath be a little longer than the "
        "in-breath. That longer exhale is the body's own signal to settle.\n\n"
        "If thoughts arrive, let them be boats on the horizon. You don't need "
        "to board them. Return to the wave. In... the chest and belly rise... "
        "and out... everything softens and empties.\n\n"
        "Keep this rhythm going at your own pace. Wave after wave. Each breath "
        "asks nothing of you except to be noticed. And when you're ready to "
        "finish, take one fuller breath in... hold it gently for a moment... "
        "and let it go completely. Notice how you feel — even one degree "
        "calmer counts. The ocean is always there when you need it."
    ),
    "Morning calm": (
        "Good morning. Before the day asks anything of you, take these few "
        "minutes for yourself.\n\n"
        "Sit somewhere comfortable and let your spine be tall but easy. Rest "
        "your hands wherever they naturally fall. You can close your eyes, or "
        "soften your gaze toward the floor.\n\n"
        "Take a slow breath in through the nose... and let it out with a "
        "quiet sigh. One more like that. Let the night loosen its grip.\n\n"
        "Now let the breath find its own pace, and simply watch it. The cool "
        "air arriving... the warm air leaving. Nothing to fix, nothing to "
        "improve. Just this.\n\n"
        "When you're ready, ask yourself gently: what matters most today? Not "
        "the whole list — just the one thing. Let it surface on its own. "
        "Picture yourself moving through it steadily, without hurry.\n\n"
        "Now widen your attention. Feel the weight of your body in the chair, "
        "the temperature of the room, the sounds around you arriving and "
        "passing. The day is already here, and you are already enough for it.\n\n"
        "Take one more full breath... in... and out. Open your eyes if they "
        "were closed. Carry this pace with you — you can return to it with a "
        "single slow breath, any time before tonight."
    ),
    "Soft focus": (
        "This is a short reset for your attention. You can keep your eyes "
        "open or closed — whatever suits where you are.\n\n"
        "Start by noticing how your attention feels right now. Scattered? "
        "Tight? Pulled in six directions? No judgment — attention gets tired "
        "like anything else. We're going to let it rest, then gather it.\n\n"
        "Take three slow breaths, and with each exhale, let one layer of "
        "noise fall away. First breath... let go of everything that happened "
        "before this moment. Second breath... let go of everything scheduled "
        "after it. Third breath... just this.\n\n"
        "Now pick one small anchor: the feeling of your feet on the floor, or "
        "the breath at the tip of your nose. Rest your attention there — "
        "lightly, the way you'd hold something delicate. When the mind darts "
        "away, notice where it went, and walk it back kindly. Every return is "
        "a repetition; the returns are the training.\n\n"
        "Stay with the anchor a while longer. Let your focus become soft and "
        "steady, like lamplight rather than a spotlight.\n\n"
        "Before you go back, choose the single next thing you'll give this "
        "gathered attention to. Just one. Take a final breath in... and out... "
        "and begin, gently."
    ),
    "Body scan": (
        "Lie down or sit back, and let your body be fully supported. This is "
        "a slow tour from head to toe, releasing what you find along the way.\n\n"
        "Begin at the crown of your head. Notice your scalp, your forehead. "
        "If the brow is furrowed, let it smooth. Let the small muscles around "
        "the eyes soften. Unclench the jaw — let the teeth part slightly, "
        "and the tongue rest heavy.\n\n"
        "Move down to the neck and shoulders. Shoulders often carry the day "
        "without telling us. Breathe in... and as you breathe out, let them "
        "drop, even a few millimeters. That's enough.\n\n"
        "Feel your arms next — upper arms, elbows, forearms, and all the way "
        "into the hands. Let the fingers uncurl. Warmth gathering in the palms.\n\n"
        "Bring your attention to the chest and belly. Feel them rise and fall "
        "with the breath — no need to change the rhythm. Then the back: broad "
        "and strong, pressed into whatever holds you. Let it be held.\n\n"
        "Down through the hips... the thighs... the knees. Notice the calves, "
        "the ankles, and finally the feet — heels, arches, each toe. Let the "
        "whole body be heavy now, from crown to toe, one connected, resting "
        "weight.\n\n"
        "Take a moment to feel the body as a whole — softer than when you "
        "began. Whatever tension remains is allowed to be there; you've made "
        "room around it. When you're ready, wiggle your fingers and toes, "
        "take a fuller breath, and return at your own pace."
    ),
    "Sleep Reset": (
        "Welcome to Sleep Reset — seven days, one small change at a time. "
        "Nothing here is a quick fix, and none of it is medical care. These "
        "are quiet, well-worn habits that give sleep room to find you. If "
        "your nights stay hard for weeks, talking to a doctor is a kind next "
        "step — this program sits alongside that care, never in place of it.\n\n"
        "Day one: a steady wake time. Your body clock sets itself by when "
        "you get up, more than by when you lie down. Pick a wake time you "
        "can keep every day this week — even after a short night, even on "
        "the weekend — and let it be boring. Boring is the point.\n\n"
        "Day two: bed is for sleep. Let the bed mean one thing. Reading, "
        "scrolling, planning and worrying can all live somewhere else — a "
        "chair, another room. When the bed keeps a single, simple meaning, "
        "lying down starts to feel like an answer instead of a question.\n\n"
        "Day three: your wind-down hour. In the last hour before bed, turn "
        "the volume of the world down. Dim the lights, set the phone out of "
        "reach, and choose something that ends — a few pages, a warm shower, "
        "laying out tomorrow. A runway, not a wall.\n\n"
        "Day four: the twenty-minute rule. If you've been awake in bed for "
        "what feels like twenty minutes, get up. Keep the lights low, do "
        "something genuinely dull, and come back only when your eyelids are "
        "actually heavy. Each round teaches the same quiet lesson: bed is "
        "where sleep happens.\n\n"
        "Day five: caffeine, light and timing. The day shapes the night. "
        "Let caffeine end by early afternoon, get some daylight in the "
        "morning, and keep late evenings gentle — big meals, hard workouts "
        "and bright rooms all tell the body it's earlier than it is.\n\n"
        "Day six: your sleep window. Consistency beats duration. Rather "
        "than chasing more hours, keep the same window — roughly the same "
        "bedtime, the same wake time — and let the body fill it more fully "
        "night by night. A slightly shorter, steadier window often rests "
        "better than a long, drifting one.\n\n"
        "Day seven: keeping it. Some nights will still be rough — that's "
        "part of sleep, not a failure of yours. When one comes, return to "
        "the anchors: the same wake time, the wind-down hour, up if you're "
        "wide awake. You know the way back now, and you can walk it as many "
        "times as you need."
    ),
    "Keep a steady wake time": (
        "Here's one small anchor for better nights: a steady wake time.\n\n"
        "Your body runs on an internal clock, and that clock sets itself by "
        "when you get up — more than by when you go to bed. Waking at about "
        "the same time every day, even after a short night, even on weekends, "
        "teaches your body when sleep should begin and end.\n\n"
        "After a rough night, the temptation is to sleep in and repay the "
        "debt. It usually costs more than it repays: tonight's sleepiness "
        "arrives later, and the cycle drifts. Get up at your usual time, get "
        "some light, and let tonight collect the difference.\n\n"
        "Pick a wake time you can actually keep, set it once, and let it be "
        "boring. Steady and boring is exactly how the body likes its mornings."
    ),
    "Dim the inputs": (
        "About thirty minutes before bed, start turning the volume of the "
        "world down.\n\n"
        "Bright screens and busy feeds tell your brain it's still daytime — "
        "still time to react, scroll, and plan. Dim the lights in the room. "
        "Put the phone somewhere it can't murmur at you. If you want to do "
        "something, choose something that ends: a few pages of a book, a "
        "warm shower, stretching, or laying things out for tomorrow.\n\n"
        "You're not forcing sleep — you can't force sleep. You're just "
        "removing the things that hold you awake, and letting the natural "
        "tiredness that's already there come forward.\n\n"
        "Think of it as a runway, not a wall: lights lowering, inputs "
        "quieting, the day gliding down to land."
    ),
    "Bed is for sleep": (
        "If you've been lying awake for what feels like twenty minutes or "
        "more, here's a kind rule: get up.\n\n"
        "It sounds backwards, but staying in bed wrestling with wakefulness "
        "teaches your brain that bed is a place for wrestling. Getting up "
        "keeps the bed's meaning simple: this is where sleep happens.\n\n"
        "Go to another room if you can, keep the lights low, and do something "
        "genuinely dull — sit with a warm drink without a screen, page "
        "through something gentle, breathe slowly. No bright screens, no "
        "productivity. When your eyelids grow heavy — actually heavy — go "
        "back to bed.\n\n"
        "If sleep doesn't come again, repeat it. No frustration needed; each "
        "round is teaching the same quiet lesson. Bed is for sleep, and "
        "sleep comes to find you there."
    ),
    "Slow the body first": (
        "Before lights out, give your body two minutes of slow breathing — "
        "the body settles first, and the mind follows.\n\n"
        "Sit on the edge of the bed, or lie down. Breathe in through your "
        "nose for a count of four... and out through soft lips for a count "
        "of six. The longer exhale is the signal; it tells your whole system "
        "the day is over.\n\n"
        "In... two... three... four. Out... two... three... four... five... "
        "six.\n\n"
        "Let the shoulders fall with each exhale. Let the jaw unclench. "
        "Keep the counts comfortable — if four and six feel like a stretch, "
        "make them three and five. Ease beats effort here.\n\n"
        "Continue for a dozen breaths or so, and let the last few grow "
        "quieter, like footsteps reaching the end of a hallway. Then lights "
        "out. Nothing else to do tonight — you've already begun the landing."
    ),
}

# Per-day program guides (W15): the structured [{"title","body"}] form of a
# program's days, served as `today_guide` on GET /programs/active for the
# enrollment's current day. Sleep Reset's seven entries mirror the seven day
# themes already canonical in its narration script above. Same copy rules as
# everything else here: calm, second-person, adjunct-framed — habits alongside
# care, never medical claims.
_DAY_GUIDES: dict[str, list[dict]] = {
    "Sleep Reset": [
        {
            "title": "A steady wake time",
            "body": (
                "Your body clock sets itself by when you get up, more than by "
                "when you lie down. Pick a wake time you can keep every day "
                "this week — even after a short night, even on the weekend — "
                "and let it be boring. Boring is the point."
            ),
        },
        {
            "title": "Bed is for sleep",
            "body": (
                "Let the bed mean one thing. Reading, scrolling, planning and "
                "worrying can all live somewhere else — a chair, another room. "
                "When the bed keeps a single, simple meaning, lying down "
                "starts to feel like an answer instead of a question."
            ),
        },
        {
            "title": "Your wind-down hour",
            "body": (
                "In the last hour before bed, turn the volume of the world "
                "down. Dim the lights, set the phone out of reach, and choose "
                "something that ends — a few pages, a warm shower, laying out "
                "tomorrow. A runway, not a wall."
            ),
        },
        {
            "title": "The twenty-minute rule",
            "body": (
                "If you've been awake in bed for what feels like twenty "
                "minutes, get up. Keep the lights low, do something genuinely "
                "dull, and come back only when your eyelids are actually "
                "heavy. Each round teaches the same quiet lesson: bed is "
                "where sleep happens."
            ),
        },
        {
            "title": "Caffeine, light and timing",
            "body": (
                "The day shapes the night. Let caffeine end by early "
                "afternoon, get some daylight in the morning, and keep late "
                "evenings gentle — big meals, hard workouts and bright rooms "
                "all tell the body it's earlier than it is."
            ),
        },
        {
            "title": "Your sleep window",
            "body": (
                "Consistency beats duration. Rather than chasing more hours, "
                "keep the same window — roughly the same bedtime, the same "
                "wake time — and let the body fill it more fully night by "
                "night. A slightly shorter, steadier window often rests "
                "better than a long, drifting one."
            ),
        },
        {
            "title": "Keeping it",
            "body": (
                "Some nights will still be rough — that's part of sleep, not "
                "a failure of yours. When one comes, return to the anchors: "
                "the same wake time, the wind-down hour, up if you're wide "
                "awake. You know the way back now, and you can walk it as "
                "many times as you need. If hard nights persist for weeks, "
                "talking to a doctor is a kind next step — this program sits "
                "alongside that care, never in place of it."
            ),
        },
    ],
}


async def _ensure_user(db: AsyncSession, email: str, password: str, *, name: str, admin: bool) -> User:
    user = await db.scalar(select(User).where(User.email == email))
    if user:
        return user
    user = User(email=email, hashed_password=hash_password(password), name=name, is_admin=admin)
    user.consent = Consent()
    db.add(user)
    await db.flush()
    await nudges.schedule_default_nudges(db, user)
    logger.info("Seeded %s user: %s", "admin" if admin else "demo", email)
    return user


async def seed(db: AsyncSession) -> None:
    if not settings.seed_demo_data:
        return

    await _ensure_user(db, settings.admin_email, settings.admin_password, name="Admin", admin=True)
    await _ensure_user(db, "pawan@cerebro.app", "demo12345", name="Pawan", admin=False)

    # Additive by title: new catalogue entries reach existing dev DBs on boot,
    # while admin-edited rows are never overwritten.
    existing_titles = set((await db.scalars(select(ContentItem.title))).all())
    added = 0
    for title, subtitle, kind, symbol, duration, premium in _CONTENT:
        if title in existing_titles:
            continue
        db.add(
            ContentItem(
                title=title,
                subtitle=subtitle,
                kind=kind,
                symbol=symbol,
                image_url=_IMG,
                duration_min=duration,
                premium=premium,
                narration_script=_SCRIPTS.get(title, ""),
                day_guides=_DAY_GUIDES.get(title),
            )
        )
        added += 1
    if added:
        logger.info("Seeded %d content items", added)

    # Backfill scripts onto pre-existing seeded rows, but only where the script
    # is still empty — an admin-edited script is never clobbered.
    backfilled = 0
    stale = await db.scalars(
        select(ContentItem).where(
            ContentItem.title.in_(_SCRIPTS.keys()),
            ContentItem.narration_script == "",
        )
    )
    for item in stale:
        item.narration_script = _SCRIPTS[item.title]
        backfilled += 1
    if backfilled:
        logger.info("Backfilled narration scripts on %d content items", backfilled)

    # Same mechanic for per-day guides: fill only where still NULL, so an
    # admin-curated day list is never clobbered by a reboot.
    guided = 0
    unguided = await db.scalars(
        select(ContentItem).where(
            ContentItem.title.in_(_DAY_GUIDES.keys()),
            ContentItem.day_guides.is_(None),
        )
    )
    for item in unguided:
        item.day_guides = _DAY_GUIDES[item.title]
        guided += 1
    if guided:
        logger.info("Backfilled day guides on %d content items", guided)

    await db.commit()
