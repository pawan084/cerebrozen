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
