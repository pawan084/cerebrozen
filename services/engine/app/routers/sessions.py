"""Session-scoped coaching surface — the current API (webhook is deprecated).

    POST /v1/sessions/start?stream=                 -> mint session_id + run first turn
    POST /v1/sessions/{session_id}/turn?stream=     -> run a subsequent turn
    POST /v1/sessions/{session_id}/title            -> LLM-generate + persist the chat title
    POST /v1/sessions/history                        -> one session's paged transcript (body)
    GET  /v1/sessions/{session_id}/history           -> same transcript, session_id in path
    GET  /v1/sessions/{session_id}/latest-response   -> session's most recent bot message
    GET  /v1/sessions/{session_id}/actions-insights  -> that session's actions/insights
    DELETE /v1/sessions                              -> delete one session's history (body)

`?stream=true` streams the reply token-by-token as SSE (status / token / done /
error); `?stream=false` (default) returns the full result as one JSON body — the
same payload the streaming `done` event carries. JWT-protected; `user_id` comes
from the payload `sender` or the JWT `username` claim.
"""

from __future__ import annotations

import asyncio
import json
import logging
import threading
from typing import Any, AsyncGenerator, Callable, Dict, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse, StreamingResponse

from app.actions_insights import get_actions_insights_service
from app.auth import require_auth
from app.ratelimit import limit_start, limit_turn
from app.request_context import ctx_session_id, ctx_user_id, request_id as _req_id
from app.roi_metrics import canonical_roi_metrics
from app.history import get_history_service
from app.llm.title_generator import generate_chat_title
from app.schemas import (
    ActionStatusRequest,
    DeleteSessionRequest,
    GenerateTitleRequest,
    HistoryRequest,
    PhaseButtonSelectionRequest,
    SessionStartRequest,
    SessionTurnRequest,
)
from app.service import get_service
from app.session import resolve_user_id, user_id_from_claims
from app.stores import agentic, conversation

logger = logging.getLogger("cerebrozen.sessions")
router = APIRouter(prefix="/v1/sessions", tags=["sessions"])


# -- shared SSE plumbing ------------------------------------------------------


