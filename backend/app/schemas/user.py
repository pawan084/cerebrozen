import re
import uuid
import zoneinfo
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, TypeAdapter, field_validator, model_validator

# The regions the crisis directory actually has entries for (plus "" for
# automatic). Kept as a literal mirror of `services/crisis._REGIONS` rather
# than an import so the schema layer stays free of service imports — the test
# in test_input_bounds.py pins the two lists against each other.
KNOWN_REGIONS = {"", "US", "CA", "GB", "IE", "AU", "NZ", "IN"}


class ConsentSchema(BaseModel):
    """What the account currently permits. Every field defaults to False so a
    user with no consent row is reported as having granted nothing — matching
    the model and `consent_allows`. These defaults are what `GET /users/me/consent`
    falls back to, so an optimistic one here would show a settings screen full of
    switches the user never turned on."""

    model_config = ConfigDict(from_attributes=True)
    mood_history: bool = False
    ai_memory: bool = False
    voice_storage: bool = False
    model_training: bool = False
    journal_memory: bool = False
    sleep_history: bool = False


class ConsentUpdate(BaseModel):
    mood_history: bool | None = None
    ai_memory: bool | None = None
    voice_storage: bool | None = None
    model_training: bool | None = None
    journal_memory: bool | None = None
    sleep_history: bool | None = None


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    email: EmailStr
    name: str
    language: str
    companion: str
    goals: list[str]
    motivations: list[str] = []
    timezone: str
    region: str = ""
    #: The tier the server will actually enforce for this caller — which is not
    #: always the stored column. See services/entitlements.user_out: an
    #: organisation can sponsor premium, and the client must be told the same
    #: answer the quota gate will give it.
    subscription_tier: str = "free"
    #: True when the tier above is paid for by an organisation. A client that
    #: ignores this offers a cancel-subscription link for something the member
    #: cannot cancel. Defaults False so an admin listing (which reports the
    #: stored billing truth, not the effective tier) never claims sponsorship.
    sponsored: bool = False
    subscription_expires_at: datetime | None = None
    #: Whether the verification gate will actually refuse this caller — NOT
    #: whether the address is confirmed. The two differ for everyone the gate
    #: exempts: an account that predates it, a paying subscriber, or any
    #: deployment with no SMTP configured. Reporting the raw flag would nag
    #: those people to fix something that is not stopping them, which is the
    #: same mistake `subscription_tier` above exists to avoid — the client must
    #: be told the answer the gate will give, not the column behind it.
    email_verification_required: bool = False
    email_nudges: bool = False
    age_confirmed_at: datetime | None = None
    ai_disclosure_ack_at: datetime | None = None
    is_admin: bool
    is_active: bool
    created_at: datetime
    consent: ConsentSchema | None = None


class UserUpdate(BaseModel):
    # Bounds mirror the column sizes (register C19) — an over-long value used
    # to reach Postgres as a DataError and surface as a 500 instead of a 422.
    name: str | None = Field(default=None, max_length=120)
    language: str | None = Field(default=None, max_length=120)
    companion: str | None = Field(default=None, max_length=60)
    goals: list[str] | None = None
    motivations: list[str] | None = None
    timezone: str | None = Field(default=None, max_length=60)
    region: str | None = Field(default=None, max_length=8)
    email_nudges: bool | None = None

    @field_validator("timezone")
    @classmethod
    def _known_timezone(cls, v: str | None) -> str | None:
        # Register C20: every consumer swallows an unknown zone (`except
        # Exception: tz = UTC`), so a typo silently moved nudges, digests and
        # pattern hour-buckets to UTC with nothing surfaced. Refuse it here,
        # where the user can still see the answer.
        if v is None:
            return v
        try:
            zoneinfo.ZoneInfo(v)
        except (zoneinfo.ZoneInfoNotFoundError, ValueError) as exc:
            raise ValueError("Unknown timezone — use an IANA name like 'Asia/Kolkata'.") from exc
        return v

    @field_validator("region")
    @classmethod
    def _known_region(cls, v: str | None) -> str | None:
        # Register C21: an unknown code silently fell back to the
        # international default — the one place a wrong value costs a user the
        # correct hotline. "" stays valid (automatic).
        if v is None:
            return v
        v = v.strip().upper()
        if v not in KNOWN_REGIONS:
            raise ValueError("Unknown crisis region code.")
        return v


