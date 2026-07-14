"""Test fixtures — force fully-offline mode (mock LLM, in-memory stores, fakeredis).

Env is set BEFORE any app import so the cached config picks it up. No OpenAI key, no
Mongo, no Redis → the graph runs against deterministic fallbacks (Article 10), and
PROMPT_SOURCE=codebase pins the bundled workbook so no test touches S3.
"""
import os
import uuid

os.environ.setdefault("OPENAI_API_KEY", "")
# Regulated-workplace mode is the PRODUCT default (config.py). The suite pins it OFF so
# the ~1,400 feature tests keep exercising the full surface — mood capture, the Coachable
# Index, and everything downstream of them. The default's own direction, and the store
# refusals when it is on, are tested explicitly in tests/test_regulated_workplace.py.
os.environ["CEREBROZEN_REGULATED_WORKPLACE"] = "false"
# THE SUITE WAS NOT ACTUALLY OFFLINE, and had not been for its entire existence.
#
# Blanking OPENAI_API_KEY above looks sufficient. It is not: `app.main` calls
# `load_local_env()` at import, and the loader treats an EMPTY variable as unset and
# overridable — so it cheerfully repopulated the key from the repo's own `.env`, and the
# "offline, no network, no key" tests went out to the real OpenAI API and billed for it.
# That is where the 14s and 31s test times were coming from; nobody noticed because the
# tests still passed, only slowly and for money.
#
# The provider is therefore pinned EXPLICITLY. It cannot be undone by a .env file, and it
# does not depend on the absence of a key — which is the whole lesson: an offline
# guarantee built on "we removed the credential" fails the moment anything puts the
# credential back. Build it on "the network client is not the one we use."
os.environ["CEREBROZEN_LLM_PROVIDER"] = "mock"
os.environ["MONGO_DB_URL"] = ""
os.environ["REDIS_URL"] = ""
# POSTGRES_URL is the SEAM that switches the whole app from Mongo to the Postgres shim
# (stores/mongo.get_client checks it first). If a developer happens to have it exported —
# and I did, to run the pgvector tests — every Mongo store test silently runs against
# Postgres instead, and 73 of them fail for reasons that have nothing to do with the code.
# The suite must not depend on what is in your shell. Tests that genuinely want Postgres
# (tests/test_rag_layer.py) set it themselves via monkeypatch, scoped to the test.
os.environ["POSTGRES_URL"] = ""
os.environ["AUTH_DEV_BYPASS"] = "true"
# Never reach for S3 in tests: pin the registry to the bundled workbook.
os.environ["PROMPT_SOURCE"] = "codebase"
# In-memory checkpointer (no sqlite file side-effects in the repo).
os.environ["SQLITE_CHECKPOINT_PATH"] = ":memory:"

import pytest

from app.schemas import SessionStartRequest, SessionTurnRequest


class TurnResult:
    """One turn's outcome: the streamed reply text plus the `done` payload."""

    def __init__(self, reply: str, done: dict) -> None:
        self.reply = reply
        self.done = done

    def __getitem__(self, key):
        return self.done[key]

    def get(self, key, default=None):
        return self.done.get(key, default)


def _collect_tokens():
    chunks: list[str] = []
    return chunks, lambda t: chunks.append(t)


@pytest.fixture
def start_session():
    """Start a session and run its first turn, collecting streamed tokens."""
    from app.service import get_service

    def _start(user_id: str, text: str, **kwargs) -> TurnResult:
        chunks, on_token = _collect_tokens()
        done = get_service().start_session(
            user_id,
            SessionStartRequest(text=text, **kwargs),
            on_token=on_token,
        )
        return TurnResult("".join(chunks), done)

    return _start


@pytest.fixture
def run_turn():
    """Run a follow-up turn on an existing session, collecting streamed tokens."""
    from app.service import get_service

    def _turn(user_id: str, session_id: str, text: str, **kwargs) -> TurnResult:
        chunks, on_token = _collect_tokens()
        done = get_service().run_turn(
            user_id,
            session_id,
            SessionTurnRequest(text=text, **kwargs),
            on_token=on_token,
        )
        return TurnResult("".join(chunks), done)

    return _turn


@pytest.fixture
def user_id():
    """A unique user id per test — sessions/state must never bleed between tests."""
    return f"test-{uuid.uuid4().hex[:8]}"


# ── Shared fakes for the I/O layers ──────────────────────────────────────────
#
# The stores were 11-17% covered because every one of them needs a document store. They
# are NOT untestable — they just need a Mongo that isn't a server. mongomock gives us the
# real pymongo API against an in-process store, so these tests exercise the actual query
# and update expressions the code writes ($set, $push/$each/$slice, $addToSet, dotted
# paths) instead of asserting that a mock was called.
#
# Patch ONE seam: `mongo.get_client`. Every store module resolves its handle through it
# (it is also the Mongo/Postgres switch), so one fixture reaches all of them. Patching
# each store separately would be five chances to patch the wrong thing.


@pytest.fixture
def mongo(monkeypatch):
    """An in-process Mongo. Returns the client; the stores see it automatically."""
    import mongomock

    from app import config as _config
    from app.stores import mongo as _mongo

    client = mongomock.MongoClient()
    monkeypatch.setattr(_mongo, "get_client", lambda: client)
    monkeypatch.setattr(_mongo, "_client", client)
    # Several stores short-circuit on an empty MONGO_DB_URL before they ever ask for a
    # client, so the config has to look connected too.
    monkeypatch.setattr(_config, "MONGO_DB_URL", "mongodb://fake")
    return client


@pytest.fixture
def agentic_coll(mongo):
    """The per-user agentic document collection, pre-resolved."""
    from app import config as _config

    return mongo[_config.MONGO_BACKEND_DB][_config.MONGO_AGENTIC_COLLECTION]


@pytest.fixture
def frozen_now(monkeypatch):
    """Freeze 'now' so date arithmetic (the 7-day check-in rule) is deterministic.

    A test that depends on the wall clock is a test that fails on a Tuesday.
    """
    from datetime import datetime, timezone

    fixed = datetime(2026, 7, 14, 12, 0, 0, tzinfo=timezone.utc)

    class _Frozen(datetime):
        @classmethod
        def now(cls, tz=None):
            return fixed if tz else fixed.replace(tzinfo=None)

        @classmethod
        def utcnow(cls):
            return fixed.replace(tzinfo=None)

    return fixed, _Frozen
