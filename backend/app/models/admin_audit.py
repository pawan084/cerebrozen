from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, String, Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class AdminAuditLog(Base):
    """Who did what, on the operator surface, and why.

    Register E34/E35: the admin dashboard could create, edit and delete
    content, upload and clear media, save and *activate* prompt versions —
    including the live safety classifier — disable accounts, broadcast a push
    to every active user, and reveal a crisis excerpt, and none of it was
    attributable afterwards. `PromptTemplate` stored a note but not its author;
    only safety resolution recorded the acting admin; the excerpt reveal was a
    `logger.info` line that log rotation erases, while the UI told reviewers
    "the server logged it" and CLAIMS_MAP leaned on "a separate, logged,
    per-row GET". A malicious or simply mistaken operator left no trail.

    Deliberately append-only in practice: nothing in the app updates or
    deletes these rows. The point of a trail is that the person being trailed
    cannot edit it.

    `target_type` + `target_id` identify the thing acted on ("content",
    "user", "prompt", "safety_event", "nudge", "media"). `detail` carries the
    small, non-sensitive specifics an investigation needs — a disable reason,
    a prompt key and version, a broadcast's audience size. It must never carry
    user content: the excerpt reveal records THAT it happened, never what was
    read.
    """

    __tablename__ = "admin_audit_logs"

    # The operator. SET NULL rather than CASCADE: deleting an admin account
    # must not erase the record of what that account did.
    admin_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True
    )
    # Denormalised so the row still says who, after the account is gone.
    admin_email: Mapped[str] = mapped_column(String(255), default="")
    # "content.create", "user.disable", "prompt.activate", "safety.excerpt_read"…
    action: Mapped[str] = mapped_column(String(64), index=True)
    target_type: Mapped[str] = mapped_column(String(32), default="")
    target_id: Mapped[str] = mapped_column(String(64), default="")
    # Operator-supplied reason, where the action asks for one.
    reason: Mapped[str] = mapped_column(Text, default="")
    detail: Mapped[dict] = mapped_column(JSONB, default=dict)
