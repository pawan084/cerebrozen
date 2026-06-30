import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class PlanStepOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    title: str
    detail: str
    symbol: str
    order: int
    done: bool


class PlanOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    title: str
    focus: str
    rationale: str
    active: bool
    source: str
    created_at: datetime
    steps: list[PlanStepOut]


class StepToggle(BaseModel):
    done: bool
