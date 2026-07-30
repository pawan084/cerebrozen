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
class DayGuide(BaseModel):
    """One day of a program (W15): the title + body served as `today_guide`
    on /programs/active for the enrollment's current day."""

    title: str = Field(max_length=160)
    body: str = ""


class ContentBase(BaseModel):
    title: str = Field(max_length=160)
    subtitle: str = ""
    kind: str = Field(max_length=40)
    symbol: str = "sparkles"
    image_url: str = ""
    # Relative "/media/..." (backend-minted) or absolute URL; empty = no narration.
    audio_url: str = ""
    duration_min: int = 0
    premium: bool = False
    published: bool = True


class ContentCreate(ContentBase):
    narration_script: str = ""
    # Per-day program structure (W17); None for non-programs.
    day_guides: list[DayGuide] | None = None


class ContentUpdate(BaseModel):
    title: str | None = None
    subtitle: str | None = None
    kind: str | None = None
    symbol: str | None = None
    image_url: str | None = None
    audio_url: str | None = None
    narration_script: str | None = None
    duration_min: int | None = None
    premium: bool | None = None
    published: bool | None = None
    # Omitted = untouched (exclude_unset PATCH); explicit null clears the guides.
    day_guides: list[DayGuide] | None = None


class ContentOut(ContentBase):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    created_at: datetime


class AdminContentOut(ContentOut):
    """Admin CMS payload — carries the narration script (and generation stamp)
    the public catalogue omits."""

    narration_script: str = ""
    audio_generated_at: datetime | None = None
    # Per-day program structure; None for non-programs and legacy rows.
    day_guides: list[DayGuide] | None = None


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
    """A row in the admin review queue.

    Deliberately carries NO excerpt. The queue is a triage surface — listing it
    would spray verbatim private journal/chat text across an ops screen on every
    page load, for every row, whether or not anyone needed to read it. A reviewer
    who needs the text fetches one row from ``/admin/safety/{id}/excerpt``, which
    is a deliberate, per-row, logged act. ``excerpt_chars`` is here so the UI can
    show that there IS text (and how much) without disclosing it.
    """

    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    user_id: uuid.UUID
    source: str
    risk_level: str
    reason: str
    excerpt_chars: int = 0
    resolved: bool
    resolved_by: uuid.UUID | None = None
    resolved_at: datetime | None = None
    resolution_note: str = ""
    created_at: datetime


class SafetyExcerptOut(BaseModel):
    """The verbatim text behind one flagged event — fetched on demand only."""

    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    excerpt: str


# ── Context memory ──────────────────────────────────────────────────────
class MemoryOut(BaseModel):
    """One remembered item the user can act on individually.

    ``source`` is surfaced so a client can be honest about provenance — the
    user's own words read differently from something they merely approved.
    """

    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    body: str
    source: str
    salience: float
    expires_at: datetime | None = None
    dismissed_at: datetime | None = None
    created_at: datetime
    updated_at: datetime | None = None


class MemoryCreate(BaseModel):
    body: str = Field(min_length=1, max_length=2000)
    source: str = "manual"
    salience: float = Field(default=0.5, ge=0.0, le=1.0)


class MemoryUpdate(BaseModel):
    """All fields optional — a PATCH may rewrite the text, re-weight it, or
    hide it without deleting."""

    body: str | None = Field(default=None, min_length=1, max_length=2000)
    salience: float | None = Field(default=None, ge=0.0, le=1.0)
    dismissed: bool | None = None


class PatternSuppress(BaseModel):
    """Hide one computed pattern. Identified by its statement — patterns are
    derived on the fly and have no id of their own."""

    statement: str = Field(min_length=1, max_length=2000)


# ── Safety plan ─────────────────────────────────────────────────────────
class SafetyPlanOut(BaseModel):
    """A saved plan. Every section is plain text the user wrote — the API
    never scores, ranks or interprets them."""

    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    version: int
    warning_signs: str
    internal_coping: str
    social_distractors: str
    social_support: str
    professionals: str
    means_safety: str
    notes: str
    archived_at: datetime | None = None
    created_at: datetime


class SafetyPlanUpdate(BaseModel):
    """All sections optional so the guided flow can save one at a time;
    unset fields carry over from the live version rather than blanking it."""

    warning_signs: str | None = Field(default=None, max_length=4000)
    internal_coping: str | None = Field(default=None, max_length=4000)
    social_distractors: str | None = Field(default=None, max_length=4000)
    social_support: str | None = Field(default=None, max_length=4000)
    professionals: str | None = Field(default=None, max_length=4000)
    means_safety: str | None = Field(default=None, max_length=4000)
    notes: str | None = Field(default=None, max_length=4000)


# ── Recommendations ─────────────────────────────────────────────────────
class RecommendationOut(BaseModel):
    """A suggested practice plus the pattern that prompted it. `reason` is
    never omitted — a suggestion with no visible basis is what the Pattern
    Dashboard exists to avoid."""

    id: uuid.UUID
    slug: str
    title: str
    body: str
    action: str
    reason: str
    status: str
