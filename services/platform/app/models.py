"""Platform data model: orgs, users, refresh tokens, invitations, demo
requests, and the deletion ledger. No coaching content lives here — that is
the engine's domain, and the "counts never content" rule starts at the schema."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


def _id() -> str:
    return uuid.uuid4().hex


def _now() -> datetime:
    return datetime.now(timezone.utc)


ROLE_USER = "user"
ROLE_ORG_ADMIN = "org_admin"
ROLE_INTERNAL_ADMIN = "internal_admin"
ROLES = {ROLE_USER, ROLE_ORG_ADMIN, ROLE_INTERNAL_ADMIN}


# A "personal" org is the org-of-one minted by consumer self-serve signup
# (POST /auth/signup): the same schema as a B2B tenant, but seats_total=1 and a
# slug under this prefix. The prefix is the marker — so analytics and the
# internal-admin console can tell a solo B2C account from a real customer tenant
# without a schema change — while seats_total=1 naturally caps it at its owner.
PERSONAL_ORG_SLUG_PREFIX = "personal-"


def is_personal_org(org: "Org") -> bool:
    """True for a consumer self-serve org-of-one (see PERSONAL_ORG_SLUG_PREFIX)."""
    return (org.slug or "").startswith(PERSONAL_ORG_SLUG_PREFIX)


# ── consumer plans (B2C freemium) ────────────────────────────────────────────
# A personal account is FREE until it holds a Subscription in an access-granting
# status; a B2B seat and internal staff are ENTERPRISE — the full product, never a
# consumer cap. Crisis/safety and the core daily loop (check-ins, the coach itself,
# breathing/grounding tools) are on in EVERY plan: safety is never paywalled
# (docs/SECURITY.md). Only the paywalled DEPTH toggles live in the map below.
PLAN_FREE = "free"
PLAN_PLUS = "plus"
PLAN_ENTERPRISE = "enterprise"
PLANS = {PLAN_FREE, PLAN_PLUS, PLAN_ENTERPRISE}

SUB_STATUS_ACTIVE = "active"
SUB_STATUS_TRIALING = "trialing"
SUB_STATUS_CANCELED = "canceled"
SUB_STATUS_PAST_DUE = "past_due"
#: Statuses that still grant the paid plan's entitlements.
ACTIVE_SUB_STATUSES = {SUB_STATUS_ACTIVE, SUB_STATUS_TRIALING}

# `coach_daily_limit` / `programs_limit` = None means unlimited.
_PLAN_ENTITLEMENTS: dict[str, dict] = {
    PLAN_FREE: {
        "voice": False,
        "all_programs": False,
        "sleep": False,
        "insights": False,
        "patterns": False,
        "soundscapes": False,
        "journal_memory": False,
        "coach_daily_limit": 5,
        "programs_limit": 1,
    },
    PLAN_PLUS: {
        "voice": True,
        "all_programs": True,
        "sleep": True,
        "insights": True,
        "patterns": True,
        "soundscapes": True,
        "journal_memory": True,
        "coach_daily_limit": None,
        "programs_limit": None,
    },
}
# Enterprise is the full product with no consumer caps — same toggles as Plus.
_PLAN_ENTITLEMENTS[PLAN_ENTERPRISE] = dict(_PLAN_ENTITLEMENTS[PLAN_PLUS])


def entitlements_for(plan: str) -> dict:
    """The feature map a client uses to gate UI. Unknown plan → free (fail safe)."""
    return dict(_PLAN_ENTITLEMENTS.get(plan, _PLAN_ENTITLEMENTS[PLAN_FREE]))


def subscription_grants(subscription: "Subscription", at: "datetime | None" = None) -> bool:
    """Whether a subscription currently entitles its paid plan.

    Active/trialing always grant. A CANCELLED subscription keeps access until its
    paid period ends — cancel means 'won't renew', not 'cut off now' (the standard
    consumer-billing contract). `at` is injectable for deterministic tests."""
    if subscription.status in ACTIVE_SUB_STATUSES:
        return True
    if (
        subscription.status == SUB_STATUS_CANCELED
        and subscription.current_period_end is not None
    ):
        end = subscription.current_period_end
        if end.tzinfo is None:  # SQLite drops tzinfo
            end = end.replace(tzinfo=timezone.utc)
        return end > (at or datetime.now(timezone.utc))
    return False


def resolve_plan(
    org: "Org | None", subscription: "Subscription | None", at: "datetime | None" = None
) -> str:
    """The effective plan for a user, from their org and any subscription.

    Internal staff (no org) and B2B seats are ENTERPRISE by construction; a
    personal account is FREE unless it holds a subscription that currently grants
    (see subscription_grants). Fail safe: an unknown stored plan degrades to free."""
    if org is None or not is_personal_org(org):
        return PLAN_ENTERPRISE
    if subscription is not None and subscription_grants(subscription, at):
        return subscription.plan if subscription.plan in PLANS else PLAN_FREE
    return PLAN_FREE


class Org(Base):
    __tablename__ = "orgs"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    name: Mapped[str] = mapped_column(String(200))
    slug: Mapped[str] = mapped_column(String(80), unique=True, index=True)
    seats_total: Mapped[int] = mapped_column(Integer, default=50)
    # Mirrors the engine default: regulated ON unless the contract says otherwise.
    regulated_mode: Mapped[bool] = mapped_column(Boolean, default=True)
    crisis_region: Mapped[str] = mapped_column(String(8), default="IN")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


# The six DPDP consent categories. The ORDER is the client's render order and the keys
# are a cross-stack contract (apps/android ConsentNotice.CONSENT_KEY_ORDER, iOS, web) —
# renaming one silently unticks a box somebody consented to, so they only ever get added.
#
# The first three govern STORAGE, and the engine enforces them: a category the person has
# not consented to is not written down (see the `consent` claim below, and the engine's
# routers/wellness.py). The last three are honoured by an absence rather than a check:
# there is no voice recording to store and no model being trained on anyone's data.
CONSENT_KEYS = (
    "mood_history",
    "ai_memory",
    "journal_memory",
    "sleep_history",
    "voice_storage",
    "model_training",
)


#: The domain a deleted account's email is scrubbed to (`routers/users.py::delete_me`).
#: The row survives as a tombstone so foreign keys stay valid, which means every query over
#: users has to decide whether it means "people" or "rows" — see `is_tombstone`.
DELETED_EMAIL_DOMAIN = "@deleted.invalid"


def is_tombstone(user: "User") -> bool:
    """A scrubbed row left behind by an erasure, not a person."""
    return (user.email or "").endswith(DELETED_EMAIL_DOMAIN)


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    org_id: Mapped[str | None] = mapped_column(
        ForeignKey("orgs.id"), nullable=True, index=True
    )  # null = CereBroZen internal staff
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(200), default="")
    password_hash: Mapped[str] = mapped_column(String(300))
    role: Mapped[str] = mapped_column(String(20), default=ROLE_USER)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    # ── profile (the app's You tab / Settings) ───────────────────────────────
    companion: Mapped[str] = mapped_column(String(40), default="")
    language: Mapped[str] = mapped_column(String(40), default="")
    region: Mapped[str] = mapped_column(String(8), default="")   # crisis helpline region
    goals: Mapped[str] = mapped_column(Text, default="")         # JSON array, from onboarding
    motivations: Mapped[str] = mapped_column(Text, default="")   # JSON array, from onboarding

    # ── consent (DPDP) ───────────────────────────────────────────────────────
    # DEFAULT FALSE, every one of them. Consent is an act, not an inheritance: a row that
    # nobody has ticked must not read as six yeses, and a person who never saw the notice
    # has not agreed to anything. The app's Privacy & memory screen is where they say yes.
    consent_mood_history: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_ai_memory: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_journal_memory: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_sleep_history: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_voice_storage: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_model_training: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    # The 18+ attestation from onboarding. DPDP treats a child's data differently, and the
    # product's answer is that it is not for children — so the moment they said so is
    # worth having. It is a date, not a birthday: we ask whether, never how old.
    adult_attested_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # ── program enrolment (multi-day journeys) ───────────────────────────────
    # Which program they're on, and when they started it. The current day is DERIVED
    # from the date (catalog.program_payload), not stored — so it cannot drift. Benign
    # preference state, not content: it names an app catalog id, holds nothing personal.
    active_program_id: Mapped[str] = mapped_column(String(40), default="")
    program_started_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # ── trusted contact (crisis) ─────────────────────────────────────────────
    # A THIRD PARTY's details, given by someone else. It is never shown to the org, never
    # exported to HR, and never contacted automatically — the app shows it to the person
    # in a crisis so THEY can reach out. Storing it is a favour to them, not a capability
    # we hold over them.
    trusted_contact_name: Mapped[str] = mapped_column(String(120), default="")
    trusted_contact_method: Mapped[str] = mapped_column(String(20), default="")
    trusted_contact_value: Mapped[str] = mapped_column(String(200), default="")
    trusted_contact_consent: Mapped[bool] = mapped_column(Boolean, default=False)

    def consents(self) -> dict[str, bool]:
        """The six flags as the client's dict — also the JWT `consent` claim."""
        return {key: bool(getattr(self, f"consent_{key}")) for key in CONSENT_KEYS}


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class Invitation(Base):
    __tablename__ = "invitations"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    org_id: Mapped[str] = mapped_column(ForeignKey("orgs.id"), index=True)
    email: Mapped[str] = mapped_column(String(320), index=True)
    role: Mapped[str] = mapped_column(String(20), default=ROLE_USER)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    accepted_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class Subscription(Base):
    """A personal account's consumer plan (B2C). One row per org, and in practice
    only ever a PERSONAL org — B2B orgs are ENTERPRISE by construction and never get
    a row (an absent row = free tier). Holds NO payment data beyond an opaque
    provider reference: card details live with the payment provider, never here,
    the same way coaching content never lives on the platform."""

    __tablename__ = "subscriptions"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    org_id: Mapped[str] = mapped_column(
        ForeignKey("orgs.id"), unique=True, index=True
    )
    plan: Mapped[str] = mapped_column(String(20), default=PLAN_PLUS)
    status: Mapped[str] = mapped_column(String(20), default=SUB_STATUS_ACTIVE)
    provider: Mapped[str] = mapped_column(String(20), default="mock")  # mock|stripe|play
    provider_ref: Mapped[str] = mapped_column(String(200), default="")
    current_period_end: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class PasswordReset(Base):
    """A single-use, short-lived password-reset token. Only the hash is stored (like
    Invitation / RefreshToken); the raw token rides in the emailed link and is never
    persisted. `used_at` makes it one-shot; expiry makes a leaked link go stale."""

    __tablename__ = "password_resets"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class OtpCode(Base):
    """A short-lived, single-use email one-time code for passwordless sign-in. Only the
    hash is stored; `attempts` is capped so a guessable 6-digit code can't be brute-forced,
    and expiry + `used_at` bound its life to one use in a small window."""

    __tablename__ = "otp_codes"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    email: Mapped[str] = mapped_column(String(320), index=True)
    code_hash: Mapped[str] = mapped_column(String(64), index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class ConsentEvent(Base):
    """An append-only record of one consent toggle — which category changed to what,
    and when. Content-free by construction (a category name + a boolean), so it lives on
    the platform without breaking 'counts, never content', and gives the person an
    auditable history of their own DPDP choices."""

    __tablename__ = "consent_events"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    key: Mapped[str] = mapped_column(String(40))  # one of CONSENT_KEYS
    value: Mapped[bool] = mapped_column(Boolean)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class DemoRequest(Base):
    __tablename__ = "demo_requests"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    name: Mapped[str] = mapped_column(String(200))
    email: Mapped[str] = mapped_column(String(320))
    company: Mapped[str] = mapped_column(String(200))
    size: Mapped[str] = mapped_column(String(40), default="")
    message: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(20), default="new")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


