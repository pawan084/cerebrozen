from __future__ import annotations

import uuid
from datetime import date, datetime

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class Goal(Base):
    """Something the user is working towards, in their own words.

    Onboarding has always captured goals — as strings on the profile that never
    became anything. This makes one addressable, so it can be tracked, broken
    into steps, and finished.

    `decompose` turns a goal into plan steps through the existing agentic
    planner, which is the point: goals feed the machinery already here rather
    than adding a parallel to-do list.
    """

    __tablename__ = "goals"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    title: Mapped[str] = mapped_column(String(160))
    why: Mapped[str] = mapped_column(Text, default="")
    # active | achieved | released. "Released" rather than "abandoned" is
    # deliberate: letting a goal go is a legitimate outcome, not a failure, and
    # the vocabulary shouldn't imply otherwise.
    status: Mapped[str] = mapped_column(String(16), default="active", index=True)
    resolved_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )


class Habit(Base):
    """A small thing the user chose to repeat.

    Deliberately NOT streak-shaped in the schema: completions are dated rows, so
    the UI can show a gentle "days you showed up" without the data model itself
    encoding a chain that breaks. The existing streak rules already forgive a
    missed day; nothing here should be stricter than that.
    """

    __tablename__ = "habits"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    title: Mapped[str] = mapped_column(String(160))
    # A cue, in the user's words — "after I brush my teeth". Implementation
    # intentions are the one habit mechanism with good evidence behind them.
    cue: Mapped[str] = mapped_column(String(255), default="")
    # 1–7 days a week the user is aiming for. Aspiration, never enforced.
    target_per_week: Mapped[int] = mapped_column(Integer, default=7)
    archived: Mapped[bool] = mapped_column(Boolean, default=False, index=True)


class HabitCompletion(Base):
    """One day a habit happened. Unique per habit per day — tapping twice is
    not two days."""

    __tablename__ = "habit_completions"
    __table_args__ = (UniqueConstraint("habit_id", "day", name="uq_habit_day"),)

    habit_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("habits.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    day: Mapped[date] = mapped_column(Date, index=True)
