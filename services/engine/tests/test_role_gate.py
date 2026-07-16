"""Operator surfaces require the internal_admin role.

WHY THIS EXISTS. The engine shipped with no role checks at all — `require_auth`'s own
docstring said so: "no role or sender checks, per design". That was a straight inheritance
from a single-tenant reference where every caller *was* the operator. It stopped being true
the moment this became multi-tenant, and an e2e run against the composed stack found what
it cost:

  * a plain `user` — a rank-and-file employee at any customer — could
    GET /v1/prompts/download and walk away with the entire coaching workbook;
  * any authenticated token could PUT /v1/prompts/{stage}, rewriting or DISABLING a
    coaching agent for EVERY tenant, because the workbook is global, not org-scoped;
  * POST /v1/prompts/upload could replace the whole file.

The workbook is also the IP whose provenance docs/LICENSING.md is asking counsel about, so
"any employee can download it" was the wrong answer twice over.

The other half of the contract matters just as much: helplines must stay open to everyone.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app import config

OPERATOR_GETS = [
    "/v1/prompts",
    "/v1/prompts/validate",
    "/v1/prompts/checksum",
    "/v1/prompts/download",
    "/v1/prompts/core_coaching_agent",
    "/v1/graph",
    "/v1/graph/mermaid",
    "/v1/agents",
    "/v1/nudges",
    "/v1/safety/escalations",
]


@pytest.fixture
def authed(monkeypatch):
    """Auth ON with a known secret, so role claims are actually read.

    conftest sets AUTH_DEV_BYPASS=true for the whole suite, which disables auth entirely —
    so a role test that only patched config would assert against an open door and pass for
    the wrong reason. Same shape as test_wellness.py's `authed`: clear the env flag AND
    leave a dev ENV, or auth_enabled() short-circuits.
    """
    secret = "dGVzdC1zZWNyZXQtZm9yLXJvbGUtZ2F0ZS10ZXN0cw=="
    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", secret)
    return secret


def _token(secret: str, role: str, org: str = "acme", sub: str = "u1") -> str:
    import jwt

    return jwt.encode(
        {"sub": sub, "org_id": org, "role": role, "user": {"username": sub}},
        secret,
        algorithm=config.JWT_ALGORITHM,
    )


def _client():
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def _h(tok: str) -> dict:
    return {"Authorization": f"Bearer {tok}"}


# ── the hole that was open ───────────────────────────────────────────────────


@pytest.mark.parametrize("path", OPERATOR_GETS)
@pytest.mark.parametrize("role", ["user", "org_admin"])
def test_a_non_operator_cannot_read_an_operator_surface(authed, path, role):
    r = _client().get(path, headers=_h(_token(authed, role)))
    assert r.status_code == 403, f"{role} read {path} ({r.status_code})"


def test_an_employee_cannot_download_the_coaching_workbook(authed):
    # The sharpest instance: the whole .xlsx, over one GET, to anyone with an account.
    r = _client().get("/v1/prompts/download", headers=_h(_token(authed, "user")))
    assert r.status_code == 403


@pytest.mark.parametrize("role", ["user", "org_admin"])
def test_a_non_operator_cannot_rewrite_the_global_workbook(authed, role):
    # The worst instance: the workbook is GLOBAL, so one customer's HR could have
    # disabled a coaching agent for every other customer.
    r = _client().put(
        "/v1/prompts/core_coaching_agent",
        json={"enabled": False},
        headers=_h(_token(authed, role)),
    )
    assert r.status_code == 403, f"{role} wrote the workbook ({r.status_code})"


@pytest.mark.parametrize("role", ["user", "org_admin"])
def test_a_non_operator_cannot_replace_the_workbook_file(authed, role):
    r = _client().post(
        "/v1/prompts/upload",
        files={"file": ("x.xlsx", b"not-a-workbook", "application/vnd.ms-excel")},
        headers=_h(_token(authed, role)),
    )
    assert r.status_code == 403


@pytest.mark.parametrize("role", ["user", "org_admin"])
def test_a_non_operator_cannot_spend_money_on_the_console_runner(authed, role):
    # These call the live model. An open door here is a billing hole as well as an IP one.
    c = _client()
    for r in (
        c.post("/v1/console/run", json={"system": "x", "user": "y"}, headers=_h(_token(authed, role))),
        c.post("/v1/agents/core_coaching_agent/run", json={"input": "y"}, headers=_h(_token(authed, role))),
    ):
        assert r.status_code == 403


def test_the_operator_still_gets_in(authed):
    r = _client().get("/v1/agents", headers=_h(_token(authed, "internal_admin")))
    assert r.status_code == 200, r.text


# ── 403 vs 401: a role refusal is final ──────────────────────────────────────


def test_a_role_refusal_is_403_not_401(authed):
    """401 tells a client its token is stale and invites a refresh-and-retry loop that can
    never succeed — the Android session burns a refresh on every 401. A role refusal is a
    real answer; it must not masquerade as an expiry."""
    r = _client().get("/v1/prompts", headers=_h(_token(authed, "user")))
    assert r.status_code == 403
    assert r.json()["message"]


def test_no_token_is_still_401(authed):
    assert _client().get("/v1/prompts").status_code == 401


def test_a_forged_token_is_401_not_403(authed):
    import jwt

    forged = jwt.encode({"sub": "x", "org_id": "acme", "role": "internal_admin"},
                        "the-wrong-secret", algorithm=config.JWT_ALGORITHM)
    assert _client().get("/v1/prompts", headers=_h(forged)).status_code == 401


def test_claiming_internal_admin_without_the_right_secret_gets_nowhere(authed):
    # The role is only worth anything because it is signed.
    import jwt

    forged = jwt.encode({"sub": "x", "org_id": "acme", "role": "internal_admin"},
                        "attacker-secret", algorithm=config.JWT_ALGORITHM)
    assert _client().get("/v1/prompts/download", headers=_h(forged)).status_code == 401


# ── what must NOT be gated ───────────────────────────────────────────────────


@pytest.mark.parametrize("role", ["user", "org_admin", "internal_admin"])
def test_helplines_stay_open_to_every_authenticated_user(authed, role):
    """The one route where a role gate would be catastrophic: it backs a crisis screen.
    Denying someone in crisis a phone number because they aren't staff is the worst thing
    this codebase could do, so it is pinned separately from the gate above."""
    r = _client().get("/v1/safety/helplines?region=GB", headers=_h(_token(authed, role)))
    assert r.status_code == 200, f"{role} was refused a helpline ({r.status_code})"
    assert r.json()["helplines"]


def test_a_member_can_still_read_their_own_session_stage(authed):
    """Ownership, not role, is the right control for a person's own content — these are
    scoped by _assert_owner (404 unless it's yours). Gating them to internal_admin would
    have broken the app's history panel, which is exactly what a blanket change did once."""
    r = _client().get("/v1/sessions/nope/stage", headers=_h(_token(authed, "user")))
    assert r.status_code != 403, "a member must not be role-refused their own session"
