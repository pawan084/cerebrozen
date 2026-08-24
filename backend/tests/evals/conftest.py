"""Per-test graph isolation for the judged evals.

The Oracle graph and its checkpointer are cached in module globals
(`app.agent.graph._graph` / `_checkpointer`) — right for production, where one
uvicorn worker means one event loop for the process's lifetime. The test
runner gives EVERY test its own loop, so the second Oracle test in a session
inherited a graph whose internal asyncio.Lock was bound to the first test's
dead loop and the stream failed with "bound to a different event loop" —
surfacing as "oracle returned no final text" and, for a while, masquerading
as model dishonesty in the suite-vs-isolation numbers. The measurement layer
was measuring an infrastructure bug.

Resetting the cache per test makes each test build its graph on its own loop,
which is exactly production's one-loop-one-graph invariant, per test.
"""
import pytest

from app.agent import graph as oracle_graph


@pytest.fixture(autouse=True)
def fresh_oracle_graph():
    oracle_graph._graph = None
    oracle_graph._checkpointer = None
    yield
    oracle_graph._graph = None
    oracle_graph._checkpointer = None
