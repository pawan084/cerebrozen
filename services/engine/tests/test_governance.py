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


def test_regulated_is_the_default(monkeypatch):
    """Unset env → fully regulated: no emotion inference, no durable person-score. This is
    the shipped posture and the attestation must report it truthfully."""
    for var in ("CEREBROZEN_REGULATED_WORKPLACE", "CEREBROZEN_EMOTION_CAPTURE",
                "CEREBROZEN_PERSON_SCORING"):
        monkeypatch.delenv(var, raising=False)
    import importlib

    import app.config as config
    importlib.reload(config)
    try:
        att = governance.attestation()
        rw = att["regulated_workplace"]
        assert rw["fully_regulated"] is True
        assert rw["emotion_inference"] is False
        assert rw["durable_person_scoring"] is False
    finally:
        importlib.reload(config)  # restore for the rest of the suite


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
        "spec", "brand", "generated_from", "non_decisional", "regulated_workplace",
        "data_boundary", "certifications", "ai_inventory", "not_legal_advice",
    }


def test_endpoint_is_public_and_returns_the_attestation():
    """It must answer without a token — a reviewer fetches it like /health."""
    client = TestClient(create_app())
    r = client.get("/v1/governance")
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["spec"] == governance.GOVERNANCE_SPEC
    assert body["data_boundary"]["content_exposed_to_employer"] is False
    assert len(body["ai_inventory"]) == len(STAGE_SHEET)
