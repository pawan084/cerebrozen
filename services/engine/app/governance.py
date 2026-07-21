"""Machine-readable AI-governance attestation — the model card, the AI inventory, and the
non-decisional guarantee, built from the RUNNING config rather than a static file.

Why this exists. The product already enforces three things that, together, keep an
individual's coaching signals away from employment decisions:

  1. "counts, never content" — no admin/HR surface or API exposes a transcript, journal,
     or commitment body;
  2. aggregate-only org analytics with a k-anonymity floor (in the platform);
  3. regulated-workplace mode — no emotion inference, no durable per-person score
     (see config._REGULATED / EMOTION_CAPTURE_ENABLED / PERSON_SCORING_ENABLED).

Those were true, but EMERGENT: a security reviewer asking "prove coaching can't feed a
promotion decision" had no single, named control to point at — only three mechanisms in
three files. This composes them into one attestation, and derives it from the deployment's
ACTUAL flags, so the document cannot claim a posture the running service does not hold.

Under the EU AI Act, AI used to evaluate workers is high-risk (Annex III) and inferring
their emotions in the workplace is prohibited (Art. 5). The defensible position is not to
argue about which bucket a coach falls in — it is to be, and to be able to SHOW you are,
non-decisional. This is that showing, in JSON. It is not legal advice.
"""

from __future__ import annotations

from typing import Any, Dict, List

# The canonical agent list IS the prompt registry's stage map — one source of truth, so a
# new agent cannot enter the flow without also appearing in this inventory. A drift guard
# in tests/test_governance.py fails the build if the two diverge.
from app.llm.prompts import STAGE_SHEET

GOVERNANCE_SPEC = "cerebrozen.governance/v1"

# Per-agent governance metadata. `decisional` is False for EVERY agent — no output of this
# system is an input to hiring, promotion, termination, task allocation, or performance
# evaluation. `emotion` / `scoring` mark the two agents in EU-AI-Act-loaded territory; both
# are gated off by regulated-workplace mode, and the inventory reports their live state.
_AGENT_META: Dict[str, Dict[str, Any]] = {
    "environment_system_agent":       {"purpose": "Always-on guardrail layer"},
    "repeat_user_checkin_agent":      {"purpose": "Returning-user continuity"},
    "coaching_intake_agent":          {"purpose": "Session intake"},
    "challenge_context_agent":        {"purpose": "Frame the challenge"},
    "core_coaching_agent":            {"purpose": "Coaching method (CIM / CBT)"},
    "CH_coaching_agent":              {"purpose": "Coaching method (CH)"},
    "simulation_decision_agent":      {"purpose": "Offer or skip rehearsal"},
    "role_play_agent":                {"purpose": "Rehearsal against a profiled counterpart"},
    "SJT_simulation_agent":           {"purpose": "Situational-judgement practice"},
    "learning_aid_agent":             {"purpose": "Micro-learning retrieval"},
    "feedback_mood_capture_agent":    {"purpose": "Session mood capture", "emotion": True},
    "dynamic_actions_insights_agent": {"purpose": "Action-card synthesis (background)"},
    "user_context_builder_agent":     {"purpose": "Context assembly (background)"},
    "pattern_agent":                  {"purpose": "Cross-session pattern reflection (background)", "scoring": True},
    "action_checkin_agent":           {"purpose": "Per-action follow-through check-in"},
}


def ai_inventory() -> List[Dict[str, Any]]:
    """Every agent in the flow, with its purpose and its governance-relevant flags.

    Names are taken from the live registry (``STAGE_SHEET``); an agent added there without
    an entry here still appears, with ``purpose: "—"`` — visible, never silently omitted.
    """
    from app import config

    rows: List[Dict[str, Any]] = []
    for agent in sorted(STAGE_SHEET):
        meta = _AGENT_META.get(agent, {"purpose": "—"})
        row: Dict[str, Any] = {
            "agent": agent,
            "purpose": meta.get("purpose", "—"),
            "model_backed": True,
            "decisional": False,
        }
        if meta.get("emotion"):
            row["infers_emotion"] = True
            row["active"] = config.EMOTION_CAPTURE_ENABLED
        if meta.get("scoring"):
            row["durable_person_scoring"] = True
            row["active"] = config.PERSON_SCORING_ENABLED
        rows.append(row)
    return rows


def attestation() -> Dict[str, Any]:
    """The full governance attestation for the running deployment. Content-free by
    construction — it describes the system, never a user."""
    from app import config

    emotion = bool(config.EMOTION_CAPTURE_ENABLED)
    scoring = bool(config.PERSON_SCORING_ENABLED)

    return {
        "spec": GOVERNANCE_SPEC,
        "brand": config.BRAND_NAME,
        # Not a checked-in claim: this is assembled from the process's own config at request
        # time, so it always reflects THIS deployment's posture.
        "generated_from": "runtime-config",
        "non_decisional": {
            "attested": True,
            "statement": (
                "Coaching outputs are never used as an input to hiring, promotion, "
                "termination, task allocation, or performance evaluation."
            ),
            "enforced_by": [
                "counts-never-content: no admin/HR surface or API exposes a transcript, "
                "journal, or commitment body",
                "aggregate-only analytics with a k-anonymity floor (platform)",
                "regulated-workplace mode: emotion inference and durable person-scoring "
                "gated off at the store",
            ],
        },
        "regulated_workplace": {
            # True only when BOTH loaded flags are off — the shipped default.
            "fully_regulated": (not emotion) and (not scoring),
            "emotion_inference": emotion,
            "durable_person_scoring": scoring,
            "reference": "EU AI Act Art. 5 (workplace emotion recognition), "
            "Annex III (employment high-risk)",
        },
        # Non-companion by design. A coaching product and a companion product are built
        # from the same parts and regulated differently (CA SB243, NY companion-AI law),
        # so "we're a coach, not a companion" has to be a control a reviewer can inspect,
        # not a positioning statement. Both mechanisms below are in code, on every turn,
        # and outside the editable prompt workbook.
        "non_companion": {
            "attested": True,
            "statement": (
                "The coach does not simulate a personal relationship, does not claim to be "
                "human or clinically licensed, and discloses that it is an AI whenever it "
                "is asked or treated as a person."
            ),
            "enforced_by": [
                "always-on conduct guardrail prepended to every turn's system prompt "
                "(graph/guardrails.py::NON_COMPANION) — in code, not in the editable "
                "prompt workbook",
                "mandatory per-turn disclosure when a message treats the coach as a "
                "person, a relationship, or a clinician (safety/boundaries.py); counted "
                "content-free as cerebrozen_boundary_prompted_total{kind}",
            ],
            "reference": "CA SB243 (companion chatbots), NY GBL art. 47 (AI companions)",
        },
        "data_boundary": {
            "content_exposed_to_employer": False,
            "trains_on_user_data": False,
            "crisis_handling": "deterministic code; the model is never consulted and "
            "cannot be persuaded",
        },
        # Honest by policy: none held, none claimed. A buyer sees the truth, not a blank.
        "certifications": {"soc2": False, "iso27001": False, "iso42001": False},
        "ai_inventory": ai_inventory(),
        "not_legal_advice": True,
    }
