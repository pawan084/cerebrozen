from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class InterventionRecommendation(Base):
    """One "here's something that might help, and here's why" offer.

    Adapted from the sibling build's intervention engine. The idea worth taking
    is **recommend with a visible rationale**: the app already nudges, but it
    never says what it noticed. A suggestion the user can't audit is a black
    box; one that says "2 of your last 5 logged nights were rated 2 or lower"
    can be argued with, and that fits this product's honesty posture.

    ``reason`` and the action are **frozen at fire time**, not looked up on
    read. If a rule's copy is edited later, an offer already shown to a user
    keeps the wording it was actually shown with — history stays true.

    ``state_snapshot`` holds the numbers behind ``reason`` (never raw journal
    or chat text), so the rationale can be checked rather than trusted.
    """

    __tablename__ = "intervention_recommendations"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    # Which code-defined rule fired (see services/interventions.RULES). A slug
    # rather than an FK: rules live in code for now, like the prompt registry's
    # code defaults, so there is no rules table to point at.
    rule_slug: Mapped[str] = mapped_column(String(80), index=True)
    # "self_support" — something the user can do alone, right now.
    # "human_support" — a real person. Reserved for the escalation path.
    tier: Mapped[str] = mapped_column(String(20), default="self_support")
    # The plain-language "why", shown verbatim to the user.
    reason: Mapped[str] = mapped_column(String(300), default="")
    # "widget" (an activity kind) | "program" (a catalogue program) | "support".
    action_kind: Mapped[str] = mapped_column(String(40), default="widget")
    action_target: Mapped[str] = mapped_column(String(120), default="")
    # The counts behind `reason` — numbers only, never content.
    state_snapshot: Mapped[dict] = mapped_column(JSONB, default=dict)

    accepted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    dismissed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
