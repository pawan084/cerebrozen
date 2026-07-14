"""Session identity helpers — minting session ids and resolving the user id.

`session_id` is an opaque UUID (no user_id/bot_name embedded): a user can have
many sessions, and a fresh id means a fresh checkpointer thread (so starting or
restarting a session naturally isolates its graph state).

`resolve_user_id` prefers an explicit payload `sender`, falling back to the
JWT `username` claim — so authenticated callers needn't repeat their id.
"""

from __future__ import annotations

import uuid
from typing import Any, Dict, Optional


def mint_session_id() -> str:
    """A fresh, opaque session id (uuid4 hex)."""
    return uuid.uuid4().hex


def user_id_from_claims(claims: Optional[Dict[str, Any]]) -> str:
    """Pull the user id (Mongo ObjectId string) from JWT claims — `user.username`,
    with a few flatter spellings accepted as fallbacks. Empty string when absent."""
    if not isinstance(claims, dict):
        return ""
    user = claims.get("user")
    if isinstance(user, dict):
        uid = user.get("username") or user.get("userName")
        if uid:
            return str(uid)
    return str(claims.get("username") or claims.get("userName") or "")


def resolve_user_id(
    user_id: Optional[str], claims: Optional[Dict[str, Any]] = None
) -> str:
    """User id from the payload `user_id` if given, else the JWT claim."""
    if user_id and user_id.strip():
        return user_id.strip()
    return user_id_from_claims(claims)