def _sse_response(run: Callable[..., Dict[str, Any]]) -> StreamingResponse:
    """Wrap a blocking turn fn (taking on_status/on_token) as an SSE stream."""
    # Capture correlation IDs now, in the async context where the router already
    # set them.  We re-stamp them inside the worker thread because
    # BaseHTTPMiddleware may reset the ContextVars before the stream body runs.
    #
    # ctx_org_id IS ONE OF THEM, and it was the one missing. It is not a correlation id —
    # it is the tenancy key every store writes with — and losing it meant `current_org()`
    # fell back to DEFAULT_ORG inside the worker, so EVERY streamed turn recorded its
    # conversation under org "default" instead of the caller's. Reads run in the request
    # context with the real org, so nothing could ever find them again: /v1/sessions,
    # /v1/sessions/resumable and the history all came back empty for every real tenant, and
    # the Resume pill could not appear. Silent, because the writes succeeded.
    from app.request_context import ctx_session_id, ctx_user_id
    from app.request_context import request_id as _req_id_var
    from app.tenancy import ctx_org_id as _org_var
    _snap_rid = _req_id_var.get()
    _snap_uid = ctx_user_id.get()
    _snap_sid = ctx_session_id.get()
    _snap_org = _org_var.get()

    async def _gen() -> AsyncGenerator[str, None]:
        queue: asyncio.Queue = asyncio.Queue()
        loop = asyncio.get_event_loop()

        def _put(event) -> None:
            loop.call_soon_threadsafe(queue.put_nowait, event)

        def _worker() -> None:
            # Re-stamp correlation IDs in this thread's copy of the context.
            from app.request_context import ctx_session_id as _cs, ctx_user_id as _cu
            from app.request_context import request_id as _ri
            from app.tenancy import ctx_org_id as _co
            if _snap_rid:
                _ri.set(_snap_rid)
            if _snap_uid:
                _cu.set(_snap_uid)
            if _snap_sid:
                _cs.set(_snap_sid)
            # The tenancy key. Without it every store in this thread writes to DEFAULT_ORG.
            if _snap_org:
                _co.set(_snap_org)
            try:
                result = run(
                    on_status=lambda msg: _put({"type": "status", "msg": msg}),
                    on_token=lambda text: _put({"type": "token", "text": text}),
                    # Structured node lifecycle (start/end per graph node) — the flow
                    # view animates the live path from these. Chat clients ignore them.
                    on_node=lambda ev: _put({"type": "node", **ev}),
                )
                _put({"type": "done", **result})
            except Exception as exc:  # noqa: BLE001
                logger.exception("sessions.stream_error")
                _put({"type": "error", "detail": str(exc)})
            finally:
                _put(None)  # sentinel

        threading.Thread(target=_worker, daemon=True).start()

        while True:
            event = await queue.get()
            if event is None:
                break
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        _gen(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


async def _run_or_stream(
    stream: bool, run: Callable[..., Dict[str, Any]]
) -> Any:
    """Stream the turn as SSE, or run it in a worker thread and return one JSON body."""
    if stream:
        return _sse_response(run)
    try:
        result = await run_in_threadpool(run)
    except HTTPException:
        # A deliberate 4xx must survive. The broad catch below was swallowing the
        # HTTPExceptions the service raises INSIDE the worker thread — so
        # `edit_last_message`'s documented `400 No turn to edit` reached the client as
        # `500 {"detail": "400: No turn to edit."}`. The status code is the API's contract:
        # a client can retry a 400 by fixing its request, and can only page someone about a
        # 500. Turning one into the other is not a cosmetic bug, it is a lie about whose
        # fault the failure was. (routers/prompts.py already re-raises like this.)
        raise
    except Exception as exc:  # noqa: BLE001
        logger.exception("sessions.error")
        return JSONResponse(status_code=500, content={"type": "error", "detail": str(exc)})
    return JSONResponse(content=result)


# -- coaching endpoints -------------------------------------------------------


@router.post("/start", dependencies=[Depends(limit_start)])
async def start_session(
    request: SessionStartRequest,
    stream: bool = False,
    claims: dict = Depends(require_auth),
):
    """Mint (or adopt) a session_id and run the first turn. The response/`done`
    event carries `session_id` — the client stores it and sends it on each turn."""
    user_id = resolve_user_id(request.user_id, claims)
    ctx_user_id.set(user_id)
    # session_id is minted inside the service; it sets ctx_session_id there.
    return await _run_or_stream(
        stream,
        lambda on_status=None, on_token=None, on_node=None: get_service().start_session(
            user_id, request, on_status=on_status, on_token=on_token, on_node=on_node
        ),
    )


@router.post("/{session_id}/turn", dependencies=[Depends(limit_turn)])
async def run_turn(
    session_id: str,
    request: SessionTurnRequest,
    stream: bool = False,
    edit: bool = False,
    claims: dict = Depends(require_auth),
):
    """Run one coaching turn on an existing session.

    `edit=true` instead REPLACES the last user message with `text` and regenerates
    the reply (LangGraph time-travel fork) — same request/response shape. Edits are
    rejected on ended sessions (409) or when there's no user message to edit (400).
    """
    user_id = resolve_user_id(request.user_id, claims)
    ctx_user_id.set(user_id)
    ctx_session_id.set(session_id)
    svc = get_service()
    if edit:
        # Validate synchronously so 4xx is a real HTTP status (not an SSE error).
        svc.assert_editable(user_id, session_id)
        return await _run_or_stream(
            stream,
            lambda on_status=None, on_token=None, on_node=None: svc.edit_last_message(
                user_id, session_id, request, on_status=on_status, on_token=on_token, on_node=on_node
            ),
        )
    return await _run_or_stream(
        stream,
        lambda on_status=None, on_token=None, on_node=None: svc.run_turn(
            user_id, session_id, request, on_status=on_status, on_token=on_token, on_node=on_node
        ),
    )


@router.post("/{session_id}/title")
def generate_title(
    session_id: str,
    request: GenerateTitleRequest,
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """LLM-generate this session's chat title from the user's message and persist it.

    Called explicitly by the UI (not bundled into ``/start`` or ``/turn``).
    Replaces the old behaviour of storing the first user message verbatim as the
    title — see app/llm/title_generator.py for the prompt. The generated title is
    saved to the same `title` field every other endpoint already reads, so nothing
    else changes. Returns ``{"session_id", "title"}``.
    """
    user_id = resolve_user_id(request.user_id, claims)
    text = request.user_text()
    if not text:
        raise HTTPException(status_code=400, detail="message or text is required.")
    title = generate_chat_title(text, session_id=session_id, user_id=user_id)
    conversation.set_session_title(session_id, user_id, title)
    return {"session_id": session_id, "title": title}


@router.delete("")
def delete_session(
    request: DeleteSessionRequest,
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """Delete one session's conversation history.

    `user_id` comes ONLY from the JWT (not the payload) so a caller can never
    delete another user's session — the delete is scoped by BOTH `session_id`
    and `user_id`. 404 when no session matches that pair.
    """
    user_id = user_id_from_claims(claims)
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id not found in JWT.")
    ok = conversation.delete_session(request.session_id, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Session not found for this user.")
    return {"session_id": request.session_id, "user_id": user_id, "ok": True}


# -- read endpoints -----------------------------------------------------------


@router.get("")
def list_sessions(
    user_id: str = "",
    limit: int = 50,
    offset: int = 0,
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """List a user's sessions for the Recents sidebar, newest first.

    `user_id` from `?user_id=` or the JWT. Each entry carries a `resumable` flag
    (`false` once the conversation has ended) so the UI can disable the input box
    for ended sessions. `title` is LLM-generated via POST
    `/v1/sessions/{session_id}/title` (falls back to the first user message for
    sessions where that endpoint was never called — see get_session_title)."""
    uid = resolve_user_id(user_id, claims)
    if not uid:
        raise HTTPException(status_code=400, detail="user_id required (param or JWT).")
    return get_history_service().list_sessions(uid, limit=limit, offset=offset)


@router.get("/resumable")
def latest_resumable(
    user_id: str = "",
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """Home-screen check for the "Resume" pill: returns `resumable` (true when the
    user has a non-ended session) plus the most-recent resumable `session_id`.
    `user_id` from `?user_id=` or the JWT."""
    uid = resolve_user_id(user_id, claims)
    if not uid:
        raise HTTPException(status_code=400, detail="user_id required (param or JWT).")
    return get_history_service().latest_resumable(uid)


@router.post("/history")
def get_history(
    request: HistoryRequest,
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """Return one session's FULL transcript for the resume UI (no pagination).

    `page`/`take` are accepted for backward compatibility but unused."""
    user_id = resolve_user_id(request.user_id, claims)
    return get_history_service().get_history(request, user_id=user_id)


@router.get("/{session_id}/history")
def get_history_by_session(
    session_id: str,
    user_id: Optional[str] = Query(default=None, description="User id; falls back to the JWT username claim."),
    page: int = Query(default=1, ge=1, description="Unused — accepted for backward compatibility only."),
    take: int = Query(default=500, ge=1, description="Unused — accepted for backward compatibility only."),
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """GET a session's transcript by session_id in the path.

    Identical payload to ``POST /v1/sessions/history`` but friendlier for
    curl / browser testing: session_id goes in the path. Returns the FULL
    transcript (no pagination) — ``page``/``take`` are accepted but unused.
    Returns ``{converstation_status, session_id, chat_history}``.
    """
    uid = resolve_user_id(user_id, claims)
    req = HistoryRequest(
        session_id=session_id,
        user_id=uid,
        page=page,
        take=take,
    )
    return get_history_service().get_history(req, user_id=uid)


@router.get("/{session_id}/latest-response")
def get_latest_response(
    session_id: str,
    user_id: Optional[str] = Query(default=None, description="User id; falls back to the JWT username claim."),
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """Return the session's most recent bot message, independent of `page`/`take`.

    For the voice barge-in case: when the user interrupts CereBroZen mid-speech,
    the client may not have a reliable page number to page `/history` with
    (the `cerebrozen.ui_event` data-channel message that carries it can be
    delayed or dropped at the exact moment TTS was cut off). This endpoint just
    returns whichever bot message is last. ``message`` is ``null`` if the
    session doesn't exist, isn't owned by this user, or has no bot reply yet.
    """
    uid = resolve_user_id(user_id, claims)
    return get_history_service().get_latest_response(session_id, user_id=uid)


def _resolve_action_status(action: str) -> str:
    act = (action or "").strip().lower()
    if act not in ("save", "skip", "skipped", "delete"):
        raise HTTPException(status_code=400, detail='action must be "save", "skip", or "delete".')
    if act == "save":
        return "saved"
    if act in ("skip", "skipped"):
        return "skipped"
    return "deleted"


@router.post("/{session_id}/actions/status")
def set_action_status(
    session_id: str,
    request: ActionStatusRequest,
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """Save or delete one or more generated actions (the right-panel card buttons).

    Single-action body (unchanged): ``{action_id, action: "save"|"delete",
    roi_metrics?, full_text?, action_body?, expected_outcome?}``. The optional
    fields apply the card's inline ✏️ edits on save (the action keeps its
    `action_id`). A deleted action is flagged in the per-user agentic doc and
    thereafter hidden from the actions-insights endpoint AND dropped from the
    coaching context (`previousUserActions`), so it disappears for the user.

    Multi-action body: ``{actions: [{action_id, action, roi_metrics?, ...}, ...]}``
    applies every item in one Mongo round trip and returns ``{results: [...]}``
    (one entry per input item, in order) instead of the single-action shape.
    A per-item miss doesn't fail the rest of the batch — check each item's `ok`.

    `user_id` from `?user_id=`/body or the JWT. 404 when a single-action body's
    action isn't found; 400 on a bad `action` value.
    """
    uid = resolve_user_id(request.user_id, claims)
    if not uid:
        raise HTTPException(status_code=400, detail="user_id required (param or JWT).")

    if request.actions is not None:
        if not request.actions:
            raise HTTPException(status_code=400, detail="actions must not be empty.")
        updates = []
        for item in request.actions:
            status = _resolve_action_status(item.action)
            is_save = status == "saved"
            updates.append({
                "action_id": item.action_id,
                "status": status,
                "roi_metrics": item.roi_metrics if is_save else None,
                "full_text": item.full_text if is_save else None,
                "action_body": item.action_body if is_save else None,
                "expected_outcome": item.expected_outcome if is_save else None,
            })
        raw_results = agentic.set_action_statuses(uid, updates)
        results = [
            {
                "action_id": update["action_id"],
                "status": update["status"],
                "ok": r["ok"],
                **({"roi_metrics": r["roi_metrics"]} if update["status"] == "saved" and r["ok"] else {}),
            }
            for update, r in zip(updates, raw_results)
        ]
        return {
            "session_id": session_id,
            "ok": all(r["ok"] for r in results),
            "results": results,
        }

    target_status = _resolve_action_status(request.action)
    ok = agentic.set_action_status(
        uid, request.action_id, target_status,
        roi_metrics=request.roi_metrics if target_status == "saved" else None,
        full_text=request.full_text if target_status == "saved" else None,
        action_body=request.action_body if target_status == "saved" else None,
        expected_outcome=request.expected_outcome if target_status == "saved" else None,
    )
    if not ok:
        raise HTTPException(status_code=404, detail="Action not found for this user.")
    result: Dict[str, Any] = {
        "session_id": session_id,
        "action_id": request.action_id,
        "status": target_status,
        "ok": True,
    }
    if target_status == "saved":
        result["roi_metrics"] = canonical_roi_metrics(request.roi_metrics)
    return result


@router.post("/{session_id}/phase-selection")
def record_phase_selection(
    session_id: str,
    request: PhaseButtonSelectionRequest,
    claims: dict = Depends(require_auth),
) -> Dict[str, Any]:
    """Record which CH phase-transition button the user pressed.

    This endpoint exists because when the user presses "Save & Exit" at phase
    completion there is no subsequent coaching turn — the button press would
    otherwise never be persisted. For "Continue" buttons the selection is also
    conveyed via the next turn's ``metadata.session_continued``; calling this
    endpoint for those too keeps the conversation document consistent.

    ``user_selection`` must match one of the ``user_selection`` values from the
    ``phase_buttons`` array the turn payload returned (e.g.
    ``"continue_to_phase_2"``, ``"save_and_exit"``). Returns ``{"ok": true}``
    on success; 400 when ``user_selection`` is blank; 404 when the session has
    no bot message to stamp.
    """
    if not (request.user_selection or "").strip():
        raise HTTPException(status_code=400, detail="user_selection is required.")
    ok = conversation.record_phase_selection(
        session_id=session_id,
        user_selection=request.user_selection.strip(),
    )
    if not ok:
        raise HTTPException(
            status_code=404,
            detail="No bot message found for this session to record the phase selection on.",
        )
    return {
        "ok": True,
        "session_id": session_id,
        "user_selection": request.user_selection.strip(),
    }


@router.get("/{session_id}/actions-insights")
def get_actions_insights(
    session_id: str,
    request: Request,
    response: Response,
    user_id: str = "",
    claims: dict = Depends(require_auth),
) -> Any:
    """Deduped actions/insights for this session — powers the right-panel tabs and
    resume rendering. `user_id` from `?user_id=` or the JWT. Send
    `If-None-Match: <etag>` for a cheap 304 when nothing changed since last poll."""
    uid = resolve_user_id(user_id, claims)
    if not uid:
        raise HTTPException(status_code=400, detail="user_id required (param or JWT).")
    rid = _req_id.get()
    logger.info(
        "api.actions_insights_request",
        extra={"request_id": rid, "session_id": session_id, "user_id": uid},
    )
    data = get_actions_insights_service().get(uid, session_id)
    logger.info(
        "api.actions_insights_response",
        extra={
            "request_id": rid,
            "session_id": session_id,
            "user_id": uid,
            "actions": len(data.get("actions") or []),
            "insights": len(data.get("insights") or []),
            "version": data.get("version"),
        },
    )
    etag = f'W/"ai-{data.get("version", 0)}"'
    if request.headers.get("if-none-match") == etag:
        return Response(status_code=304, headers={"ETag": etag})
    response.headers["ETag"] = etag
    return data
