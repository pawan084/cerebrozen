"""GET /v1/graph — the compiled arc as structured nodes + edges.

The admin draws the governed arc from this instead of parsing the mermaid diagram.
It is read-only on purpose: the graph is compiled in build_graph.py and routing is
code, so there is no write side to test — only that what we hand over is the truth.
"""

from fastapi.testclient import TestClient


def _client():
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def test_graph_reports_every_node_in_the_compiled_arc():
    body = _client().get("/v1/graph").json()
    ids = [n["id"] for n in body["nodes"]]
    # The 18 real nodes + LangGraph's two terminals.
    for stage in ("safety", "safe_response", "profile_read", "intake", "checkin",
                  "challenge", "core", "capability", "dynamic_actions",
                  "simulation_decision", "role_play", "sjt", "pattern", "learning_aid",
                  "final_action_check", "feedback", "session_complete", "action_checkin"):
        assert stage in ids, f"{stage} missing from the graph"
    assert "__start__" in ids and "__end__" in ids


def test_the_arc_starts_at_safety_and_crisis_forks_to_safe_response():
    body = _client().get("/v1/graph").json()
    edges = body["edges"]
    assert any(e["source"] == "__start__" and e["target"] == "safety" for e in edges), \
        "safety must run first — it is the crisis screen"
    crisis = [e for e in edges if e["source"] == "safety" and e["target"] == "safe_response"]
    assert crisis and crisis[0]["label"] == "crisis" and crisis[0]["conditional"] is True
    ok = [e for e in edges if e["source"] == "safety" and e["target"] == "profile_read"]
    assert ok and ok[0]["label"] == "ok"


def test_safe_response_is_terminal():
    # A crisis reply ends the turn — it must not chain onward into coaching.
    edges = _client().get("/v1/graph").json()["edges"]
    out = {e["target"] for e in edges if e["source"] == "safe_response"}
    assert out == {"__end__"}


def test_edges_carry_the_conditional_flag_and_are_dense():
    body = _client().get("/v1/graph").json()
    edges = body["edges"]
    # Conditional edges fan out to every possible target, so this is a dense graph —
    # the client relies on `conditional` to draw a spine instead of a hairball.
    assert len(edges) > len(body["nodes"]), "expected the conditional fan-out"
    assert any(e["conditional"] for e in edges)
    assert all({"source", "target", "label", "conditional"} <= set(e) for e in edges)


def test_stage_to_node_map_is_returned():
    body = _client().get("/v1/graph").json()
    stage_to_node = body["stage_to_node"]
    assert isinstance(stage_to_node, dict) and stage_to_node
    # Every stage must point at a node the graph actually contains, or the admin's
    # stage→agent lookup silently shows the wrong prompt for a node.
    ids = {n["id"] for n in body["nodes"]}
    assert set(stage_to_node.values()) <= ids
