"""App-layer tenancy: tenant A must never see tenant B's data.

This is the cross-tenant access class the repo docs promise: direct store
isolation across every engine-owned collection, legacy-document ownership
(pre-tenancy docs belong to the default org and nobody else), Redis key
scoping, and the JWT `org_id` claim contract on the HTTP surface.
"""

import jwt
import pytest

from app import config
from app.request_context import request_id as rid_var
from app.stores import agentic, conversation, dynamic_vars
from app.stores import redis_state as rs
from app.tenancy import DEFAULT_ORG, ctx_org_id, current_org, scoped


@pytest.fixture
def as_org():
    """Switch the active tenant; restores the previous org on teardown."""
    tokens = []

    def _switch(org: str) -> str:
        tokens.append(ctx_org_id.set(org))
        return org

    yield _switch
    for tok in reversed(tokens):
        ctx_org_id.reset(tok)


def _seed_turn(session_id: str, user_id: str, text: str) -> None:
    tok = rid_var.set("t-req")
    try:
        conversation.record_turn(
            session_id=session_id, user_id=user_id, user_message=text, bot_text="noted."
        )
    finally:
        rid_var.reset(tok)


# ── store-level isolation ─────────────────────────────────────────────────────


class TestCrossTenantIsolation:
    def test_transcripts_are_invisible_across_orgs(self, mongo, as_org):
        as_org("acme")
        _seed_turn("s-1", "u1", "hi from acme")

        as_org("globex")
        assert conversation.list_sessions("u1") == []
        assert conversation.get_session("s-1") is None
        assert conversation.has_prior_sessions("u1") is False

        as_org("acme")
        assert [s["session_id"] for s in conversation.list_sessions("u1")] == ["s-1"]

    def test_the_same_session_id_can_exist_in_two_orgs_without_mixing(self, mongo, as_org):
        """The business key is (org, session): a collision on the id alone must
        neither merge transcripts nor leak one org's messages into the other."""
        as_org("acme")
        _seed_turn("s-shared", "ua", "acme secret")
        as_org("globex")
        _seed_turn("s-shared", "ug", "globex secret")

        globex_doc = conversation.get_session("s-shared")
        assert globex_doc is not None and globex_doc["user_id"] == "ug"
        assert "acme secret" not in str(globex_doc)

        as_org("acme")
        acme_doc = conversation.get_session("s-shared")
        assert acme_doc is not None and acme_doc["user_id"] == "ua"
        assert "globex secret" not in str(acme_doc)

    def test_deleting_across_orgs_is_a_noop(self, mongo, as_org):
        as_org("acme")
        _seed_turn("s-2", "u1", "keep me")

        as_org("globex")
        assert conversation.delete_session("s-2", "u1") is False

        as_org("acme")
        assert conversation.get_session("s-2") is not None
        assert conversation.delete_session("s-2", "u1") is True

    def test_actions_are_invisible_and_immutable_across_orgs(self, mongo, as_org):
        as_org("acme")
        added, _ = agentic.append_actions_insights(
            "u1",
            [{"full_text": "Book the feedback conversation", "verb": "Book"}],
            [],
            session_id="s-3",
            agent_name="dynamic_actions_insights_agent",
        )
        action_id = added[0]["action_id"]

        as_org("globex")
        assert agentic.load("u1").get("actions", []) == []
        assert agentic.set_action_status("u1", action_id, "saved") is False

        as_org("acme")
        actions = agentic.load("u1")["actions"]
        assert len(actions) == 1
        assert actions[0].get("status") != "saved", "another org changed our action"

    def test_dynamic_vars_do_not_cross_orgs(self, mongo, as_org):
        as_org("acme")
        assert dynamic_vars.save_session_dynamic_vars(
            "u1", "s-4", {"userGoal": "delegate more"}, stage="intake"
        )

        as_org("globex")
        assert dynamic_vars.read_dynamic_vars("u1") == {}

        as_org("acme")
        assert dynamic_vars.read_dynamic_vars("u1").get("userGoal") == "delegate more"

    def test_redis_session_markers_are_org_scoped(self, as_org):
        as_org("acme")
        rs.mark_session_seen("s-5")
        assert rs.is_session_seen("s-5") is True

        as_org("globex")
        assert rs.is_session_seen("s-5") is False

    def test_legacy_orgless_documents_belong_to_the_default_org_only(self, mongo, as_org):
        """Docs written before tenancy have no org field. The default org still
        reads them (migration compatibility); every other org must not."""
        coll = mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
        coll.insert_one(
            {"session_id": "s-legacy", "user_id": "u1", "messages": [], "updated_at": "2020-01-01"}
        )

        as_org(DEFAULT_ORG)
        assert conversation.get_session("s-legacy") is not None

        as_org("acme")
        assert conversation.get_session("s-legacy") is None


# ── the scoping primitives themselves ────────────────────────────────────────


def test_scoped_is_strict_for_non_default_orgs(as_org):
    as_org("acme")
    assert scoped({"user_id": "u1"}) == {"user_id": "u1", "org_id": "acme"}


def test_scoped_admits_legacy_docs_only_for_the_default_org(as_org):
    as_org(DEFAULT_ORG)
    q = scoped({"user_id": "u1"})
    assert q["user_id"] == "u1"
    assert {"org_id": DEFAULT_ORG} in q["$and"][0]["$or"]
    assert {"org_id": {"$exists": False}} in q["$and"][0]["$or"]


def test_current_org_never_returns_empty(as_org):
    as_org("")
    assert current_org() == DEFAULT_ORG


# ── the JWT claim contract on the HTTP surface ───────────────────────────────


@pytest.fixture
def org_auth_app(monkeypatch):
    """An app with auth ENFORCED, plus a token factory taking an org."""
    from fastapi.testclient import TestClient

    from app.main import create_app

    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", "s3cret")

    def token_for(user_id: str, org: str | None) -> dict:
        payload: dict = {"user": {"username": user_id}}
        if org is not None:
            payload["org_id"] = org
        raw = jwt.encode(payload, "s3cret", algorithm=config.JWT_ALGORITHM)
        return {"Authorization": f"Bearer {raw}"}

    return TestClient(create_app(), raise_server_exceptions=False), token_for


def test_a_token_without_an_org_claim_is_refused(org_auth_app):
    client, token_for = org_auth_app
    response = client.get("/v1/sessions?user_id=u1", headers=token_for("u1", None))
    assert response.status_code == 401


def test_the_org_claim_scopes_what_the_api_returns(org_auth_app, mongo, as_org):
    client, token_for = org_auth_app

    as_org("acme")
    _seed_turn("s-api", "u1", "acme confidential")

    body = client.get("/v1/sessions?user_id=u1", headers=token_for("u1", "globex")).json()
    assert body["count"] == 0, "another org's token listed our sessions"

    body = client.get("/v1/sessions?user_id=u1", headers=token_for("u1", "acme")).json()
    assert body["count"] == 1
    assert body["sessions"][0]["session_id"] == "s-api"


def test_org_enforcement_can_be_consciously_disabled(org_auth_app, monkeypatch):
    """Single-tenant deployments may opt out — org-less tokens then run as the
    default org instead of being refused."""
    client, token_for = org_auth_app
    monkeypatch.setattr(config, "REQUIRE_ORG_CLAIM", False)
    response = client.get("/v1/sessions?user_id=u1", headers=token_for("u1", None))
    assert response.status_code == 200