EVENT_KINDS = {"session_started", "session_completed", "action_saved", "action_completed"}

# Controlled coaching-theme vocabulary (the "Development Area" catalogue). A coaching beat
# MAY carry one of these as its `theme` — a CONTROLLED label, never free text, so "counts,
# never content" survives the new dimension: a theme is which development area a person is
# working on, never a word they said. Anything not in this set is dropped on ingest, exactly
# like an unknown event kind. Owned by the platform (the analytics vocabulary lives here);
# mirrors the engine's default ROI-metric catalogue.
THEME_VOCABULARY = {
    "Mental & emotional state", "Inspiration", "Managing conflicts", "Future orientation",
    "Creativity & innovation", "Decision making", "Inclusion", "Contribution & giving back",
    "Delegation", "Clarity of purpose", "Upskilling", "Level of stress", "Collaboration",
    "Self-confidence", "Job satisfaction", "Goal setting", "Resilience", "Intellectual growth",
    "Ownership", "Building relationships", "Continuous improvement & learning", "Communication",
}

# Pre-auth product-funnel beats (the app's anonymous /events contract). Names
# not in this set are dropped on ingest, never stored.
FUNNEL_EVENTS = {"onboarding_step", "onboarding_done"}


class FunnelEvent(Base):
    """One anonymous funnel beat. Deliberately has NO org/user columns — the
    client sends a random install id and no auth header, so these rows can
    never join accounts. That is a schema property, same as ActivityEvent's
    kind-only rule."""

    __tablename__ = "funnel_events"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    anon_id: Mapped[str] = mapped_column(String(64), index=True)
    source: Mapped[str] = mapped_column(String(16), default="")
    name: Mapped[str] = mapped_column(String(32), index=True)
    step: Mapped[str] = mapped_column(String(64), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class ActivityEvent(Base):
    """One coaching-activity beat — KIND ONLY, never content. This table is
    the entire substrate of HR analytics; keeping content out of it is what
    makes "counts never content" a schema property rather than a promise."""

    __tablename__ = "activity_events"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    org_id: Mapped[str] = mapped_column(String(32), index=True)
    user_id: Mapped[str] = mapped_column(String(32), index=True)
    kind: Mapped[str] = mapped_column(String(32), index=True)
    # Optional controlled theme (a Development Area from THEME_VOCABULARY). Nullable so the
    # dimension is backward-compatible: beats without it still count for every kind metric.
    theme: Mapped[str | None] = mapped_column(String(48), index=True, nullable=True, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class DeletionLedger(Base):
    """Proof a deletion happened, holding no PII: the email survives only as a
    salted hash so a later 'did you really delete me?' can be answered."""

    __tablename__ = "deletion_ledger"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    user_id: Mapped[str] = mapped_column(String(32), index=True)
    org_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    email_hash: Mapped[str] = mapped_column(String(64))
    requested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
