import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr


class ConsentSchema(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    mood_history: bool = True
    ai_memory: bool = True
    voice_storage: bool = False
    model_training: bool = False


class ConsentUpdate(BaseModel):
    mood_history: bool | None = None
    ai_memory: bool | None = None
    voice_storage: bool | None = None
    model_training: bool | None = None


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
    subscription_tier: str = "free"
    subscription_expires_at: datetime | None = None
    age_confirmed_at: datetime | None = None
    ai_disclosure_ack_at: datetime | None = None
    is_admin: bool
    is_active: bool
    created_at: datetime
    consent: ConsentSchema | None = None


class UserUpdate(BaseModel):
    name: str | None = None
    language: str | None = None
    companion: str | None = None
    goals: list[str] | None = None
    motivations: list[str] | None = None
    timezone: str | None = None
    region: str | None = None


class PushTokenUpdate(BaseModel):
    push_token: str


class SubscriptionVerify(BaseModel):
    """A StoreKit 2 signed transaction JWS to verify server-side."""
    signed_transaction: str
