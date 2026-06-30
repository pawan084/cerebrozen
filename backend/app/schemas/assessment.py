"""Self-reflection assessment + conversation-topic schemas."""
from pydantic import BaseModel, Field


class AssessmentStructureOut(BaseModel):
    """The fixed taxonomy the onboarding assessment renders from."""
    motivations: dict[str, list[str]]
    goals: dict[str, list[str]]


class TopicsRequest(BaseModel):
    """Optional explicit selection. When omitted, the caller's saved
    onboarding choices (``user.motivations`` / ``user.goals``) are used —
    so onboarding can preview topics before anything is persisted."""
    motivations: list[str] | None = None
    goals: list[str] | None = None
    language: str | None = None
    count: int = Field(default=8, ge=4, le=12)


class Topic(BaseModel):
    id: int
    topic: str


class TopicsOut(BaseModel):
    topics: list[Topic]
    source: str  # "ai" | "rule"
