"""Postgres backend for the document stores — a Mongo-compatible shim over JSONB.

WHY A SHIM AND NOT A REWRITE
----------------------------
The five store modules (agentic, conversation, dynamic_vars, org, mongo) are ~2,200 lines
and speak pymongo: `coll.find_one(...)`, `coll.update_one(..., {"$push": ...})`. Rewriting
them for SQL means rewriting every query AND re-testing every caller.

But the API surface they actually use is tiny — measured across all five modules:

    find_one 16 · update_one 14 · find 2 · count_documents 2 · delete_one 1 · aggregate 1
    operators: $set $push $each $slice $addToSet $setOnInsert $inc $ne $exists $size $ifNull

That is small enough to *emulate*. So this module implements a `PgCollection` that quacks
like a pymongo collection and stores each document as a JSONB row. The result: **all five
store modules and all 17 caller files work unchanged** — the Postgres port is a config
switch, not a rewrite.

The trade: update_one is a read-modify-write under a row lock (`SELECT … FOR UPDATE`), with
the Mongo operators applied in Python. That is O(document), not O(change) — correct and
simple, and at coaching-session scale (one doc per user, one per session) the difference is
irrelevant. If a document ever gets huge, revisit.

Schema: one table per collection, `_id TEXT PRIMARY KEY, doc JSONB`, plus a GIN index so
filters on doc fields stay fast.
"""

from __future__ import annotations

import copy
import logging
import os
import threading
from typing import Any, Dict, Iterable, List, Optional

logger = logging.getLogger("cerebrozen.stores.pg")

_pool = None
_pool_lock = threading.Lock()
_ensured: set = set()


def postgres_url() -> str:
    from app import config

    return os.environ.get("POSTGRES_URL", "") or getattr(config, "POSTGRES_URL", "")


def get_pool():
    """Lazy connection pool. None when Postgres isn't configured/reachable — callers then
    fall through to Mongo exactly as before."""
    global _pool
    if _pool is not None:
        return _pool or None
    url = postgres_url()
    if not url:
        return None
    with _pool_lock:
        if _pool is None:
            try:
                from psycopg_pool import ConnectionPool

                _pool = ConnectionPool(
                    conninfo=url,
                    max_size=int(os.environ.get("CEREBROZEN_PG_POOL_MAX", "10")),
                    kwargs={"autocommit": True},
                    open=True,
                )
                logger.info("pg.pool_open")
            except Exception as exc:  # noqa: BLE001
                logger.error("pg.pool_failed", extra={"error": str(exc)})
                _pool = False  # sentinel: tried and failed, don't retry every call
    return _pool or None


# ── Mongo operator emulation ────────────────────────────────────────────────


def _get_path(doc: Dict[str, Any], path: str) -> Any:
    node: Any = doc
    for part in path.split("."):
        if not isinstance(node, dict) or part not in node:
            return None
        node = node[part]
    return node


def _set_path(doc: Dict[str, Any], path: str, value: Any) -> None:
    parts = path.split(".")
    node = doc
    for p in parts[:-1]:
        node = node.setdefault(p, {})
        if not isinstance(node, dict):  # a scalar sits where a subdoc is needed
            return
    node[parts[-1]] = value


def _matches(doc: Dict[str, Any], flt: Dict[str, Any]) -> bool:
    """The filter subset the stores actually use: equality, $ne, $exists, $in,
    plus the top-level $and/$or that tenancy's scoped() filters emit."""
    for key, cond in (flt or {}).items():
        if key == "$and":
            if not all(_matches(doc, sub) for sub in cond):
                return False
            continue
        if key == "$or":
            if not any(_matches(doc, sub) for sub in cond):
                return False
            continue
        actual = _get_path(doc, key)
        if isinstance(cond, dict):
            for op, want in cond.items():
                if op == "$ne" and actual == want:
                    return False
                if op == "$exists" and (actual is not None) != bool(want):
                    return False
                if op == "$in" and actual not in want:
                    return False
                if op == "$nin" and actual in want:
                    return False
            continue
        if actual != cond:
            return False
    return True


