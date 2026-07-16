"""At-rest encryption is an ATTESTED datastore concern, surfaced honestly.

The engine does no app-layer field crypto (that would add a native crypto dep the
project avoids and break queryability). At-rest encryption lives at the datastore
(DB TDE / encrypted volume); the app only carries the operator's attestation so a
deployment can't be quietly assumed encrypted. These tests pin the parse, the
deployed-env boot warning, and the /health surface.
"""

from fastapi.testclient import TestClient

from app import config


def _client():
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


# ── the attestation parse ────────────────────────────────────────────────────

def test_parse_recognises_true_and_false_forms():
    for raw in ("true", "TRUE", "1", "yes", "on"):
        assert config._parse_bool_attestation(raw) is True
    for raw in ("false", "0", "no", "off"):
        assert config._parse_bool_attestation(raw) is False


def test_parse_unknown_is_none():
    for raw in ("", "   ", "maybe", "encrypted", None):
        assert config._parse_bool_attestation(raw) is None


# ── the deployed-env boot warning ────────────────────────────────────────────

def test_dev_envs_are_never_warned():
    for env in ("local", "dev", "development", "test", "ci"):
        assert config._datastore_attestation_warning(env, None) is None
        assert config._datastore_attestation_warning(env, False) is None


def test_a_deployed_env_without_attestation_is_warned():
    assert config._datastore_attestation_warning("production", None)   # unknown
    assert config._datastore_attestation_warning("production", False)  # declared off


def test_a_deployed_env_that_attests_true_is_not_warned():
    assert config._datastore_attestation_warning("production", True) is None


# ── the /health surface ──────────────────────────────────────────────────────

def test_health_reports_unknown_by_default():
    # The test env sets no CEREBROZEN_DATASTORE_ENCRYPTED → unknown, never assumed on.
    body = _client().get("/health").json()
    assert body["storage"]["encrypted"] == "unknown"


def test_health_reports_the_attested_value(monkeypatch):
    monkeypatch.setattr(config, "DATASTORE_ENCRYPTED", True)
    assert _client().get("/health").json()["storage"]["encrypted"] == "true"
    monkeypatch.setattr(config, "DATASTORE_ENCRYPTED", False)
    assert _client().get("/health").json()["storage"]["encrypted"] == "false"
