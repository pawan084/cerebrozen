from __future__ import annotations

import uuid
from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

if TYPE_CHECKING:
    pass


class Plan(Base):
    __tablename__ = "plans"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(160))
    focus: Mapped[str] = mapped_column(String(120), default="")
    # Why the agent shaped the plan this way (shown in the app).
    rationale: Mapped[str] = mapped_column(Text, default="")
    active: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
    # "rule" (fallback) | "ai" (Claude-generated)
    source: Mapped[str] = mapped_column(String(20), default="rule")

    steps: Mapped[list["PlanStep"]] = relationship(
        back_populates="plan",
        cascade="all, delete-orphan",
        order_by="PlanStep.order",
        lazy="selectin",
    )


class PlanStep(Base):
    __tablename__ = "plan_steps"

    plan_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("plans.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(160))
    detail: Mapped[str] = mapped_column(String(255), default="")
    symbol: Mapped[str] = mapped_column(String(60), default="sparkles")
    order: Mapped[int] = mapped_column(Integer, default=0)
    done: Mapped[bool] = mapped_column(Boolean, default=False)
    done_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    plan: Mapped["Plan"] = relationship(back_populates="steps")
