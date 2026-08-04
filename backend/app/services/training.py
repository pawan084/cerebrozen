"""The one gate any model-training use of user data must pass through.

Register C5 (finding 66): ``model_training`` was collected at onboarding,
stored, exported and shown in admin — and read at zero sites. A consent
category nobody enforces is decoration, and under DPDP an itemized category
implies a purpose the controller actually honours.

There is **no training pipeline today**, and this module does not create one.
What it creates is the seam: if one is ever built, it takes its corpus from
here, so the gate cannot be forgotten — the alternative (a future exporter
querying users directly) is exactly how an unenforced flag stays unenforced.

The gate is deliberately conservative in the same way :func:`consent_allows`
is: a missing consent row is not consent.
"""
from __future__ import annotations

from collections.abc import Iterable

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.consent import Consent, consent_allows
from app.models.user import User

TRAINING_FLAG = "model_training"


def may_train_on(user: User) -> bool:
    """Whether this user's content may be used to improve models."""
    return consent_allows(user, TRAINING_FLAG)


def filter_trainable(users: Iterable[User]) -> list[User]:
    """Keep only the users who opted in. The list a corpus builder may read."""
    return [u for u in users if may_train_on(u)]


async def trainable_user_ids(db: AsyncSession) -> list:
    """Ids of every user who switched ``model_training`` on.

    A corpus builder must start here rather than from ``select(User)``; the
    join is the enforcement.
    """
    rows = await db.scalars(
        select(Consent.user_id).where(Consent.model_training.is_(True))
    )
    return list(rows)
