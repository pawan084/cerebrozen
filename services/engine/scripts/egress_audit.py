"""Egress audit — run a coaching turn and prove nothing left the network.

    python -m scripts.egress_audit           # human-readable
    python -m scripts.egress_audit --json     # machine-readable

The live-demo companion to tests/test_zero_egress.py: it wraps the socket layer, runs a
real coaching turn through the offline stack, and reports every connection attempted to
anything other than loopback. In a correctly air-gapped deployment that number is zero — and
this is the artifact a CISO watches run, not a sentence in a contract.

This forces the offline provider (mock) so it needs no keys and no network by construction;
the point is to demonstrate the ARCHITECTURE, so any egress it caught would be a real defect
worth failing on, not a config accident.
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys

# Pin fully-offline BEFORE importing the app, exactly as the test conftest does.
os.environ.setdefault("CEREBROZEN_LLM_PROVIDER", "mock")
os.environ.setdefault("AUTH_DEV_BYPASS", "true")
os.environ.setdefault("ENV", "local")

_LOOPBACK = {"127.0.0.1", "::1", "localhost", "0.0.0.0", ""}


def _host_of(address) -> str:
    if isinstance(address, tuple) and address:
        return str(address[0])
    return str(address)


def run_audit() -> dict:
    external: list[str] = []
    real_connect = socket.socket.connect
    real_create = socket.create_connection

    def _c(self, address, *a, **k):
        h = _host_of(address)
        if h not in _LOOPBACK:
            external.append(h)
        return real_connect(self, address, *a, **k)

    def _cc(address, *a, **k):
        h = _host_of(address)
        if h not in _LOOPBACK:
            external.append(h)
        return real_create(address, *a, **k)

    socket.socket.connect = _c  # type: ignore[assignment]
    socket.create_connection = _cc  # type: ignore[assignment]
    try:
        from fastapi.testclient import TestClient

        from app.main import create_app

        client = TestClient(create_app(), raise_server_exceptions=False)
        r = client.post("/v1/webhook", json={"sender": "audit", "text": "delegation is hard"})
        replied = r.status_code == 200 and bool(r.json().get("response_to_user"))
    finally:
        socket.socket.connect = real_connect  # type: ignore[assignment]
        socket.create_connection = real_create  # type: ignore[assignment]

    return {
        "provider": os.environ["CEREBROZEN_LLM_PROVIDER"],
        "turn_completed": replied,
        "external_connections": sorted(set(external)),
        "egress_count": len(set(external)),
        "verdict": "sealed" if not external and replied else
                   ("egress-detected" if external else "turn-did-not-complete"),
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Prove a coaching turn makes zero external connections.")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args(argv)

    result = run_audit()
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print("\n  EGRESS AUDIT — one coaching turn, offline provider")
        print("  " + "─" * 52)
        print(f"  turn completed ........ {result['turn_completed']}")
        print(f"  external connections .. {result['egress_count']}")
        if result["external_connections"]:
            for h in result["external_connections"]:
                print(f"      -> {h}")
        print(f"  verdict ............... {result['verdict'].upper()}\n")
    # Non-zero exit if anything leaked or the turn didn't run — usable as a gate.
    return 0 if result["verdict"] == "sealed" else 1


if __name__ == "__main__":
    sys.exit(main())
