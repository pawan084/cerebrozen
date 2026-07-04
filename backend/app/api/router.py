from fastapi import APIRouter

from app.api.routes import (
    admin,
    assessment,
    auth,
    chat,
    content,
    events,
    insights,
    journal,
    moods,
    oracle,
    plans,
    sleep,
    users,
    voice,
    waitlist,
    webhooks,
)

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(users.router)
api_router.include_router(assessment.router)
api_router.include_router(moods.router)
api_router.include_router(sleep.router)
api_router.include_router(journal.router)
api_router.include_router(chat.router)
api_router.include_router(plans.router)
api_router.include_router(content.router)
api_router.include_router(insights.router)
api_router.include_router(oracle.router)
api_router.include_router(voice.router)
api_router.include_router(waitlist.router)
api_router.include_router(events.router)
api_router.include_router(admin.router)
api_router.include_router(webhooks.router)
