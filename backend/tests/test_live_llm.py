"""Opt-in live-LLM integration tests — the streaming paths hermetic CI skips.

The Oracle/agent modules are excluded from coverage because they need a real
LLM; this module exercises them end-to-end when explicitly enabled:

    RUN_LLM_TESTS=1 python -m pytest tests/test_live_llm.py -q

Skipped entirely (not failed) without the flag or without an LLM key, so the
normal suite and CI stay hermetic.
"""
import os

import pytest

from app.core.config import settings

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_LLM_TESTS") != "1" or not settings.ai_enabled,
    reason="live-LLM suite is opt-in: set RUN_LLM_TESTS=1 and provide an LLM key",
)


async def test_chat_reply_generated_by_live_llm(auth_client):
    r = await auth_client.post("/chat/messages",
                               json={"text": "In one short sentence, what is box breathing?"})
    assert r.status_code == 201
    reply = r.json()["reply"]["text"]
    assert len(reply) > 20   # a real model reply, not an empty fallback


@pytest.mark.skipif(not settings.oracle_enabled, reason="ORACLE_ENABLED is off")
async def test_oracle_stream_emits_sse_frames(auth_client):
    """The SSE stream produces data frames and terminates cleanly for a short
    prompt — covering the graph + checkpointer + streaming plumbing."""
    collected = ""
    async with auth_client.stream("POST", "/oracle/messages",
                                  json={"text": "Say hello in five words."}) as resp:
        assert resp.status_code == 200
        assert resp.headers["content-type"].startswith("text/event-stream")
        async for line in resp.aiter_lines():
            collected += line + "\n"
            if len(collected) > 4000:   # plenty to prove liveness; don't run long
                break
    assert "data:" in collected
