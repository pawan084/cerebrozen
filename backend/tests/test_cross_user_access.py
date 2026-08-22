"""Every route that takes a row id, called by somebody else's account (WC-74).

The organisation tier of this attack lives in `test_org_tenant_isolation.py`.
This is the same bug class one tier down, and the blast radius is worse: a
tenant here is a *person*, and the rows are journal entries, mood logs and
memories — the most private data the product holds.

The shape is always the same. A route takes `{something_id}`, loads it with
`db.get(Model, id)` — which is scoped by nothing at all — and then has to
remember to compare the row's `user_id` to the caller's. Every one of those
comparisons is currently present. That is precisely why they need tests: a
present check is invisible, and deleting one changes no test's outcome unless a
test exists that calls the route as the wrong user.

So this file is a table rather than prose. Adding a route to `ATTACKS` costs one
line, which is the point — the next id-taking route should be cheaper to cover
than to skip.

Two conventions are asserted rather than assumed:

* **404, never 403.** A 403 confirms the row exists, which turns every refusal
  into an existence oracle. `_owned_memory` in routes/users.py documents this
  reasoning; the table holds every other route to the same standard.
* **The victim's row is unchanged afterwards.** A 404 that still performed the
  write would be worse than a 200, because nothing would ever surface it.
"""

from __future__ import annotations

import uuid
from datetime import date, timedelta

import pytest
from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.consent import Consent
from app.models.habit import Goal, Habit
from app.models.intervention import InterventionRecommendation
from app.models.journal import JournalEntry
from app.models.memory import ContextMemory
from app.models.mood import MoodLog
from app.models.plan import Plan, PlanStep
from app.models.recommendation import Recommendation
from app.models.user import User

VICTIM_SECRET = "SECRET-PRIVATE-PROSE-DO-NOT-LEAK"


async def _signup(client, prefix: str) -> tuple[str, str]:
    email = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": email, "password": "password123", "name": "T"}
    )
    assert r.status_code == 201, r.text
    return email, r.json()["access_token"]


@pytest.fixture
async def victim_and_attacker(client):
    """One row of every id-addressable kind, owned by the victim.

    The client is left signed in as the ATTACKER — a perfectly ordinary account
    with no relationship to the victim, which is the realistic case.
    """
    victim_email, _ = await _signup(client, "victim")
    attacker_email, attacker_token = await _signup(client, "attacker")

    async with SessionLocal() as db:
        victim = await db.scalar(select(User).where(User.email == victim_email))
        attacker = await db.scalar(select(User).where(User.email == attacker_email))

        # The attacker consents to ai_memory FOR THEMSELVES. Without this the
        # memory routes 403 on the attacker's own consent gate, which runs
        # before the ownership check -- so the attack would be turned away for
        # a reason that has nothing to do with the victim, and the test would
        # pass while `_owned_memory` was never reached. Found by writing it the
        # naive way first and reading why it went red.
        #
        # Signup already writes a Consent row (user_id is unique), so this
        # flips the existing one rather than inserting a second.
        attacker_consent = await db.scalar(
            select(Consent).where(Consent.user_id == attacker.id)
        )
        attacker_consent.ai_memory = True

        entry = JournalEntry(user_id=victim.id, title="Private", body=VICTIM_SECRET)
        mood = MoodLog(user_id=victim.id, mood="Low", intensity=1, note=VICTIM_SECRET)
        goal = Goal(user_id=victim.id, title=VICTIM_SECRET)
        habit = Habit(user_id=victim.id, title=VICTIM_SECRET)
        memory = ContextMemory(user_id=victim.id, body=VICTIM_SECRET, source="manual")
        plan = Plan(user_id=victim.id, title=VICTIM_SECRET)
        rec = Recommendation(user_id=victim.id, practice_slug="box-breathing")
        offer = InterventionRecommendation(
            user_id=victim.id, rule_slug="low_mood_run", reason=VICTIM_SECRET
        )
        db.add_all([entry, mood, goal, habit, memory, plan, rec, offer])
        await db.flush()

        step = PlanStep(plan_id=plan.id, title=VICTIM_SECRET)
        db.add(step)
        await db.flush()

        ids = {
            "entry": str(entry.id), "mood": str(mood.id), "goal": str(goal.id),
            "habit": str(habit.id), "memory": str(memory.id), "step": str(step.id),
            "rec": str(rec.id), "offer": str(offer.id),
        }
        await db.commit()

    client.headers["Authorization"] = f"Bearer {attacker_token}"
    return ids


