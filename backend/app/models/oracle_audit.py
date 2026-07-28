from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class OracleToolCall(Base):
    """One Oracle tool invocation — the agent's audit trail.

    The Oracle can WRITE user data (mood, journal, sleep) behind an
    ``interrupt()`` confirmation. Until this table existed nothing recorded
    which tools ran, which writes were approved, or which confirmations were
    still stuck — the operator-visible half of a feature that edits a user's
    own records.

    **Argument VALUES are deliberately not stored.** Only ``arg_keys``: a
    journal body or mood note copied into an audit row would duplicate the
    user's most sensitive content into a store that sits outside the consent
    flags governing the originals, survives a journal deletion, and would have
    to be exported and erased separately under DPDP. The keys answer the
    operational question ("what did it try to write?") without becoming a
    second copy of the content.
    """

    __tablename__ = "oracle_tool_calls"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    # The LangGraph conversation thread; how a pending row is matched to the
    # /oracle/confirm that resolves it.
    thread_id: Mapped[str] = mapped_column(String(120), index=True)
    tool: Mapped[str] = mapped_column(String(80), index=True)
    # "read"  — ran immediately, nothing to confirm.
    # "write" — paused for the user's approval first.
    risk_tier: Mapped[str] = mapped_column(String(16), default="read")
    # "auto"     — a read tool; no confirmation exists to give.
    # "pending"  — waiting on the user. Stuck rows are the thing to watch.
    # "approved" / "declined" — the user's answer.
    decision: Mapped[str] = mapped_column(String(16), default="auto", index=True)
    # Argument NAMES only — never their values. See the class docstring.
    arg_keys: Mapped[list[str]] = mapped_column(JSONB, default=list)
    resolved_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
