"""Seed the database with an admin user, a demo user, and the content catalogue.

Idempotent: safe to run on every startup. Mirrors the iOS app's dummy content.
"""
from __future__ import annotations

import logging

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import hash_password
from app.models.consent import Consent
from app.models.content import ContentItem
from app.models.user import User
from app.services import nudges

logger = logging.getLogger("cerebro.seed")

_IMG = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1000&q=80"

_CONTENT = [
    ("Rain over quiet hills", "Sleep story · 18 min", "sleep", "moon.stars", 18, False),
    ("Ocean breathing", "Breathwork · 5 min", "breath", "waveform", 5, False),
    ("Deep night drift", "Soundscape · 45 min", "soundscape", "moon.zzz", 45, True),
    ("Morning calm", "Start your day · 6 min", "meditation", "sun.max", 6, False),
    ("Soft focus", "Deep work · 12 min", "meditation", "scope", 12, False),
    ("Body scan", "Release tension · 10 min", "meditation", "figure.mind.and.body", 10, True),
    ("Ease work stress", "7-day plan · Breathing + journaling", "program", "leaf", 0, False),
    ("Sleep deeper", "10-day wind-down program", "program", "moon.stars", 0, True),
    # CBT-I-informed wind-down guide (docs/SLEEP_TRACKING.md) — awareness copy,
    # never diagnosis/treatment claims. Mirrored as the iOS offline fallback.
    ("Keep a steady wake time", "Anchors your body clock — even after a rough night", "wind_down", "alarm", 0, False),
    ("Dim the inputs", "Screens down and lights low, 30 minutes before bed", "wind_down", "moon.haze", 0, False),
    ("Bed is for sleep", "Awake 20+ minutes? Get up, reset gently, return sleepy", "wind_down", "bed.double", 0, False),
    ("Slow the body first", "Two minutes of soft breathing before lights out", "wind_down", "wind", 2, False),
]


async def _ensure_user(db: AsyncSession, email: str, password: str, *, name: str, admin: bool) -> User:
    user = await db.scalar(select(User).where(User.email == email))
    if user:
        return user
    user = User(email=email, hashed_password=hash_password(password), name=name, is_admin=admin)
    user.consent = Consent()
    db.add(user)
    await db.flush()
    await nudges.schedule_default_nudges(db, user)
    logger.info("Seeded %s user: %s", "admin" if admin else "demo", email)
    return user


async def seed(db: AsyncSession) -> None:
    if not settings.seed_demo_data:
        return

    await _ensure_user(db, settings.admin_email, settings.admin_password, name="Admin", admin=True)
    await _ensure_user(db, "pawan@cerebro.app", "demo12345", name="Pawan", admin=False)

    # Additive by title: new catalogue entries reach existing dev DBs on boot,
    # while admin-edited rows are never overwritten.
    existing_titles = set((await db.scalars(select(ContentItem.title))).all())
    added = 0
    for title, subtitle, kind, symbol, duration, premium in _CONTENT:
        if title in existing_titles:
            continue
        db.add(
            ContentItem(
                title=title,
                subtitle=subtitle,
                kind=kind,
                symbol=symbol,
                image_url=_IMG,
                duration_min=duration,
                premium=premium,
            )
        )
        added += 1
    if added:
        logger.info("Seeded %d content items", added)

    await db.commit()
