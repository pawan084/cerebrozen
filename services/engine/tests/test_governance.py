"""The AI-governance attestation — the named non-decisional control + AI inventory.

The attestation is only worth publishing if it (a) cannot drift from the flow it claims to
describe, (b) reflects the deployment's real config rather than a hopeful constant, and
(c) never asserts a certification the product doesn't hold. Each is a test here.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from app import governance
from app.llm.prompts import STAGE_SHEET
from app.main import create_app


def test_inventory_covers_every_agent_in_the_flow():
    """Drift guard. The inventory is built from STAGE_SHEET, so a new agent added to the
    registry appears here automatically — but if that ever stops being true, this fails
    rather than letting the flow carry an undocumented agent."""
    inventoried = {row["agent"] for row in governance.ai_inventory()}
    assert inventoried == set(STAGE_SHEET), (
        "AI inventory and the live agent registry diverged: "
        f"missing={set(STAGE_SHEET) - inventoried}, extra={inventoried - set(STAGE_SHEET)}"
    )


def test_no_agent_is_decisional():
    """The core claim, checked per row: nothing this system runs is an input to an
    employment decision."""
    assert all(row["decisional"] is False for row in governance.ai_inventory())


def test_attestation_reflects_the_regulated_flags(monkeypatch):
    """The attestation must report the deployment's ACTUAL posture, both ways.

    Flags are patched directly rather than reloading the config module — reloading mutates
    shared global state that later tests (mood/ic_profile persistence) depend on, which is
    exactly the kind of cross-test pollution a reload causes. `monkeypatch.setattr` restores
    cleanly at teardown."""
    import app.config as config

    # Shipped default: both off → fully regulated.
    monkeypatch.setattr(config, "EMOTION_CAPTURE_ENABLED", False)
    monkeypatch.setattr(config, "PERSON_SCORING_ENABLED", False)
    rw = governance.attestation()["regulated_workplace"]
    assert rw == {
        "fully_regulated": True,
        "emotion_inference": False,
        "durable_person_scoring": False,
        "reference": rw["reference"],
    }

    # Opted in (contract-level decision): the attestation must say so, not hide it.
    monkeypatch.setattr(config, "EMOTION_CAPTURE_ENABLED", True)
    rw2 = governance.attestation()["regulated_workplace"]
    assert rw2["fully_regulated"] is False
    assert rw2["emotion_inference"] is True
    # The inventory row for the mood agent must track the live flag too.
    mood = next(r for r in governance.ai_inventory() if r["agent"] == "feedback_mood_capture_agent")
    assert mood["active"] is True


def test_certifications_are_reported_honestly():
    """None held, none claimed — the attestation must not quietly assert one."""
    certs = governance.attestation()["certifications"]
    assert certs == {"soc2": False, "iso27001": False, "iso42001": False}


def test_attestation_is_content_free():
    """A governance document that leaked a user would be self-defeating. The object is a
    fixed set of system-describing keys; assert no free-text user field sneaks in."""
    att = governance.attestation()
    assert att["spec"] == governance.GOVERNANCE_SPEC
    assert att["non_decisional"]["attested"] is True
    # top-level shape is stable and about the SYSTEM, never a person
    assert set(att) == {
        "spec", "brand", "generated_from", "non_decisional", "non_companion",
        "regulated_workplace", "data_boundary", "certifications", "ai_inventory",
        "not_legal_advice",
    }


def test_the_non_companion_attestation_names_mechanisms_that_actually_exist():
    """#74. "We're a coach, not a companion" is a positioning statement until a reviewer
    can inspect the control. Each mechanism the attestation cites is imported here — if one
    is renamed or deleted, this fails rather than leaving the document asserting a control
    the service no longer has."""
    from app.graph.guardrails import NON_COMPANION
    from app.safety import boundaries

    field = governance.attestation()["non_companion"]
    assert field["attested"] is True
    assert "SB243" in field["reference"]

    enforced = " ".join(field["enforced_by"])
    assert "guardrails.py::NON_COMPANION" in enforced and NON_COMPANION
    assert "safety/boundaries.py" in enforced and boundaries.block_for("are you human")
    assert "cerebrozen_boundary_prompted_total" in enforced


def test_endpoint_is_public_and_returns_the_attestation():
    """It must answer without a token — a reviewer fetches it like /health."""
    client = TestClient(create_app())
    r = client.get("/v1/governance")
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["spec"] == governance.GOVERNANCE_SPEC
    assert body["data_boundary"]["content_exposed_to_employer"] is False
    assert len(body["ai_inventory"]) == len(STAGE_SHEET)
