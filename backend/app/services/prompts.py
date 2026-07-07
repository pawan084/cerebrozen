"""Prompt registry — versioned, admin-editable LLM prompts.

Each prompt-owning module registers its in-code default at import time
(``_SYSTEM = prompts.register("agentic_plan", "…")``) and fetches the live
text with ``await prompts.get("agentic_plan")`` right before the LLM call.
An active ``PromptTemplate`` row overrides the default; no row (or any DB
hiccup) falls back to the registered constant, so the LLM path can never
break on registry state — dev/CI run identically with an empty table.

Admin surface: ``/admin/prompts`` (list / new version / activate / revert)
+ the admin dashboard "Prompts" tab. Every edit is a new immutable version,
so rollback is activating an old row — no deploy needed either way.
"""
from __future__ import annotations

import logging

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.prompt import PromptTemplate

logger = logging.getLogger("cerebro.prompts")

# In-code defaults, registered by the owning modules at import time.
_DEFAULTS: dict[str, str] = {}


def register(name: str, template: str) -> str:
    """Register (and return) a prompt's in-code default."""
    _DEFAULTS[name] = template
    return template


def registered() -> dict[str, str]:
    """All known prompt names → their code defaults (for the admin list)."""
    return dict(_DEFAULTS)


def default_for(name: str) -> str:
    return _DEFAULTS.get(name, "")


async def get(name: str, db: AsyncSession | None = None) -> str:
    """The live template: the active DB version if one exists, else the
    registered default. Opens a short-lived session when the caller has none
    (e.g. classifier paths without a db handle); any failure degrades to the
    default so prompt delivery can never take the feature down."""
    try:
        if db is not None:
            override = await _active_template(db, name)
        else:
            from app.core.database import SessionLocal

            async with SessionLocal() as session:
                override = await _active_template(session, name)
    except Exception as exc:  # pragma: no cover - depends on DB availability
        logger.warning("Prompt registry lookup failed for %s: %s", name, exc)
        override = None
    return override if override is not None else default_for(name)


async def _active_template(db: AsyncSession, name: str) -> str | None:
    return await db.scalar(
        select(PromptTemplate.template)
        .where(PromptTemplate.name == name, PromptTemplate.active.is_(True))
        .order_by(PromptTemplate.version.desc())
        .limit(1)
    )
