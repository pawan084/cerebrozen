"""The Oracle SSE contract, hermetically.

`.coveragerc` excludes the live-LLM streaming paths, and that exclusion had
quietly become "the frame protocol has no tests at all": every client hand-codes
a switch over `type` (`TalkScreen.consume`, the web client's reader), so a
renamed or malformed frame is a silent client regression — the stream still
flows, the client just stops understanding it.

These tests drive the real route with a stub graph, so the whole request path —
auth, quota, persistence, frame serialisation — runs exactly as production does,
minus the model. What the stub yields is shaped like LangGraph's
`stream_mode=["messages", "updates"]` output, which is the seam `_run` consumes.

Added with the `tool` frame (WC-138/139): the agent announces each tool call by
NAME before the ToolNode runs, so a read tool's latency renders as "checking
your week" instead of a stall. The privacy half of that contract is tested
here too — tool ARGUMENTS never reach the stream, because they routinely quote
the user's own words.
"""
import json

import pytest
from httpx import ASGITransport, AsyncClient

from app.core.config import settings
from app.main import app


class _Msg:
    """Duck-typed AIMessageChunk / AIMessage — only what `_run` reads."""

    def __init__(self, content="", type_="", tool_calls=None):
        self.content = content
        self.type = type_
        self.tool_calls = tool_calls


class _State:
    next = ()


class _StubGraph:
    """Yields a scripted stream; records nothing."""

    def __init__(self, script):
        self._script = script

    async def astream(self, graph_input, config, stream_mode):
        for frame in self._script:
            yield frame

    async def aget_state(self, config):
        return _State()


def _frames(body: str) -> list[dict]:
    return [
        json.loads(line[len("data: "):])
        for line in body.splitlines()
        if line.startswith("data: ")
    ]


@pytest.fixture()
def oracle_on(monkeypatch):
    """Force the availability gate open without a key: the property reads two
    flags, so patch the property itself on the Settings class."""
    monkeypatch.setattr(type(settings), "oracle_available", property(lambda self: True))


async def _post(auth_client, text="hello"):
    # auth_client's transport is already wired to the app; reuse its auth header
    # on a fresh client because the SSE body must be read to completion.
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
        headers=auth_client.headers,
    ) as c:
        return await c.post("/oracle/messages", json={"text": text})


@pytest.mark.anyio
async def test_tool_calls_stream_as_named_tool_frames(auth_client, oracle_on, monkeypatch):
    script = [
        ("updates", {"agent": {"messages": [
            _Msg(tool_calls=[
                {"name": "get_weekly_insights", "args": {"secret": "user words"}},
                {"name": "suggest_activity", "args": {"kind": "breathing"}},
            ])
        ]}}),
        ("messages", (_Msg("Here is ", "AIMessageChunk"), {})),
        ("messages", (_Msg("your week.", "AIMessageChunk"), {})),
    ]

    async def fake_get_graph():
        return _StubGraph(script)

    monkeypatch.setattr("app.api.routes.oracle.get_graph", fake_get_graph)
    r = await _post(auth_client)
    assert r.status_code == 200
    frames = _frames(r.text)

    tools = [f for f in frames if f["type"] == "tool"]
    assert [t["tool"] for t in tools] == ["get_weekly_insights", "suggest_activity"]
    # The privacy half of the contract: names only, never arguments.
    for t in tools:
        assert set(t.keys()) == {"type", "tool"}
    assert "user words" not in r.text

    # Tool frames precede the tokens they explain — announcing a tool after its
    # answer has streamed would be decoration, not progress.
    types = [f["type"] for f in frames]
    assert types.index("tool") < types.index("token")
    assert frames[-1] == {"type": "done", "text": "Here is your week."}


@pytest.mark.anyio
async def test_a_plain_reply_streams_no_tool_frames(auth_client, oracle_on, monkeypatch):
    script = [("messages", (_Msg("Hi.", "AIMessageChunk"), {}))]

    async def fake_get_graph():
        return _StubGraph(script)

    monkeypatch.setattr("app.api.routes.oracle.get_graph", fake_get_graph)
    r = await _post(auth_client)
    assert r.status_code == 200
    types = [f["type"] for f in _frames(r.text)]
    assert "tool" not in types
    assert types[-1] == "done"


@pytest.mark.anyio
async def test_object_style_tool_calls_also_stream(auth_client, oracle_on, monkeypatch):
    """LangChain emits tool_calls as dicts today; a provider adapter that hands
    back objects with a `.name` must not silently lose the frame."""

    class _Call:
        name = "log_sleep"

    script = [("updates", {"agent": {"messages": [_Msg(tool_calls=[_Call()])]}})]

    async def fake_get_graph():
        return _StubGraph(script)

    monkeypatch.setattr("app.api.routes.oracle.get_graph", fake_get_graph)
    r = await _post(auth_client)
    tools = [f for f in _frames(r.text) if f["type"] == "tool"]
    assert [t["tool"] for t in tools] == ["log_sleep"]
