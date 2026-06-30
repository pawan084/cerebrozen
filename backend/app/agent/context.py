"""Request-scoped context for Oracle tools.

LangGraph tools are plain callables with no access to the FastAPI request, so we
pass the current DB session + user id (and a sink for inline widgets the tools
emit) through context variables. The Oracle route sets these at the start of
every request — including the /confirm resume request, since a paused write tool
finishes executing there.
"""
from __future__ import annotations

import contextvars
import uuid
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession

current_db: contextvars.ContextVar[AsyncSession] = contextvars.ContextVar("current_db")
current_user_id: contextvars.ContextVar[uuid.UUID] = contextvars.ContextVar("current_user_id")
# Inline activity widgets emitted by tools during a run; drained by the route.
emitted_widgets: contextvars.ContextVar[list[dict[str, Any]]] = contextvars.ContextVar("emitted_widgets")
