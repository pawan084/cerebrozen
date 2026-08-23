"""Mood / journal / chat / content / insight / nudge / safety / sleep schemas."""
import uuid
from datetime import date, datetime, time, timedelta
from datetime import timezone as dt_timezone

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


def _require_web_url(v: str) -> str:
    """Content asset URLs must be renderable, not runnable (register E41):
    whatever an operator pastes is persisted and served to every client for
    rendering, so only http(s) and backend-relative paths pass — a
    `javascript:`/`data:` value is refused at the door."""
    v = v.strip()
    if v and not (v.startswith("/") or v.startswith("http://") or v.startswith("https://")):
        raise ValueError("URLs must be http(s) or a backend-relative path like /media/…")
    return v


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
    # Generous for real writing (~10k words) while refusing arbitrary blobs
    # against the unbounded Text column (register C18).
    body: str = Field(default="", max_length=50_000)
    tags: list[str] = Field(default_factory=list, max_length=20)
    symbol: str = Field(default="book", max_length=60)

    @field_validator("tags")
    @classmethod
    def _bounded_tags(cls, v: list[str]) -> list[str]:
        if any(len(t) > 60 for t in v):
            raise ValueError("tags must be 60 characters or fewer")
        return v


class JournalOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    title: str
    body: str
    tags: list[str]
    symbol: str
    risk_level: str
    # Region-aware hotlines, populated on a POST that scored elevated/crisis
    # (register C70): /chat and /oracle both answer risk with resources, and
    # this was the one path where clients had to hand-mirror the directory.
    resources: dict | None = None
    created_at: datetime


# ── Chat ────────────────────────────────────────────────────────────────
class ChatSend(BaseModel):
    # Bounded because the text feeds the LLM prompt, the transcript context
    # and a Text column (register C16). Every client composer caps well below
    # this (Android at 2000); the server stops trusting them to.
    text: str = Field(min_length=1, max_length=4000)


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
    # Bounds mirror the column sizes (register C24) — an over-long admin paste
    # used to 500 as a Postgres DataError instead of 422ing.
    title: str = Field(max_length=160)
    subtitle: str = Field(default="", max_length=255)
    kind: str = Field(max_length=40)
    symbol: str = Field(default="sparkles", max_length=60)
    image_url: str = Field(default="", max_length=1024)
    # Relative "/media/..." (backend-minted) or absolute URL; empty = no narration.
    audio_url: str = Field(default="", max_length=1024)
    # Optional looping scene video behind the item; empty = generative artwork.
    video_url: str = Field(default="", max_length=1024)
    duration_min: int = 0
    premium: bool = False
    published: bool = True

    @field_validator("image_url", "audio_url", "video_url")
    @classmethod
    def _web_url_only(cls, v: str) -> str:
        return _require_web_url(v)


class ContentCreate(ContentBase):
    narration_script: str = ""
    # Per-day program structure (W17); None for non-programs.
    day_guides: list[DayGuide] | None = None


