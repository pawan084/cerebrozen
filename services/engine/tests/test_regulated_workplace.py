"""Regulated-workplace mode — the EU AI Act switch.

Two things this product does are, in an EMPLOYMENT context, legally loaded:

  1. It **infers emotions**. `feedback_mood_capture_agent` maps a session onto a canonical
     emotion set and persists it per user.
  2. It **scores the person**. The Coachable Index is 8 dimensions plus a weighted
     `coachability_score`, captured ONCE IN A LIFETIME — a durable rating of a worker,
     computed from a conversation they were told was confidential.

Under the EU AI Act, inferring emotions of a natural person in the workplace is a
**prohibited practice** (Art. 5), and AI in employment / worker management is **high-risk**
(Annex III). A coaching tool sold to an employer that reads its users' emotions and scores
them is not near that line; it is over it.

These tests exist because **a compliance switch nobody tests is not a control, it is a
claim.** The moment we tell a buyer "we can turn that off", they are entitled to ask us to
prove it — and a DPO who has been lied to once will never believe a slide again.

Not legal advice, and no substitute for counsel. What it is: an answerable question.
"""

import subprocess
import sys

import pytest

from app import config


# ── The flag semantics run in a SUBPROCESS, deliberately. ────────────────────
#
# config.py computes these at IMPORT, so testing the env parsing means re-importing it —
# and `importlib.reload(config)` inside a fixture LEAKS: other modules hold references to
# the objects it rebinds, and the next test file inherits the damage. I did exactly that
# and broke 27 unrelated store tests with it. A subprocess cannot pollute anything, which
# is the entire point.

def _flags(**env) -> dict:
    """Import config in a clean process with `env` set, and report the two flags."""
    code = (
        "import json, app.config as c; "
        "print(json.dumps({'emotion': c.EMOTION_CAPTURE_ENABLED, "
        "'scoring': c.PERSON_SCORING_ENABLED}))"
    )
    import json
    import os

    proc = subprocess.run(
        [sys.executable, "-c", code],
        capture_output=True, text=True, cwd=str(pathlib_root()),
        env={**os.environ, "MONGO_DB_URL": "", "POSTGRES_URL": "", **env},
    )
    assert proc.returncode == 0, proc.stderr
    return json.loads(proc.stdout.strip().splitlines()[-1])


def pathlib_root():
    import pathlib

    return pathlib.Path(__file__).resolve().parent.parent


def test_regulated_mode_is_the_default():
    """A new tenant starts SAFE: no emotion inference, no person-scoring. Turning them
    on is a conscious, contract-level decision — not a flag someone forgot to set.
    (Empty string = unset: the conftest baseline pins the suite to "false", so the
    true default is asserted by clearing the variable.)"""
    f = _flags(CEREBROZEN_REGULATED_WORKPLACE="")
    assert f["emotion"] is False and f["scoring"] is False


def test_a_deployment_can_opt_out_of_regulated_mode_explicitly():
    """`CEREBROZEN_REGULATED_WORKPLACE=false` is the conscious opt-out: both features
    return, in one place, on the record."""
    f = _flags(CEREBROZEN_REGULATED_WORKPLACE="false")
    assert f["emotion"] is True and f["scoring"] is True


def test_one_flag_turns_off_everything_in_scope():
    """`CEREBROZEN_REGULATED_WORKPLACE=true` is the whole control. Nobody has to remember a
    list — and a list is exactly the thing somebody gets wrong at 2am under deal pressure."""
    f = _flags(CEREBROZEN_REGULATED_WORKPLACE="true")
    assert f["emotion"] is False and f["scoring"] is False


def test_a_feature_can_still_be_pinned_on_against_regulated_mode():
    """The two flags stay independent, because "regulated" is not one jurisdiction. A tenant
    may need emotion capture off and scoring kept (or the reverse), and an explicit setting
    must beat the umbrella."""
    f = _flags(CEREBROZEN_REGULATED_WORKPLACE="true", CEREBROZEN_PERSON_SCORING="true")
    assert f["emotion"] is False, "the umbrella should still have switched emotion capture off"
    assert f["scoring"] is True, "an explicit setting must beat the umbrella"


# ── Behaviour: patch the flags the code reads at CALL time. No reloads. ──────

@pytest.fixture
def regulated(monkeypatch):
    """A tenant that must not infer emotions or score its people."""
    monkeypatch.setattr(config, "EMOTION_CAPTURE_ENABLED", False)
    monkeypatch.setattr(config, "PERSON_SCORING_ENABLED", False)
    _reset_registry()
    yield config
    _reset_registry()


