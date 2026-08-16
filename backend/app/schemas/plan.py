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
    # WHEN it was ticked, not just that it was. The column has always existed
    # (models/plan.PlanStep.done_at, set by the toggle route); it simply was
    # never serialized, so no client could tell "finished this morning" from
    # "finished last Tuesday" — Android's Home wanted exactly that to say "one
    # thing done today" honestly. Null for a step that is not done, and for
    # rows ticked before this field shipped.
    done_at: datetime | None = None


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
