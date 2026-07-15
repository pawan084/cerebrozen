"""LLM-generated chat titles — Claude.ai-style short titles from the user's message.

Two ways this runs:
  - Automatically, off the request path: `dispatch_title_generation` is fired from
    `CoachingService.start_session` (app/service.py) the moment a NEW session_id is
    minted — on a background thread, in parallel with the coaching agent's own LLM
    call, so it never adds latency to the streamed first reply.
  - On demand: ``POST /v1/sessions/{session_id}/title`` (app/routers/sessions.py)
    calls `generate_chat_title` synchronously — e.g. for the UI to force a
    regenerate, or for a session whose automatic title never landed.

Both paths persist through the same `conversation.set_session_title`, so whichever
finishes last wins; that's fine, both call sites feed it the same first message.

Deliberately kept outside the workbook prompt registry (app/llm/prompts.py): that
registry exists so coaching prompts stay editable by non-technical users via the
Excel workbook. A chat title is a small, code-owned utility prompt — edit the
``TITLE_SYSTEM_PROMPT`` string below directly, no workbook reload required.
"""

from __future__ import annotations

import logging

from app import config
from app.graph.runtime import get_client
from app.llm.responses_client import reasoning_effort_for
from app.request_context import ContextThreadPoolExecutor
from app.stores import conversation

logger = logging.getLogger("cerebrozen.title_generator")

# Small daemon pool, mirroring app/graph/builders.py's _EXECUTOR: title generation
# is LLM-bound and must never sit on the request path. Context-propagating, because
# this worker WRITES the session doc — see ContextThreadPoolExecutor.
_EXECUTOR = ContextThreadPoolExecutor(max_workers=2, thread_name_prefix="title")

STAGE_NAME = "chat_title_agent"

# Edit this prompt to change how titles are generated.
TITLE_SYSTEM_PROMPT = """You generate short, human-readable titles for a user's coaching conversation history in CereBroZen, based on their first message.

Write a short title that captures the topic of the message.

Rules:
- 3 to 6 words.
- Sentence case: capitalize only the first word and proper nouns.
- No trailing punctuation, no quotation marks, no markdown, no emoji.
- Be specific and concrete — name the actual subject, never a generic label
  like "Chat", "New conversation", or "Coaching session".
- Paraphrase and compress; never just repeat the message verbatim.
- Describe the topic the user raised, never a clinical or diagnostic label
  (e.g. "depression", "anxiety disorder", "burnout") — even if their message
  implies one — unless the user used that exact word themselves.
- If the message is a greeting or has no clear topic yet (e.g. "hi",
  "not sure where to start"), use a soft neutral title like "Getting started"
  or "Check-in" instead of inventing a subject.
- Keep tone plain and neutral, not clinical or dramatic, especially for
  emotionally difficult messages.
- Output ONLY the title text, nothing else.
"""

_MAX_TITLE_CHARS = 80


def _clean_title(raw: str) -> str:
    """Strip wrapping quotes/whitespace the model sometimes adds and cap length."""
    title = (raw or "").strip().strip("\"'` \n\t")
    if len(title) > _MAX_TITLE_CHARS:
        title = title[:_MAX_TITLE_CHARS].rstrip()
    return title


def generate_chat_title(user_message: str, *, session_id: str = "", user_id: str = "") -> str:
    """Call the LLM to produce a short chat title from the user's message.

    Best-effort: on any LLM failure (or an empty/degenerate LLM reply), falls
    back to the user's own message (cleaned + capped) so the caller always gets
    a usable title back — never a generic placeholder.
    """
    text = (user_message or "").strip()
    if not text:
        return text
    model = config.TITLE_GENERATION_MODEL
    try:
        resp = get_client().generate(
            system_prompt=TITLE_SYSTEM_PROMPT,
            user_prompt=text,
            model=model,
            reasoning_effort=reasoning_effort_for(STAGE_NAME, model),
            stage=STAGE_NAME,
            session_id=session_id,
            user_id=user_id,
        )
        return _clean_title(resp.text) or _clean_title(text)
    except Exception as exc:  # noqa: BLE001 — title generation must never break the caller
        logger.warning(
            "title_generator.failed",
            extra={"session_id": session_id, "user_id": user_id, "error": str(exc)},
        )
        return _clean_title(text)


def _generate_and_persist(user_id: str, session_id: str, user_message: str) -> None:
    title = generate_chat_title(user_message, session_id=session_id, user_id=user_id)
    conversation.set_session_title(session_id, user_id, title)


def dispatch_title_generation(user_id: str, session_id: str, user_message: str) -> None:
    """Fire-and-forget: generate + persist this session's title off the request path.

    Called from `CoachingService.start_session` right after session_id is minted —
    BEFORE the coaching agent's own LLM call runs — so this runs concurrently with
    the turn, not after it. Never blocks the caller; a no-op when `user_message` is
    blank (nothing to title yet).
    """
    text = (user_message or "").strip()
    if not text:
        return
    _EXECUTOR.submit(_generate_and_persist, user_id, session_id, text)