def _apply_update(doc: Dict[str, Any], update: Dict[str, Any], *, inserted: bool) -> Dict[str, Any]:
    """Apply the Mongo update operators the stores use. Order matters: $setOnInsert only
    fires on an insert, and $push/$each/$slice must run together."""
    out = copy.deepcopy(doc)

    for path, value in (update.get("$setOnInsert") or {}).items():
        if inserted:
            _set_path(out, path, value)

    for path, value in (update.get("$set") or {}).items():
        _set_path(out, path, value)

    for path, value in (update.get("$inc") or {}).items():
        cur = _get_path(out, path) or 0
        _set_path(out, path, cur + value)

    for path, spec in (update.get("$push") or {}).items():
        arr = list(_get_path(out, path) or [])
        if isinstance(spec, dict) and "$each" in spec:
            arr.extend(spec["$each"])
            if "$slice" in spec:                  # keep last N (negative) / first N
                n = spec["$slice"]
                arr = arr[n:] if n < 0 else arr[:n]
        else:
            arr.append(spec)
        _set_path(out, path, arr)

    for path, spec in (update.get("$addToSet") or {}).items():
        arr = list(_get_path(out, path) or [])
        items = spec["$each"] if isinstance(spec, dict) and "$each" in spec else [spec]
        for it in items:
            if it not in arr:
                arr.append(it)
        _set_path(out, path, arr)

    return out