#: (method, path template, json body) — one line per id-addressable route.
ATTACKS = [
    ("PUT",    "/journal/{entry}",            {"title": "t", "body": "b", "tags": [], "symbol": "book"}),
    ("DELETE", "/journal/{entry}",            None),
    ("DELETE", "/moods/{mood}",               None),
    ("PATCH",  "/plans/steps/{step}",         {"done": True}),
    ("PATCH",  "/goals/{goal}",               {"title": "hijacked"}),
    ("DELETE", "/goals/{goal}",               None),
    ("POST",   "/goals/{goal}/decompose",     None),
    ("PATCH",  "/habits/{habit}",             {"title": "hijacked"}),
    ("DELETE", "/habits/{habit}",             None),
    ("POST",   "/habits/{habit}/complete",    None),
    ("DELETE", "/habits/{habit}/complete",    None),
    ("PATCH",  "/users/me/memory/{memory}",   {"body": "hijacked"}),
    ("DELETE", "/users/me/memory/{memory}",   None),
    ("POST",   "/interventions/{offer}/accept",   None),
    ("POST",   "/interventions/{offer}/dismiss",  None),
    ("POST",   "/interventions/{offer}/complete", None),
    ("POST",   "/recommendations/{rec}/accept",   None),
    ("POST",   "/recommendations/{rec}/dismiss",  None),
]


@pytest.mark.parametrize(
    "method,template,body", ATTACKS, ids=[f"{m} {p}" for m, p, _ in ATTACKS]
)
@pytest.mark.asyncio
async def test_another_users_row_is_not_reachable(
    client, victim_and_attacker, method, template, body
):
    path = template.format(**victim_and_attacker)
    r = await client.request(method, path, json=body)
    assert r.status_code == 404, f"{method} {path} -> {r.status_code}: {r.text[:200]}"
    assert VICTIM_SECRET not in r.text, f"{method} {path} echoed the victim's content"


@pytest.mark.asyncio
async def test_a_refused_write_did_not_happen_anyway(client, victim_and_attacker):
    """404 is only half the guarantee; the row must also be untouched.

    A route that mutates and *then* fails an ownership check returns exactly the
    same response as one that refused properly. Nothing but reading the rows
    back afterwards tells the two apart.
    """
    for method, template, body in ATTACKS:
        r = await client.request(method, template.format(**victim_and_attacker), json=body)
        assert r.status_code == 404

    async with SessionLocal() as db:
        ids = victim_and_attacker
        entry = await db.get(JournalEntry, uuid.UUID(ids["entry"]))
        goal = await db.get(Goal, uuid.UUID(ids["goal"]))
        habit = await db.get(Habit, uuid.UUID(ids["habit"]))
        memory = await db.get(ContextMemory, uuid.UUID(ids["memory"]))
        step = await db.get(PlanStep, uuid.UUID(ids["step"]))
        rec = await db.get(Recommendation, uuid.UUID(ids["rec"]))
        offer = await db.get(InterventionRecommendation, uuid.UUID(ids["offer"]))
        mood = await db.get(MoodLog, uuid.UUID(ids["mood"]))

    assert entry is not None and entry.body == VICTIM_SECRET, "journal entry deleted or edited"
    assert mood is not None, "another user deleted a mood log"
    assert goal is not None and goal.title == VICTIM_SECRET, "goal deleted or renamed"
    assert habit is not None and habit.title == VICTIM_SECRET, "habit deleted or renamed"
    assert memory is not None and memory.body == VICTIM_SECRET, "memory was rewritten"
    assert step is not None and step.done is False, "another user ticked off a plan step"
    assert rec is not None and rec.status == "pending", "another user resolved a recommendation"
    assert offer.accepted_at is None and offer.dismissed_at is None, "an offer was resolved"


