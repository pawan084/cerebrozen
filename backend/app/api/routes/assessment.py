"""Self-reflection assessment: taxonomy + personalized conversation topics."""
from fastapi import APIRouter, Depends

from app.core.deps import get_current_user
from app.models.user import User
from app.schemas.assessment import AssessmentStructureOut, TopicsOut, TopicsRequest
from app.services import assessment

router = APIRouter(prefix="/assessment", tags=["assessment"])


@router.get("/structure", response_model=AssessmentStructureOut)
async def structure():
    """The fixed motivations/goals taxonomy the onboarding assessment uses."""
    return assessment.ASSESSMENT_STRUCTURE


@router.post("/topics", response_model=TopicsOut)
async def topics(
    payload: TopicsRequest = TopicsRequest(),
    user: User = Depends(get_current_user),
):
    """Generate tappable conversation topics from the user's self-reflection.

    Falls back to the user's saved onboarding selection when the request omits
    one; always returns a non-empty list (deterministic when no LLM is set).
    """
    motivations = payload.motivations if payload.motivations is not None else (user.motivations or [])
    goals = payload.goals if payload.goals is not None else (user.goals or [])
    language = payload.language or (user.language or "English")
    items, source = await assessment.generate_topics(
        motivations, goals, language=language, count=payload.count
    )
    return TopicsOut(topics=items, source=source)
