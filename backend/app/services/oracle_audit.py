"""Oracle tool-call audit — what the agent did, and what it is still waiting on.

The Oracle's write tools (`log_mood`, `save_journal`, `log_sleep`) edit a user's
own records after an ``interrupt()`` confirmation. Nothing recorded any of it:
an operator could not see which writes were approved, nor that a confirmation
had been sitting unanswered since a restart. This module is that record.

It lives in `services/` on purpose. `app/agent/*` and the Oracle route are
excluded from the coverage gate (they only run meaningfully against a live
model), so all the logic sits here where it is unit-tested, and the agent
modules keep only thin call sites.

Argument VALUES are never stored — see ``OracleToolCall``'s docstring for why.
"""
from __future__ import annotations

import logging
import uuid
from collections.abc import Sequence

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.oracle_audit import OracleToolCall

logger = logging.getLogger("cerebro.oracle")

# Decisions. "pending" is the operationally interesting one.
AUTO = "auto"
PENDING = "pending"
APPROVED = "approved"
DECLINED = "declined"
#: Closed by an operator because the confirmation can no longer be answered —
#: the usual cause is a MemorySaver restart, which drops the graph state while
#: leaving this row behind (register E57). Deliberately its own value rather
#: than reusing DECLINED: the user declined nothing, and a trail that says they
#: did would be a false record of someone's choice about their own data.
EXPIRED = "expired"

READ = "read"
WRITE = "write"


def _keys(args: dict | None) -> list[str]:
    """Argument NAMES, sorted for stable output. Values are dropped here — this
    is the single choke point that keeps user content out of the audit table."""
    return sorted(args.keys()) if args else []


async def record_read(
    db: AsyncSession, *, user_id: uuid.UUID, thread_id: str, tool: str, args: dict | None = None
) -> None:
    """Log a read tool that ran immediately (nothing to confirm)."""
    db.add(
        OracleToolCall(
            user_id=user_id, thread_id=thread_id, tool=tool,
            risk_tier=READ, decision=AUTO, arg_keys=_keys(args),
            resolved_at=utcnow(),
        )
    )
    await db.commit()


async def open_pending(
    db: AsyncSession, *, user_id: uuid.UUID, thread_id: str, tool: str, args: dict | None = None
) -> None:
    """Mark a write tool as awaiting the user's decision.

    **Idempotent by necessity.** LangGraph re-executes an interrupted node from
    the top when the graph resumes, so everything before ``interrupt()`` runs a
    second time on approval. Without the existing-row check every confirmation
    would leave a duplicate pending row behind that nothing ever resolves.
    """
    existing = await db.scalar(
        select(OracleToolCall).where(
            OracleToolCall.thread_id == thread_id,
            OracleToolCall.tool == tool,
            OracleToolCall.decision == PENDING,
        )
    )
    if existing is not None:
        return
    db.add(
        OracleToolCall(
            user_id=user_id, thread_id=thread_id, tool=tool,
            risk_tier=WRITE, decision=PENDING, arg_keys=_keys(args),
        )
    )
    await db.commit()


async def resolve(db: AsyncSession, *, thread_id: str, tool: str, approved: bool) -> None:
    """Close out the pending row for this thread+tool with the user's answer.

    A missing row is not an error: audit is observability, and it must never be
    the reason a user's approved write fails. It is logged and ignored.
    """
    row = await db.scalar(
        select(OracleToolCall)
        .where(
            OracleToolCall.thread_id == thread_id,
            OracleToolCall.tool == tool,
            OracleToolCall.decision == PENDING,
        )
        .order_by(OracleToolCall.created_at.desc())
    )
    if row is None:
        logger.warning(
            "No pending audit row for %s on thread %s — recording nothing", tool, thread_id
        )
        return
    row.decision = APPROVED if approved else DECLINED
    row.resolved_at = utcnow()
    await db.commit()


async def expire(db: AsyncSession, *, call_id: uuid.UUID) -> OracleToolCall | None:
    """Close a stuck pending row. Returns the row, or None if it isn't pending.

    **This approves nothing and executes nothing.** It only marks the audit row
    as unanswerable, so the "Awaiting a decision" queue stops showing a
    confirmation that no longer has a graph behind it to resume. The Oracle's
    write tools run from the user's own approval inside their thread; there is
    no path from here to a write, and there must never be one — an operator
    closing a record is a different act from an operator writing to someone's
    journal, and only the first is defensible.

    Refuses anything already resolved, so a double-click cannot rewrite a real
    approve/decline into "expired".
    """
    row = await db.get(OracleToolCall, call_id)
    if row is None or row.decision != PENDING:
        return None
    row.decision = EXPIRED
    row.resolved_at = utcnow()
    await db.commit()
    return row


async def recent(db: AsyncSession, *, limit: int = 20) -> Sequence[OracleToolCall]:
    """Newest tool calls first, for the admin Oracle tab."""
    rows = await db.scalars(
        select(OracleToolCall).order_by(OracleToolCall.created_at.desc()).limit(limit)
    )
    return rows.all()


async def pending(db: AsyncSession, *, limit: int = 20) -> Sequence[OracleToolCall]:
    """Confirmations still awaiting an answer — oldest first, because the point
    of the list is to spot what has been stuck longest."""
    rows = await db.scalars(
        select(OracleToolCall)
        .where(OracleToolCall.decision == PENDING)
        .order_by(OracleToolCall.created_at.asc())
        .limit(limit)
    )
    return rows.all()


async def counts(db: AsyncSession) -> dict[str, int]:
    """Totals for the status band: {pending, approved, declined, total}."""
    rows = await db.execute(
        select(OracleToolCall.decision, func.count()).group_by(OracleToolCall.decision)
    )
    by_decision = {decision: count for decision, count in rows.all()}
    return {
        "pending": by_decision.get(PENDING, 0),
        "approved": by_decision.get(APPROVED, 0),
        "declined": by_decision.get(DECLINED, 0),
        "total": sum(by_decision.values()),
    }