@pytest.mark.asyncio
async def test_a_real_id_and_an_invented_one_are_indistinguishable(
    client, victim_and_attacker
):
    """Otherwise the refusal itself enumerates other people's rows."""
    invented = {key: str(uuid.uuid4()) for key in victim_and_attacker}
    for method, template, body in ATTACKS:
        real_r = await client.request(
            method, template.format(**victim_and_attacker), json=body
        )
        invented_r = await client.request(method, template.format(**invented), json=body)
        assert real_r.status_code == invented_r.status_code == 404, f"{method} {template}"
        assert real_r.json() == invented_r.json(), (
            f"{method} {template}: a row that exists answers differently from one "
            "that does not, which is an existence oracle"
        )


@pytest.mark.asyncio
async def test_the_date_keyed_route_cannot_name_another_user(client, victim_and_attacker):
    """`DELETE /sleep/{night}` is the one route keyed by a date, not a row id.

    Recorded here so the absence reads as a finding rather than an oversight: a
    date is not a handle to anyone else's row, so the attacker deleting "their
    own" night touches only their own empty history.
    """
    r = await client.delete(f"/sleep/{date(2026, 1, 1).isoformat()}")
    assert r.status_code in (200, 204, 404), r.text


class TestTheRuleEngineIsPerPerson:
    """Two queries in services/interventions.py that scope by user inside the
    WHERE clause rather than in a visible `if`.

    Both survived the first mutation sweep of this file: deleting
    `InterventionRecommendation.user_id == user.id` from either one broke no
    test at all. Neither is reachable by naming an id — which is why the table
    above misses them — but both are reached by `GET /interventions/active`,
    and one of them returns another person's row.
    """

    @pytest.mark.asyncio
    async def test_the_open_offer_on_the_table_is_never_someone_elses(
        self, client, victim_and_attacker
    ):
        """`open_recommendation` unscoped hands the victim's offer to a stranger.

        Not merely an id: the payload carries `reason` (prose about what the app
        noticed) and `state_snapshot` (the numbers behind it). This is a read of
        another person's inferred state, served by a route every client polls.
        """
        r = await client.get("/interventions/active")
        assert r.status_code == 200, r.text
        assert VICTIM_SECRET not in r.text, "another user's offer was served"
        body = r.json()
        assert body is None or body["id"] != victim_and_attacker["offer"]

    @pytest.mark.asyncio
    async def test_one_persons_cooldown_does_not_silence_everybody(self, client):
        """`_in_cooldown` unscoped makes one user's recent offer global.

        No data leaks, so it would never look like a security bug — it looks
        like the feature quietly not working, for everyone, forever, with the
        blast radius growing as the user base does. Exactly the failure that
        never gets reported because nobody can see it happening.
        """
        victim_email, _ = await _signup(client, "cooldown-victim")
        attacker_email, attacker_token = await _signup(client, "cooldown-other")

        async with SessionLocal() as db:
            victim = await db.scalar(select(User).where(User.email == victim_email))
            other = await db.scalar(select(User).where(User.email == attacker_email))

            # The victim was offered low_mood_run just now, so THEY are in
            # cooldown for it.
            db.add(InterventionRecommendation(
                user_id=victim.id, rule_slug="low_mood_run", dismissed_at=utcnow(),
            ))

            # The other user independently qualifies: mood_history consent, and
            # three distinct low days inside the last week.
            consent = await db.scalar(select(Consent).where(Consent.user_id == other.id))
            consent.mood_history = True
            now = utcnow()
            for day in range(3):
                db.add(MoodLog(
                    user_id=other.id, mood="Low", intensity=1,
                    created_at=now - timedelta(days=day),
                ))
            await db.commit()

        client.headers["Authorization"] = f"Bearer {attacker_token}"
        r = await client.get("/interventions/active")
        assert r.status_code == 200, r.text
        body = r.json()
        assert body is not None, (
            "the rule fired for this user, but somebody else's cooldown "
            "suppressed it"
        )
        assert body["rule_slug"] == "low_mood_run"
