"""Request schemas for the coaching HTTP surface.

The session-based endpoints (``/v1/sessions/*``) are the current surface; the
legacy ``WebhookRequest`` is retained only for the deprecated ``/v1/webhook``
shim so older callers keep working while they migrate.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field, field_validator

from app.config import MAX_USER_MESSAGE_CHARS


def _cap_message_length(v: Optional[str]) -> Optional[str]:
    """Reject a `message`/`text` field over MAX_USER_MESSAGE_CHARS.

    The session `title` is stored verbatim as the FIRST user message (see
    app/stores/conversation.py), so this one cap bounds both the turn's user
    message AND the derived title. Raising here surfaces a clean 422 with a
    readable detail via FastAPI's built-in RequestValidationError handling —
    no route-level duplication needed.
    """
    if v is not None and len(v) > MAX_USER_MESSAGE_CHARS:
        raise ValueError(
            f"Message exceeds the maximum allowed length of {MAX_USER_MESSAGE_CHARS} characters "
            f"(got {len(v)})."
        )
    return v


class _MessageFields(BaseModel):
    """Shared message-parsing helpers for the turn-bearing requests.

    A request carries a slash ``message`` (e.g. ``/endconversation``) and/or the
    user-typed ``text``; ``user_text()`` returns the real message, ``raw_message()``
    the slash command for intent detection.
    """

    message: Optional[str] = Field(default=None)
    text: Optional[str] = Field(default=None)

    _validate_length = field_validator("message", "text")(_cap_message_length)

    def raw_message(self) -> str:
        return (self.message or "").strip()

    def user_text(self) -> str:
        if self.text and self.text.strip():
            return self.text.strip()
        msg = (self.message or "").strip()
        return "" if msg.startswith("/") else msg


class SessionStartRequest(_MessageFields):
    """Body for ``POST /v1/sessions/start``.

    Mints a new session (or adopts the caller's ``session_id``) and runs the
    first turn. ``user_id`` is optional — it falls back to the JWT ``username``
    claim. No ``bot_name``: the coaching path isn't known until a coaching agent
    runs mid-session.
    """

    user_id: Optional[str] = Field(
        default=None, description="User id; falls back to the JWT username claim."
    )
    session_id: Optional[str] = Field(
        default=None, description="Optional caller-supplied session id; minted when absent."
    )
    metadata: Optional[Dict[str, Any]] = None


class SessionTurnRequest(_MessageFields):
    """Body for ``POST /v1/sessions/{session_id}/turn`` — one turn on an existing
    session (session_id comes from the path)."""

    user_id: Optional[str] = Field(
        default=None, description="User id; falls back to the JWT username claim."
    )
    metadata: Optional[Dict[str, Any]] = None
    hidden: bool = Field(
        default=False,
        description="Mark this turn's user message as UI-hidden (e.g. the "
        "saved|skipped action-ack turn sent right after actions/status calls). "
        "The bot still receives and replies to the text normally; only the stored "
        "user bubble is flagged so /history can omit it from the rendered chat.",
    )


class GenerateTitleRequest(_MessageFields):
    """Body for ``POST /v1/sessions/{session_id}/title`` — LLM-generate (and
    persist) this session's chat title from the user's message.

    Replaces the old behaviour of storing the first user message verbatim as the
    title; see app/llm/title_generator.py.
    """

    user_id: Optional[str] = Field(
        default=None, description="User id; falls back to the JWT username claim."
    )


class HistoryRequest(BaseModel):
    """Body for ``POST /v1/sessions/history`` — the FULL transcript of ONE session.

    Keyed by ``session_id`` (required). ``user_id`` is optional and used only for
    an ownership check; the transcript itself is fetched by session and returned
    in full, exactly as stored — no pagination.

    ``page``/``take`` are accepted for backward compatibility with existing
    callers but are not used to slice the response.
    """

    session_id: str = Field(description="Session id to return the transcript for.")
    user_id: Optional[str] = Field(
        default=None, description="User id; falls back to the JWT username claim."
    )
    page: int = Field(
        default=1, ge=1, description="Unused — accepted for backward compatibility only."
    )
    take: int = Field(
        default=500, ge=1, description="Unused — accepted for backward compatibility only."
    )


class DeleteSessionRequest(BaseModel):
    """Body for ``DELETE /v1/sessions`` — delete one session's conversation history.

    ``user_id`` is NOT accepted here (unlike the read endpoints) — it always comes
    from the JWT, so a caller can only ever delete their own sessions.
    """

    session_id: str = Field(description="Session id to delete.")


class ActionStatusItem(BaseModel):
    """One action's save/delete + optional inline edits — the per-item shape used
    both standalone (single-action body) and inside ``ActionStatusRequest.actions``
    (multi-action body). See ``ActionStatusRequest`` for field meanings.
    """

    action_id: str = Field(description="The action's stable id (from the turn payload).")
    action: str = Field(description='Either "save", "skip", or "delete".')
    roi_metrics: Optional[List[str]] = Field(
        default=None,
        description="Optional re-tag of the action's Development Areas (ROI metrics) "
        "picked in the UI; canonicalised before storing. An action may have multiple.",
    )
    # Inline edit (the card's ✏️): any provided field overwrites the stored one.
    # The action keeps its original action_id so save/delete keep working.
    full_text: Optional[str] = Field(
        default=None, description="Edited action text (I will …). Ignored if blank."
    )
    action_body: Optional[str] = Field(
        default=None, description="Edited action body (everything after the verb)."
    )
    expected_outcome: Optional[str] = Field(
        default=None, description="Edited expected outcome."
    )


class ActionStatusRequest(ActionStatusItem):
    """Body for ``POST /v1/sessions/{session_id}/actions/status`` — the UI flagging
    one or more generated actions as saved or deleted from their right-panel cards.

    Single-action form (unchanged): ``{action_id, action, roi_metrics?, full_text?,
    action_body?, expected_outcome?}``.

    Multi-action form: ``{actions: [{action_id, action, roi_metrics?, ...}, ...]}``.
    When ``actions`` is present it takes over and the top-level ``action_id``/``action``
    (inherited from ``ActionStatusItem`` but unused here) are ignored — every action to
    update, including a single one, goes in the list.
    """

    action_id: str = Field(
        default="", description="The action's stable id. Omit when using `actions`."
    )
    action: str = Field(
        default="", description='Either "save" or "delete". Omit when using `actions`.'
    )
    actions: Optional[List[ActionStatusItem]] = Field(
        default=None,
        description="Multi-action form: update several actions in one call. "
        "Takes over from the top-level action_id/action fields when present.",
    )
    user_id: Optional[str] = Field(
        default=None, description="User id; falls back to the JWT username claim."
    )


class PhaseButtonSelectionRequest(BaseModel):
    """Body for ``POST /v1/sessions/{session_id}/phase-selection``.

    Records which CH phase-transition button the user pressed. This API exists
    because when the user presses "Save & Exit" there is no subsequent coaching
    turn, so the button press would otherwise never be persisted. For "Continue"
    buttons the selection is already conveyed via the next turn's
    ``metadata.session_continued``; calling this endpoint for those too keeps the
    conversation document consistent regardless of button type.

    ``user_selection`` must be one of the values the CH agent emitted in
    ``transition_options`` (e.g. ``"continue_to_phase_2"``,
    ``"continue_to_phase_3"``, ``"save_and_exit"``).
    """

    user_selection: str = Field(
        description=(
            "The user_selection identifier of the button pressed — one of "
            "'continue_to_phase_2', 'continue_to_phase_3', 'save_and_exit'."
        )
    )
    user_id: Optional[str] = Field(
        default=None, description="User id; falls back to the JWT username claim."
    )


class WebhookRequest(BaseModel):
    """DEPRECATED — legacy ``/v1/webhook`` contract. Use ``/v1/sessions/*``.

    Retained so existing webhook callers keep working during migration.
    """

    sender: str = Field(description="User ID (maps to user_id).")
    message: Optional[str] = Field(default=None)
    text: Optional[str] = Field(default=None)
    metadata: Optional[Dict[str, Any]] = None
    session_id: Optional[str] = Field(default=None)
    tot_messages: int = Field(default=15, ge=1)
    message_num: int = Field(default=1, ge=1)
    recipient_id: Optional[str] = None
    message_uuid: Optional[str] = None
    prior_conversation: Optional[Any] = None
    is_text_animation_complete: Optional[Any] = None

    model_config = {"populate_by_name": True}

    _validate_length = field_validator("message", "text")(_cap_message_length)

    def raw_message(self) -> str:
        """Slash command from the message field — used for intent detection."""
        return (self.message or "").strip()

    def user_text(self) -> str:
        """Actual user-typed message from the text field; falls back to message
        when it is not a slash command."""
        if self.text and self.text.strip():
            return self.text.strip()
        msg = (self.message or "").strip()
        return "" if msg.startswith("/") else msg
