"""Structured error tracking (WC-17).

A production exception used to be a log line nobody reads: `logger.exception`
in the request middleware, a stack trace in a container's stdout, and no way to
know whether it happened once or ten thousand times.

This module gives that exception a **fingerprint**, a **count**, and a fixed,
allow-listed context — and then hands it to whichever sink is configured. With
nothing configured it still emits one structured line per distinct failure,
which is already the difference between "something is wrong" and "GET
/journal/{id} has failed 412 times since 09:00, all with the same fingerprint".

## Why there is no vendor here yet

WC-17 says "Sentry or equivalent". Choosing the equivalent is not an engineering
decision for this product: an error sink receives fragments of a mental-health
service's runtime, so **where it lands is a DPDP question** (transfer,
retention, and who at the vendor can read it) before it is a pricing one. That
belongs to the owner. What does not need deciding — the part that is genuinely
hard and genuinely ours — is *what is allowed to leave this process at all*, and
that is written and tested here. A vendor adapter is then a `Sink` with one
method, and the policy below already governs it.

## The policy: allow-list, never deny-list

The tempting version scrubs known-sensitive fields out of a rich context. That
fails open on the field nobody thought of, and in this codebase the fields
nobody thought of include a journal sentence in a validation error, an email in
a unique-constraint message, and a crisis flag in an escalation trace.

So nothing is scrubbed OUT. A fixed set of fields is copied IN:

* the exception's **type** — never its message, which routinely quotes the
  input that broke it (`asyncpg` puts the offending value in the text),
* a **templated** route path — `/journal/{entry_id}`, so an id in a URL is not
  an identifier in an error report,
* the HTTP method, the request id already on the response, and
* an optional **HMAC of the user id**, so "how many people hit this" is
  answerable without anyone being named.

No request body, no query string, no headers, no cookies, no form fields, no
ORM row, no `repr()` of anything. The stack trace's *frames* are kept (file,
function, line) but their **local variables are not** — a local named `body` or
`text` is exactly the thing that must not travel.
"""

from __future__ import annotations

import hashlib
import hmac
import json
import logging
import re
import traceback
from dataclasses import dataclass, field
from typing import Protocol

from app.core.config import settings

logger = logging.getLogger("cerebro.errors")

#: Path segments that are identifiers rather than route structure. A UUID, a
#: numeric id, or a long opaque token in a URL is data about a person; the route
#: it belongs to is not. Applied only when FastAPI could not give us the matched
#: route template (background jobs, 404s, failures before routing).
_UUID = re.compile(r"^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$")
_NUMERIC = re.compile(r"^\d+$")
_OPAQUE = re.compile(r"^[A-Za-z0-9_\-]{24,}$")


def template_path(path: str) -> str:
    """Replace identifier-shaped segments with `{id}`.

    The fallback for when the route template is unavailable. Deliberately
    conservative in one direction only: a segment that MIGHT be an id is
    replaced, because a false `{id}` costs a little grouping precision and a
    false passthrough leaks a user's row key into an error report.
    """
    out = []
    for segment in path.split("/"):
        if not segment:
            out.append(segment)
        elif _UUID.match(segment) or _NUMERIC.match(segment) or _OPAQUE.match(segment):
            out.append("{id}")
        else:
            out.append(segment)
    return "/".join(out)


def hash_user(user_id: str | None) -> str | None:
    """A stable, non-reversible handle for one user.

    HMAC rather than a bare digest so the mapping cannot be rebuilt by anyone
    who guesses uuids and hashes them; keyed on `secret_key`, so it is stable
    within a deployment and meaningless outside it. Twelve hex characters is
    plenty to count distinct people affected and far too few to be an identity.
    """
    if not user_id:
        return None
    digest = hmac.new(
        settings.secret_key.encode(), str(user_id).encode(), hashlib.sha256
    ).hexdigest()
    return digest[:12]


