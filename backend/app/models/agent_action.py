from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class AgentAction(Base):
    """One write the Oracle proposed, and what the user decided.

    Before this the only durable trace was the LangGraph checkpoint — a blob
    keyed by thread, not queryable, wiped by "delete all memory", and useless
    for answering "did the assistant write that, or did I?". For an agent that
    can create journal entries and log moods on someone's behalf, that question
    has to be answerable.

    Deliberately records the *decision*, not just the proposal: a declined
    action is as important as an approved one. A pattern of proposals a user
    keeps refusing is the signal that the agent is misreading them.

    `summary` is the same human-readable line shown on the confirm card — the
    user's own words back to them, never a raw tool payload. Arguments are NOT
    stored: `save_journal` carries the journal body, and duplicating that here
    would put private text in a second table with its own retention story.
    """

    __tablename__ = "agent_actions"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    thread_id: Mapped[str] = mapped_column(String(120), index=True)
    tool: Mapped[str] = mapped_column(String(64), index=True)
    summary: Mapped[str] = mapped_column(Text, default="")
    # proposed | approved | declined
    status: Mapped[str] = mapped_column(String(16), default="proposed", index=True)
    decided_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
