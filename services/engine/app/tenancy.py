"""App-layer tenancy.

Every engine-owned record is scoped by ``org_id`` so tenant isolation is a
property of the code, not of per-deploy database names (the reference
project's documented sharpest edge). The active org comes from the JWT's
``org_id`` claim, stamped into a context variable by ``require_auth``;
background/off-request work and dev-bypass requests run under DEFAULT_ORG.

Rules:
- Mongo/PG filters and upserts on engine collections go through ``scoped()``.
- Redis keys and LangGraph checkpointer thread ids embed ``current_org()``.
- The external user-profile read (``users`` collection, provisioned by the
  platform per tenant) is NOT filtered here — its documents carry no org
  field; per-tenant issuance of user ids is the platform's contract. The
  engine's own writes never rely on that assumption.
"""

from contextvars import ContextVar

DEFAULT_ORG = "default"

ctx_org_id: ContextVar[str] = ContextVar("ctx_org_id", default=DEFAULT_ORG)


def current_org() -> str:
    """The active tenant. Never empty — falls back to DEFAULT_ORG."""
    return ctx_org_id.get() or DEFAULT_ORG


# Documents written before tenancy carry no org field; they belong to the
# default org and to no one else. Every other tenant filters strictly.
_LEGACY_DEFAULT = {"$or": [{"org_id": DEFAULT_ORG}, {"org_id": {"$exists": False}}]}


def thread_id_for(session_id: str) -> str:
    """The checkpointer's key for a session: ``"<org>:<session_id>"``.

    THE ONE PLACE THIS IS BUILT. It lives here, rather than in the engine that writes it,
    because the engine is not the only party that needs it — the right to erasure has to
    find the same rows, and until 2026-07-17 it did not:

        checkpointer wrote:   6da49ab55dac…:157f8ae79fcc…      (org:session)
        erasure searched for: 157f8ae79fcc…                    (bare session_id)

    It matched nothing, so `delete_many` removed nothing, the re-scan found nothing
    remaining, and `verified` came back **True** — statutory erasure reporting success with
    the entire message history still on disk. On every backend. The unit tests inserted
    bare `thread_id`s, so they encoded the same wrong assumption as the code and stayed
    green.

    The org prefix is what tenanted the checkpointer (one namespace per org); erasure
    predates it and was never told. Two call sites deriving the same key independently is
    what made a silent divergence possible, so now there is one.
    """
    return f"{current_org()}:{session_id}"


def thread_ids_for(session_id: str) -> list:
    """Every key a session's checkpoints could be under — current, and pre-tenancy.

    Threads written before the org prefix carry the bare `session_id`. Erasure must reach
    those too: "we could not find your data in the old format" is not a defence.
    """
    return [thread_id_for(session_id), session_id]


def scoped(query: dict) -> dict:
    """Return ``query`` with the active org's scope added.

    Used for both read filters and upsert filters: an upsert through a scoped
    filter can only match — and therefore only modify — the active org's
    documents. For the default org the filter also matches legacy documents
    that predate tenancy (no ``org_id`` field); writers still stamp
    ``org_id`` explicitly on every new/updated document.
    """
    org = current_org()
    if org != DEFAULT_ORG:
        return {**query, "org_id": org}
    extra = list(query.get("$and") or [])
    return {**query, "$and": extra + [_LEGACY_DEFAULT]}
