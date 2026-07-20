"""18+ age gate (B2C). The engine refuses a coaching turn to a token whose signed `adult`
claim is explicitly False, so the onboarding age gate can't be bypassed by calling the API
directly. An ABSENT claim (auth off / dev, a pre-rollout token, or a B2B-by-contract token)
passes — the same 'absence is not refusal' rule the plan gate uses."""

import pytest

from app.auth.dependencies import require_adult


def test_require_adult_blocks_an_unattested_token():
    with pytest.raises(Exception) as ei:
        require_adult({"adult": False})
    assert getattr(ei.value, "status_code", None) == 403


def test_require_adult_allows_attested_and_absent_claims():
    assert require_adult({"adult": True}) == {"adult": True}
    assert require_adult({}) == {}                          # auth off / pre-rollout token
    assert require_adult({"sub": "u1"}) == {"sub": "u1"}    # non-consumer token (no claim)


def test_a_coaching_turn_requires_the_18_plus_attestation_end_to_end(monkeypatch):
    """#30: a valid token that has NOT attested 18+ is refused (403) at BOTH the start and
    turn endpoints — server-side, before any coaching runs. An attested (or claim-less)
    token clears the gate."""
    import jwt
    from fastapi.testclient import TestClient

    from app import config
    from app.main import create_app

    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", "s3cret")
    client = TestClient(create_app(), raise_server_exceptions=False)

    def tok(**claims):
        raw = jwt.encode(
            {"user": {"username": "u1"}, "org_id": "default", **claims},
            "s3cret", algorithm=config.JWT_ALGORITHM,
        )
        return {"Authorization": f"Bearer {raw}"}

    # Not attested → blocked before any coaching, at start AND turn.
    assert client.post("/v1/sessions/start", json={}, headers=tok(adult=False)).status_code == 403
    assert client.post("/v1/sessions/s1/turn", json={}, headers=tok(adult=False)).status_code == 403
    # Attested, and a token that predates the claim, both clear the gate (not a 403).
    assert client.post("/v1/sessions/s1/turn", json={}, headers=tok(adult=True)).status_code != 403
    assert client.post("/v1/sessions/s1/turn", json={}, headers=tok()).status_code != 403