class ContentUpdate(BaseModel):
    # Same column-size bounds as ContentBase (register C24).
    title: str | None = Field(default=None, max_length=160)
    subtitle: str | None = Field(default=None, max_length=255)
    kind: str | None = Field(default=None, max_length=40)
    symbol: str | None = Field(default=None, max_length=60)
    image_url: str | None = Field(default=None, max_length=1024)
    audio_url: str | None = Field(default=None, max_length=1024)
    video_url: str | None = Field(default=None, max_length=1024)
    narration_script: str | None = None

    @field_validator("image_url", "audio_url", "video_url")
    @classmethod
    def _web_url_only(cls, v: str | None) -> str | None:
        return v if v is None else _require_web_url(v)
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

    @field_validator("date")
    @classmethod
    def _plausible_night(cls, v: date) -> date:
        # Register C26: a client-clock bug (or hostile POST) could write a
        # night in 1970 or 2099, which then anchored weekly summaries, trends
        # and the wind-down nudge. Tomorrow is allowed for clock skew; two
        # years of backfill is generous for any real diary.
        #
        # The bound is deliberately loose by one further day in each direction,
        # and that slack is load-bearing rather than sloppy. A pydantic schema
        # has no user, so it cannot ask `core/localtime` whose day this is — it
        # only has the container's UTC date. Local dates run from UTC-12 to
        # UTC+14, so a member's "today" can legitimately sit a day either side
        # of UTC's. Without the slack this validator rejected a real Asia/
        # Kolkata member's tomorrow for the 5.5 hours a day when IST is already
        # on the next date — the same off-by-one-day-east class as C59-C65,
        # which this line survived. It also failed
        # `test_input_bounds::test_sleep_rejects_implausible_dates` in that same
        # window, since the suite asks the *account* what day it is.
        #
        # Precision is not this check's job: it exists to reject 1970 and 2099,
        # and the exact day boundary belongs to the per-user code that has a
        # timezone to consult. Widening keeps C26 true in every zone.
        today = datetime.now(dt_timezone.utc).date()
        if v > today + timedelta(days=2):      # 1 skew + 1 timezone
            raise ValueError("That date is in the future.")
        if v < today - timedelta(days=731):    # 730 backfill + 1 timezone
            raise ValueError("That date is too far in the past to record.")
        return v

    @model_validator(mode="after")
    def _not_a_zero_minute_night(self) -> "SleepLogCreate":
        # Register C27: bedtime == wake_time yields duration_min == 0, which
        # was averaged into avg_duration_min and the sleep-mood pairing as if
        # it were data.
        if self.bedtime == self.wake_time:
            raise ValueError("Bedtime and wake time are the same — that isn't a night we can record.")
        return self


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
    # The resolver as a human-readable identity (register E54) — populated by
    # the list route; the UUID above stays for exactness.
    resolved_by_email: str | None = None
    resolved_at: datetime | None = None
    resolution_note: str = ""
    #: Whether a trusted contact was actually REACHED — not whether one was
    #: attempted. Both senders swallow their own failures by design, so this
    #: used to be true for a contact nobody ever reached.
    escalated: bool = False
    escalated_at: datetime | None = None
    #: The outcome in short tokens ("ops_alerted,contact_notify_failed"), so a
    #: reviewer sees that nobody was reached rather than assuming somebody was.
    escalation_note: str = ""
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


# ── Goals + habits (the things the user defines) ────────────────────────
class GoalOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    title: str
    why: str
    status: str
    created_at: datetime


class GoalCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    why: str = Field(default="", max_length=2000)


class GoalUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=160)
    why: str | None = Field(default=None, max_length=2000)
    # active | achieved | released — "released" is a real outcome, not a failure.
    status: str | None = None


class HabitOut(BaseModel):
    """`recent_days` is a 7-day window, not a streak. The absence of a streak
    field is deliberate: the schema shouldn't be able to say "you broke it"."""

    id: uuid.UUID
    title: str
    cue: str
    target_per_week: int
    archived: bool
    recent_days: list[str] = Field(default_factory=list)
    done_today: bool = False


class HabitCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    cue: str = Field(default="", max_length=255)
    target_per_week: int = Field(default=7, ge=1, le=7)


class HabitUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=160)
    cue: str | None = Field(default=None, max_length=255)
    target_per_week: int | None = Field(default=None, ge=1, le=7)
    archived: bool | None = None


# ── Agent audit trail ───────────────────────────────────────────────────
class AgentActionOut(BaseModel):
    """One write the Oracle proposed and what the user decided.

    No tool arguments: `save_journal` carries the journal body, and copying it
    here would put private text in a second table with its own retention story.
    `summary` is the same line the user already saw on the confirm card.
    """

    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    tool: str
    summary: str
    status: str
    decided_at: datetime | None = None
    created_at: datetime