def _project(doc: Dict[str, Any], projection: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    """Inclusion projection, plus the one exotic form the stores use:
    `{"messages": {"$slice": 4}}` (first N) and `{"$slice": -10}` (last N)."""
    if not projection:
        return doc
    include = {k: v for k, v in projection.items() if k != "_id"}
    if not any(v for v in include.values()):        # exclusion-only → return whole doc
        return doc
    out: Dict[str, Any] = {}
    for key, spec in include.items():
        if isinstance(spec, dict) and "$slice" in spec:
            n = spec["$slice"]
            arr = list(_get_path(doc, key) or [])
            out[key] = arr[n:] if n < 0 else arr[:n]
        elif spec:
            val = _get_path(doc, key)
            if val is not None:
                out[key] = val
    return out


class _Result:
    def __init__(self, matched: int = 0, modified: int = 0, deleted: int = 0) -> None:
        self.matched_count = matched
        self.modified_count = modified
        self.deleted_count = deleted


class PgCursor:
    """A pymongo cursor: iterable, and chainable with .sort()/.limit().

    `find()` must return one of these, not a list — the stores do
    `coll.find(...).sort("updated_at", -1).limit(n)`, and a list has no .sort(key, dir)."""

    def __init__(self, docs: List[Dict[str, Any]]) -> None:
        self._docs = docs

    def sort(self, key, direction: int = 1) -> "PgCursor":
        # pymongo accepts both .sort("field", -1) and .sort([("a", -1), ("b", -1)]).
        # Missing keys sort last — Mongo orders missing values first on ascending, but the
        # stores only sort on always-present timestamps, so `""` is a safe, total ordering.
        spec = key if isinstance(key, list) else [(key, direction)]
        for k, d in reversed(spec):  # minor keys first: python sorts are stable
            self._docs.sort(key=lambda doc, _k=k: str(_get_path(doc, _k) or ""), reverse=(d < 0))
        return self

    def skip(self, n: int) -> "PgCursor":
        self._docs = self._docs[n:]
        return self

    def limit(self, n: int) -> "PgCursor":
        self._docs = self._docs[:n]
        return self

    def __iter__(self):
        return iter(self._docs)

    def __len__(self) -> int:
        return len(self._docs)


class PgCollection:
    """A pymongo-shaped collection backed by one JSONB table."""

    def __init__(self, name: str) -> None:
        self.name = "".join(c for c in name if c.isalnum() or c == "_")
        self._ensure()

    def _ensure(self) -> None:
        if self.name in _ensured:
            return
        pool = get_pool()
        if pool is None:
            return
        with pool.connection() as conn:
            conn.execute(
                f'CREATE TABLE IF NOT EXISTS "{self.name}" '
                f'(_id TEXT PRIMARY KEY, doc JSONB NOT NULL)'
            )
            conn.execute(
                f'CREATE INDEX IF NOT EXISTS "{self.name}_doc_gin" '
                f'ON "{self.name}" USING GIN (doc)'
            )
        _ensured.add(self.name)

    # -- the identity of a document. Mongo lets any field be the key; the stores key on
    #    user_id or session_id, so derive a stable primary key from the filter.
    @staticmethod
    def _key(flt: Dict[str, Any], doc: Optional[Dict[str, Any]] = None) -> Optional[str]:
        for field in ("_id", "user_id", "session_id"):
            v = (flt or {}).get(field)
            if isinstance(v, str) and v:
                return v
            if doc and isinstance(doc.get(field), str) and doc[field]:
                return doc[field]
        return None

    def _all(self) -> Iterable[Dict[str, Any]]:
        pool = get_pool()
        if pool is None:
            return []
        with pool.connection() as conn:
            rows = conn.execute(f'SELECT doc FROM "{self.name}"').fetchall()
        return [r[0] for r in rows]

    # -- pymongo surface ------------------------------------------------------

    def find_one(self, flt: Dict[str, Any], projection: Optional[Dict[str, Any]] = None):
        pool = get_pool()
        if pool is None:
            return None
        key = self._key(flt)
        with pool.connection() as conn:
            if key:
                row = conn.execute(
                    f'SELECT doc FROM "{self.name}" WHERE _id = %s', (key,)
                ).fetchone()
                docs = [row[0]] if row else []
            else:
                docs = [r[0] for r in conn.execute(f'SELECT doc FROM "{self.name}"').fetchall()]
        for d in docs:
            if _matches(d, flt):
                return _project(d, projection)
        return None

    def find(self, flt: Optional[Dict[str, Any]] = None,
             projection: Optional[Dict[str, Any]] = None) -> "PgCursor":
        # Must return a CURSOR, not a list: callers chain `.sort(...).limit(...)`.
        docs = [_project(d, projection) for d in self._all() if _matches(d, flt or {})]
        return PgCursor(docs)

    def count_documents(self, flt: Optional[Dict[str, Any]] = None, **kwargs: Any) -> int:
        # **kwargs absorbs pymongo's `limit=` — without it this raised TypeError, the
        # store swallowed it, and every repeat user silently read back as FRESH.
        n = sum(1 for d in self._all() if _matches(d, flt or {}))
        limit = kwargs.get("limit")
        return min(n, limit) if limit else n

    def update_one(self, flt: Dict[str, Any], update: Dict[str, Any], upsert: bool = False):
        """Read-modify-write under a row lock. The lock is what makes concurrent turns on
        the same session safe — two writers would otherwise clobber each other's $push."""
        pool = get_pool()
        if pool is None:
            return _Result()
        key = self._key(flt)
        if not key:
            return _Result()

        with pool.connection() as conn:
            with conn.transaction():
                row = conn.execute(
                    f'SELECT doc FROM "{self.name}" WHERE _id = %s FOR UPDATE', (key,)
                ).fetchone()
                existing = row[0] if row else None
                # The row `_key` guessed must still satisfy the WHOLE filter. `_key` picks
                # a primary key by field-name priority, so a filter naming more than one
                # identity (say {session_id, user_id}) can resolve to the wrong row — and
                # writing to a row the caller did not ask for is how one user's update
                # lands on another user's document. find_one already re-checks with
                # `_matches`; this is the same guard on the write path.
                if existing is not None and not _matches(existing, flt):
                    return _Result()
                if existing is None and not upsert:
                    return _Result()

                inserted = existing is None
                base = existing or {k: v for k, v in flt.items() if not isinstance(v, dict)}
                new_doc = _apply_update(base, update, inserted=inserted)

                from psycopg.types.json import Jsonb

                conn.execute(
                    f'INSERT INTO "{self.name}" (_id, doc) VALUES (%s, %s) '
                    f'ON CONFLICT (_id) DO UPDATE SET doc = EXCLUDED.doc',
                    (key, Jsonb(new_doc)),
                )
        return _Result(matched=0 if inserted else 1, modified=1)

    def _rows_matching(self, flt: Dict[str, Any]) -> List[tuple]:
        """(_id, doc) for every row matching the FULL filter.

        Deletes route through this rather than through `_key`, and that is the whole point.
        `_key` GUESSES the primary key from a filter by field-name priority; a delete that
        trusts the guess and ignores the rest of the filter is two bugs at once:

          - It deleted the wrong row (or none). `conversation.delete_session` passes
            {session_id, user_id}; `_key` returned the USER id, so the shim ran
            `DELETE WHERE _id = <user_id>` against a table keyed by session — nothing
            matched, and "delete my session" returned False forever while the transcript
            stayed in the database.
          - And it discarded `user_id` entirely. So the moment the key bug alone were
            fixed, `DELETE WHERE _id = <session_id>` would happily delete a session
            belonging to somebody else — the exact IDOR that `delete_session`'s docstring
            promises is impossible. The two had to be fixed together, or the fix is worse
            than the bug.

        Matching the whole filter here is what makes that promise true.
        """
        pool = get_pool()
        if pool is None:
            return []
        with pool.connection() as conn:
            rows = conn.execute(f'SELECT _id, doc FROM "{self.name}"').fetchall()
        return [(rid, doc) for rid, doc in rows if _matches(doc, flt or {})]

    def delete_one(self, flt: Dict[str, Any]):
        pool = get_pool()
        # An EMPTY filter deletes nothing. Mongo would happily delete an arbitrary
        # document for `delete_one({})`, and matching that would be faithful and insane:
        # an empty filter reaching a delete is a caller bug (a variable that came back
        # None), and the blast radius is the whole collection. Refuse it.
        # (Caught by a test the moment my full-filter scan made `{}` match everything —
        # the previous key-guessing version was accidentally safe here.)
        if pool is None or not flt:
            return _Result()
        rows = self._rows_matching(flt)
        if not rows:
            return _Result()
        with pool.connection() as conn:
            cur = conn.execute(f'DELETE FROM "{self.name}" WHERE _id = %s', (rows[0][0],))
        return _Result(deleted=cur.rowcount or 0)

    def delete_many(self, flt: Dict[str, Any]):
        pool = get_pool()
        if pool is None or not flt:   # same guard as delete_one — see above
            return _Result()
        # Count what the DATABASE actually removed, not what we hoped it would. The old
        # version incremented on a filter MATCH and never looked at rowcount, so it could
        # report N deletions having removed nothing — and a GDPR erase built on that would
        # report success while the data sat there.
        deleted = 0
        for rid, _doc in self._rows_matching(flt):
            with pool.connection() as conn:
                cur = conn.execute(f'DELETE FROM "{self.name}" WHERE _id = %s', (rid,))
            deleted += cur.rowcount or 0
        return _Result(deleted=deleted)

    def aggregate(self, pipeline: List[Dict[str, Any]]):
        """Only the ONE pipeline the stores use: $match then a $project computing
        `total: {$size: {$ifNull: [$messages, []]}}` and `tail: {$slice: [$messages, -10]}`.
        Emulated directly rather than pretending to be a general aggregation engine —
        an honest 20 lines beats a fake framework."""
        docs = list(self._all())
        for stage in pipeline:
            if "$match" in stage:
                docs = [d for d in docs if _matches(d, stage["$match"])]
            elif "$project" in stage:
                spec = stage["$project"]
                out = []
                for d in docs:
                    proj: Dict[str, Any] = {}
                    for field, rule in spec.items():
                        if field == "_id":
                            continue
                        if rule == 1:
                            proj[field] = _get_path(d, field)
                        elif isinstance(rule, dict) and "$size" in rule:
                            inner = rule["$size"]
                            arr = d.get("messages") or []
                            if isinstance(inner, dict) and "$ifNull" in inner:
                                path = str(inner["$ifNull"][0]).lstrip("$")
                                arr = _get_path(d, path) or []
                            proj[field] = len(arr)
                        elif isinstance(rule, dict) and "$slice" in rule:
                            path, n = rule["$slice"]
                            arr = list(_get_path(d, str(path).lstrip("$")) or [])
                            proj[field] = arr[n:] if n < 0 else arr[:n]
                    out.append(proj)
                docs = out
        return docs

    # index management — Postgres has the GIN index from _ensure(); these are no-ops so
    # the Mongo-era index code paths keep working untouched.
    def create_index(self, *a: Any, **k: Any) -> str:
        return ""

    def index_information(self) -> Dict[str, Any]:
        return {}

    def drop_index(self, *a: Any, **k: Any) -> None:
        return None


_collections: Dict[str, PgCollection] = {}


def collection(name: str) -> Optional[PgCollection]:
    """The Postgres-backed collection, or None when Postgres isn't configured."""
    if get_pool() is None:
        return None
    if name not in _collections:
        _collections[name] = PgCollection(name)
    return _collections[name]


# ── the client shim: ONE seam for every store ───────────────────────────────
# The stores reach their data two ways: `_collection()` (agentic, conversation,
# dynamic_vars) and `client[db][coll]` (mongo.read_user_context — the READ path that
# feeds profile_read). Shimming the CLIENT covers both, so there is a single place where
# Mongo becomes Postgres rather than one patch per store.


class PgDatabase:
    def __getitem__(self, name: str) -> PgCollection:
        return collection(name)          # type: ignore[return-value]


class PgClient:
    """Quacks like a MongoClient: `client[db][collection]`. The database name is ignored —
    Postgres has one database and the collections are tables inside it."""

    def __getitem__(self, _db_name: str) -> PgDatabase:
        return PgDatabase()


def client() -> Optional[PgClient]:
    """A Mongo-shaped client backed by Postgres, or None when Postgres isn't configured."""
    return PgClient() if get_pool() is not None else None
