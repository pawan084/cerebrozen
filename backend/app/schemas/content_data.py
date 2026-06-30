"""Mood / journal / chat / content / insight / nudge / safety schemas."""
import uuid
from datetime import datetime

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
