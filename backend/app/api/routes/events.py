"""Anonymous first-party product analytics (the 2026-07-04 analytics decision).

Zero third-party SDKs. Events are allowlisted, carry no account linkage and no
free-form payload, and only ever leave the database as /admin aggregates. The
endpoint deliberately takes no auth dependency — a bearer token on the request
is ignored, so events can't be joined to users even by accident. Disclosed in
the privacy hub ("anonymous usage counts").
"""
from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.ratelimit import limiter
from app.models.product_event import ProductEvent

router = APIRouter(prefix="/events", tags=["events"])

# The complete event vocabulary. Unknown names are dropped (not errored) so an
# older server never 4xxes a newer client; growing this list is a code change,
# which is the point — every new signal is a deliberate, reviewable decision.
ALLOWED_EVENTS = {
    "onboarding_step",   # step = screen name (welcome … notifications)
    "onboarding_done",
    "paywall_view",
    "paywall_cta",       # step = product id tapped
}

_MAX_BATCH = 20


class EventIn(BaseModel):
    name: str = Field(max_length=60)
    step: str = Field(default="", max_length=60)


class EventBatch(BaseModel):
    anon_id: str = Field(min_length=8, max_length=64)
    source: str = Field(default="ios", pattern=r"^(ios|web|app|android)$")
    events: list[EventIn] = Field(max_length=_MAX_BATCH)


@router.post("", status_code=202)
@limiter.limit("60/minute")
async def ingest(request: Request, payload: EventBatch, db: AsyncSession = Depends(get_db)):
    """Accept a small batch of anonymous events; returns how many were kept."""
    kept = 0
    for e in payload.events:
        if e.name not in ALLOWED_EVENTS:
            continue
        db.add(ProductEvent(anon_id=payload.anon_id, name=e.name,
                            source=payload.source, step=e.step))
        kept += 1
    await db.commit()
    return {"accepted": kept}