@dataclass(frozen=True)
class ErrorEvent:
    """Everything that is allowed to leave this process about one failure."""

    kind: str
    """The exception's class name. Never its message."""

    fingerprint: str
    """Stable across occurrences of the same fault, so they can be counted."""

    where: str
    """`METHOD /templated/path` for requests, or `job:<name>` for the loops."""

    request_id: str | None = None
    user: str | None = None
    frames: tuple[str, ...] = field(default_factory=tuple)

    def as_dict(self) -> dict:
        payload = {
            "kind": self.kind,
            "fingerprint": self.fingerprint,
            "where": self.where,
        }
        if self.request_id:
            payload["request_id"] = self.request_id
        if self.user:
            payload["user"] = self.user
        if self.frames:
            payload["frames"] = list(self.frames)
        return payload


class Sink(Protocol):
    """Where a scrubbed event goes. A vendor adapter implements exactly this."""

    def send(self, event: ErrorEvent) -> None: ...


class LogSink:
    """The always-on sink: one structured JSON line per failure.

    Not a placeholder for a "real" tracker — it is what makes the failure
    countable by anything that already reads container logs, and it is the only
    sink that is guaranteed to exist in every environment including CI.
    """

    def send(self, event: ErrorEvent) -> None:
        logger.error("error_event %s", json.dumps(event.as_dict(), sort_keys=True))


_sinks: list[Sink] = [LogSink()]


def register_sink(sink: Sink) -> None:
    """Add a sink. Called by a vendor adapter at startup, and by tests."""
    _sinks.append(sink)


def reset_sinks() -> None:
    """Back to log-only. For tests, and for a clean re-init."""
    _sinks.clear()
    _sinks.append(LogSink())


def _frames_of(exc: BaseException, limit: int = 12) -> tuple[str, ...]:
    """`file:line in function`, innermost last. No locals, ever.

    `traceback.extract_tb` reads only the frame's position, never its
    `f_locals` — which is the point. A helpful "show me the variables" error
    reporter would ship the journal entry that caused the crash.
    """
    tb = exc.__traceback__
    if tb is None:
        return ()
    out = []
    for frame in traceback.extract_tb(tb)[-limit:]:
        filename = frame.filename.replace("\\", "/").split("/app/", 1)[-1]
        out.append(f"{filename}:{frame.lineno} in {frame.name}")
    return tuple(out)


def _fingerprint(kind: str, where: str, frames: tuple[str, ...]) -> str:
    """Group occurrences of the same fault.

    Built from the exception type, the route, and the INNERMOST frame — not the
    message, which often contains the offending value and would therefore split
    one recurring bug into a thousand singletons AND carry that value into the
    grouping key.
    """
    innermost = frames[-1] if frames else ""
    basis = f"{kind}|{where}|{innermost}"
    return hashlib.sha256(basis.encode()).hexdigest()[:16]


def capture(
    exc: BaseException,
    *,
    where: str,
    request_id: str | None = None,
    user_id: str | None = None,
) -> ErrorEvent:
    """Scrub, fingerprint and dispatch one failure. Never raises.

    A crash inside the error reporter must not become the error: every sink is
    called defensively, because the one moment this code runs is the moment the
    process is already having a bad time.
    """
    kind = type(exc).__name__
    frames = _frames_of(exc)
    event = ErrorEvent(
        kind=kind,
        fingerprint=_fingerprint(kind, where, frames),
        where=where,
        request_id=request_id,
        user=hash_user(user_id),
        frames=frames,
    )
    for sink in list(_sinks):
        try:
            sink.send(event)
        except Exception:  # noqa: BLE001 - a broken sink cannot break the request
            logger.warning("error sink %s failed", type(sink).__name__, exc_info=False)
    return event


def where_for_request(method: str, route_template: str | None, path: str) -> str:
    """`METHOD /template`, preferring FastAPI's matched route.

    The route template is already free of ids by construction, which is why it
    is preferred; `template_path` is the fallback for the requests that failed
    before or outside routing.
    """
    return f"{method} {route_template or template_path(path)}"