@pytest.fixture
def unregulated(monkeypatch):
    """The default. The incumbent depends on both features, so they stay ON unless asked."""
    monkeypatch.setattr(config, "EMOTION_CAPTURE_ENABLED", True)
    monkeypatch.setattr(config, "PERSON_SCORING_ENABLED", True)
    _reset_registry()
    yield config
    _reset_registry()


def _reset_registry():
    """The variable registry is a singleton and reads the flag at LOAD, so it has to be
    rebuilt when the flag moves — otherwise the test would assert against a registry that
    was built under the previous tenant's rules."""
    from app.stores.variable_capture_registry import VariableCaptureRegistry

    VariableCaptureRegistry._instance = None


# ── EMOTION: nothing reaches the disk ────────────────────────────────────────

def test_no_emotion_is_persisted_in_regulated_mode(regulated, mongo, agentic_coll, user_id):
    """The guarantee lives in the STORE, deliberately — the last gate before the disk.

    Gating the agent instead would mean the guarantee holds only as long as nobody edits a
    prompt, and prompts here are editable by non-engineers with no code review. Whatever
    happens upstream, nothing is written.
    """
    from app.stores import agentic

    written = agentic.save_mood_capture(
        user_id, "s1",
        {"mapped_emotions": ["anxious", "resentful"], "negative_emotions": ["dread"]},
    )

    assert written is False, "an emotion record was written in a tenant that forbids it"
    doc = agentic_coll.find_one({"user_id": user_id}) or {}
    assert not doc.get("moods"), f"emotions reached the database: {doc.get('moods')!r}"


def test_emotion_capture_still_works_for_a_tenant_that_permits_it(unregulated, mongo,
                                                                  agentic_coll, user_id):
    """The switch must be a switch, not a deletion. The incumbent's product depends on this
    and it has to keep working."""
    from app.stores import agentic

    ok = agentic.save_mood_capture(user_id, "s1", {"mapped_emotions": ["hopeful"]})

    assert ok is True
    doc = agentic_coll.find_one({"user_id": user_id}) or {}
    assert doc.get("moods"), "the default tenant lost its mood capture"


# ── SCORING: the variable is never even registered ───────────────────────────

PERSON_SCORE_VARS = [
    "coachability_score",
    "ci_openness", "ci_accountability", "ci_growth_mindset", "ci_action_bias",
    "ci_honesty", "ci_consistency", "ci_specificity", "ci_reflectiveness",
]


@pytest.mark.parametrize("var", PERSON_SCORE_VARS)
def test_a_durable_person_score_is_never_registered_in_regulated_mode(regulated, var):
    """Suppressed at LOAD, not at write.

    If the variable is never registered, no agent can capture it, no store can persist it,
    and — the part that matters — **no later prompt edit can quietly bring it back.**
    Disabling it in the workbook would be a setting somebody can flip. Refusing to register
    it is a property of the deployment.
    """
    from app.stores.variable_capture_registry import VariableCaptureRegistry

    reg = VariableCaptureRegistry()
    cfg = reg.config(var)

    assert cfg is None or not cfg.capture_enabled, (
        f"{var} is still capturable — this tenant would hold a durable score about a worker"
    )


def test_the_scoring_suppression_names_every_coachable_index_dimension():
    """A prefix match on `ci_` is the kind of shortcut that silently stops covering the day
    someone adds `ci_grit`. The list is explicit, and this test is what makes the list a
    contract rather than a comment."""
    from app.stores.variable_capture_registry import _PERSON_SCORE_VARS

    assert set(PERSON_SCORE_VARS) == set(_PERSON_SCORE_VARS), (
        "the Coachable Index gained or lost a dimension and the regulated-mode gate did not "
        "follow — a worker score would leak into a tenant that forbids it"
    )


def test_ordinary_coaching_variables_are_untouched(regulated):
    """Regulated mode removes the SCORE, not the coaching. A tenant in this mode still gets
    a coach that remembers the session goal and what the person is working on — it simply
    does not keep a rating of them."""
    from app.stores.variable_capture_registry import VariableCaptureRegistry

    reg = VariableCaptureRegistry()
    for keeper in ("userRoleContext", "userMotivations", "coachingNeeds"):
        cfg = reg.config(keeper)
        if cfg is not None:
            assert cfg.capture_enabled, f"{keeper} was suppressed — that is coaching, not scoring"
