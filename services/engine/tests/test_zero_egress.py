"""The sovereignty proof: a coaching turn makes ZERO external network connections.

"Nothing has to leave your network" is the load-bearing enterprise claim, and the research
was blunt that a contractual promise is a post-breach remedy while an architectural
guarantee is prevention. This test is the guarantee, made checkable: it wraps the real
socket layer, runs a real coaching turn through the offline stack (mock provider, in-memory
stores — conftest pins this), and asserts that not one connection was opened to anything
but loopback.

A regression that introduces a phone-home — a telemetry beacon, an un-gated API call, a CDN
font fetch on some new surface — trips this immediately, in CI, offline, with no keys. The
runnable companion (scripts/egress_audit.py) does the same for a live demo a CISO watches.
"""

from __future__ import annotations

import socket

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client():
    return TestClient(create_app(), raise_server_exceptions=False)


# Loopback + the sentinels a stdlib/testclient stack legitimately uses in-process. Anything
# else is, by definition, egress.
_LOOPBACK = {"127.0.0.1", "::1", "localhost", "0.0.0.0", ""}


def _host_of(address) -> str:
    if isinstance(address, tuple) and address:
        return str(address[0])
    return str(address)


@pytest.fixture
def egress_guard(monkeypatch):
    """Record every attempted connection; block nothing so a real egress would still be
    observable rather than masked by a raised error. Returns the list of external hosts."""
    external: list[str] = []
    real_connect = socket.socket.connect
    real_create = socket.create_connection

    def _guarded_connect(self, address, *a, **k):
        host = _host_of(address)
        if host not in _LOOPBACK:
            external.append(host)
        return real_connect(self, address, *a, **k)

    def _guarded_create(address, *a, **k):
        host = _host_of(address)
        if host not in _LOOPBACK:
            external.append(host)
        return real_create(address, *a, **k)

    monkeypatch.setattr(socket.socket, "connect", _guarded_connect)
    monkeypatch.setattr(socket, "create_connection", _guarded_create)
    return external


def test_a_coaching_turn_leaves_the_network_untouched(client, egress_guard):
    """A full turn: the offline stack must complete it without reaching outside loopback."""
    r = client.post("/v1/webhook", json={"sender": "u1", "text": "delegation is hard"})
    assert r.status_code == 200, r.text
    assert r.json()["response_to_user"], "the turn must actually produce a reply, or we proved nothing"
    assert egress_guard == [], f"coaching turn attempted external egress to: {sorted(set(egress_guard))}"


def test_the_governance_attestation_leaves_the_network_untouched(client, egress_guard):
    """The trust surface itself must not phone home either."""
    r = client.get("/v1/governance")
    assert r.status_code == 200
    assert egress_guard == [], f"governance endpoint attempted external egress to: {sorted(set(egress_guard))}"
