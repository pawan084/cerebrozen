"""Mood / journal / chat / content / insight / nudge / safety / sleep schemas."""
import uuid
from datetime import date, datetime, time

from pydantic import BaseModel, ConfigDict, Field


# ── Mood ────────────────────────────────────────────────────────────────
class MoodCreate(BaseModel):
    mood: str = Field(max_length=60)
    note: str = Field(default="", max_length=255)
    symbol: str = Field(default="sparkles", max_length=60)
    intensity: int = Field(default=3, ge=1, le=5)
    trigger: str | None = Field(default=None, max_length=255)


class MoodOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    mood: str
    note: str
    symbol: str
    intensity: int
    trigger: str | None
    created_at: datetime


# ── Journal ─────────────────────────────────────────────────────────────
class JournalCreate(BaseModel):
    title: str = Field(max_length=120)
    body: str = ""
    tags: list[str] = Field(default_factory=list)
    symbol: str = Field(default="book", max_length=60)


class JournalOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    title: str
    body: str
    tags: list[str]
    symbol: str
    risk_level: str
    created_at: datetime


# ── Chat ────────────────────────────────────────────────────────────────
class ChatSend(BaseModel):
    text: str = Field(min_length=1)


class ChatOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    role: str
    text: str
    created_at: datetime


class Suggestion(BaseModel):
    """A quick-reply chip shown under the composer."""
    label: str
    action: str


class WidgetSpec(BaseModel):
    """An inline activity the companion offers in chat (breathing, grounding…)."""
    widget_kind: str
    title: str
    description: str
    params: dict = Field(default_factory=dict)


class ChatReply(BaseModel):
    """Returned after sending: the persisted user message + assistant reply,
    plus an optional inline activity widget and quick-reply suggestions."""
    user_message: ChatOut
    reply: ChatOut
    widget: WidgetSpec | None = None
    suggestions: list[Suggestion] = Field(default_factory=list)


# ── Content ─────────────────────────────────────────────────────────────
class ContentBase(BaseModel):
    title: str = Field(max_length=160)
    subtitle: str = ""
    kind: str = Field(max_length=40)
    symbol: str = "sparkles"
    image_url: str = ""
    duration_min: int = 0
    premium: bool = False
    published: bool = True


class ContentCreate(ContentBase):
    pass


class ContentUpdate(BaseModel):
    title: str | None = None
    subtitle: str | None = None
    kind: str | None = None
    symbol: str | None = None
    image_url: str | None = None
    duration_min: int | None = None
    premium: bool | None = None
    published: bool | None = None


class ContentOut(ContentBase):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    created_at: datetime


# ── Insight ─────────────────────────────────────────────────────────────
class MetricOut(BaseModel):
    label: str
    value: str
    progress: float


class InsightOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    period: str
    headline: str
    summary: str
    metrics: list[MetricOut]


# ── Nudge ───────────────────────────────────────────────────────────────
class NudgeOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    kind: str
    title: str
    body: str
    deeplink: str | None
    scheduled_for: datetime
    status: str


# ── Sleep ───────────────────────────────────────────────────────────────
class SleepLogCreate(BaseModel):
    date: date
    bedtime: time
    wake_time: time
    quality: int = Field(default=3, ge=1, le=5)
    awakenings: int = Field(default=0, ge=0, le=50)
    source: str = Field(default="manual", pattern="^(manual|healthkit)$")
    note: str = Field(default="", max_length=255)


class SleepLogOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    date: date
    bedtime: time
    wake_time: time
    quality: int
    awakenings: int
    source: str
    note: str
    duration_min: int
    created_at: datetime


class SleepSummaryOut(BaseModel):
    """Weekly aggregates; `enough_data` gates every derived number honestly."""
    nights: int
    enough_data: bool
    avg_duration_min: int
    avg_quality: float
    bedtime_consistency_min: int
    trend: str  # improving | steady | declining | not_enough_data


# ── Safety ──────────────────────────────────────────────────────────────
class SafetyEventOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    user_id: uuid.UUID
    source: str
    risk_level: str
    reason: str
    excerpt: str
    resolved: bool
    created_at: datetime
