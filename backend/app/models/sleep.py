from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import Date, ForeignKey, Integer, String, Time, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class SleepLog(Base):
    """One night's sleep-diary entry (morning check-in).

    Manual-first, non-diagnostic awareness data (see docs/SLEEP_TRACKING.md) —
    times are stored wall-clock (no timezone) because the diary describes the
    user's night, not an instant.
    """

    __tablename__ = "sleep_logs"
    __table_args__ = (UniqueConstraint("user_id", "date", name="uq_sleep_logs_user_date"),)

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    # The wake-up date the entry describes; one entry per user per date (upsert).
    date: Mapped[dt.date] = mapped_column(Date)
    bedtime: Mapped[dt.time] = mapped_column(Time)
    wake_time: Mapped[dt.time] = mapped_column(Time)
    # 1–5 felt quality; awakenings = times woken during the night.
    quality: Mapped[int] = mapped_column(Integer, default=3)
    awakenings: Mapped[int] = mapped_column(Integer, default=0)
    source: Mapped[str] = mapped_column(String(20), default="manual")  # manual | healthkit
    note: Mapped[str] = mapped_column(String(255), default="")

    @property
    def duration_min(self) -> int:
        """Night length in minutes; a bedtime after midnight is same-day, else previous evening."""
        bed = self.bedtime.hour * 60 + self.bedtime.minute
        wake = self.wake_time.hour * 60 + self.wake_time.minute
        return wake - bed if wake > bed else (24 * 60 - bed) + wake