class PushTokenUpdate(BaseModel):
    push_token: str = Field(min_length=8, max_length=512)


class WebPushSubscriptionIn(BaseModel):
    """A browser PushSubscription (endpoint + client encryption keys)."""
    endpoint: str = Field(max_length=1024)
    p256dh: str = Field(max_length=255)
    auth: str = Field(max_length=255)


class WebPushSubscriptionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    endpoint: str
    created_at: datetime


class WebPushStatusOut(BaseModel):
    """What the web client needs before subscribing: whether the server can
    deliver at all (VAPID keys set) and the public application server key."""
    enabled: bool
    public_key: str
    subscriptions: int


class DeviceTokenIn(BaseModel):
    """One native install registering for push.

    `platform` is closed to the two clients that exist; anything else would
    reach a dispatcher that has no provider for it and fail silently at 3am.
    """
    token: str = Field(min_length=8, max_length=512)
    platform: str = Field(pattern="^(ios|android)$")
    app_version: str = Field(default="", max_length=40)


class DeviceTokenOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    platform: str
    app_version: str
    last_seen_at: datetime
    created_at: datetime


class PushStatusOut(BaseModel):
    """What the native client shows in Settings: whether the server can deliver
    to this platform at all, and how many installs are currently registered.
    `enabled: false` means the toggle explains itself instead of lying."""
    enabled: bool
    platform: str
    devices: int


class AttestBody(BaseModel):
    """Optional client-recorded age-confirmation time — the tap happened
    on-device during onboarding, possibly before the first connect."""
    age_confirmed_at: datetime | None = None


class SubscriptionVerify(BaseModel):
    """A StoreKit 2 signed transaction JWS to verify server-side."""
    signed_transaction: str


class PlaySubscriptionVerify(BaseModel):
    """A Google Play purchase: the JSON payload and Play's signature over it.

    Two fields rather than one because Play signs the payload as a detached
    signature — the bytes that were signed must reach the server unchanged, so
    the raw string is sent rather than a re-serialised object.
    """
    purchase_payload: str
    purchase_signature: str


_EMAIL = TypeAdapter(EmailStr)


class TrustedContactIn(BaseModel):
    """The person to reach in a detected crisis.

    The value is validated against the chosen method: this is the one contact
    the product may ever use on someone's behalf in their worst moment, and a
    typo'd address fails silently exactly then. Found on-device 2026-08-03 —
    an adb-mangled "sister%40example.com" saved without complaint.
    """

    name: str = Field(default="", max_length=120)
    method: Literal["email", "sms", "phone"] = "email"
    value: str = Field(default="", max_length=255)
    relationship: str = Field(default="", max_length=60)
    notify_consent: bool = False

    @model_validator(mode="after")
    def _value_matches_method(self) -> "TrustedContactIn":
        self.value = self.value.strip()
        if not self.value:
            # An empty draft may be saved — but consent to contact nobody is a
            # promise the product cannot keep, so it cannot be switched on.
            if self.notify_consent:
                raise ValueError("Add an email or number before turning on crisis contact.")
            return self
        if self.method == "email":
            try:
                _EMAIL.validate_python(self.value)
            except ValueError as exc:
                raise ValueError("That doesn't look like an email address.") from exc
        else:  # sms | phone — digits with the usual separators, at least 7 digits
            if len(re.sub(r"\D", "", self.value)) < 7 or not re.fullmatch(r"[+()\-.\s\d]+", self.value):
                raise ValueError("That doesn't look like a phone number.")
        return self


# NOT TrustedContactIn's subclass: the input validator must never run on rows
# read back from the database — a historical row that predates validation (or
# was written by an older build) should still be readable, not turn GET into
# a 500. What's saved is shown as-is; only new writes are held to the format.
class TrustedContactOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    name: str
    method: str
    value: str
    relationship: str
    notify_consent: bool
