"""Pure-Python stand-in for uuid_utils.compat — local dev machine only.

This machine's Windows Application Control policy blocks the Rust extension
(_uuid_utils.pyd) that langsmith/langchain-core import. Running pytest with
PYTHONPATH=.localdev makes this shim shadow the installed package. It
implements the stdlib-compatible subset those libraries use: uuid7
(RFC 9562) and uuid4. Linux/Docker/CI use the real package — never ship this.
"""
import os
import time
import uuid as _uuid


def uuid7(timestamp=None, nanos=None) -> _uuid.UUID:
    """RFC 9562 UUIDv7: 48-bit unix-ms timestamp, version/variant, 74 random bits."""
    if timestamp is None:
        ns = time.time_ns()
    else:
        ns = int(timestamp) * 1_000_000_000 + int(nanos or 0)
    unix_ts_ms = (ns // 1_000_000) & 0xFFFF_FFFF_FFFF
    rand = int.from_bytes(os.urandom(10), "big")  # 80 bits, we use 74
    rand_a = (rand >> 62) & 0x0FFF  # 12 bits
    rand_b = rand & 0x3FFF_FFFF_FFFF_FFFF  # 62 bits
    val = (
        (unix_ts_ms << 80)
        | (0x7 << 76)
        | (rand_a << 64)
        | (0b10 << 62)
        | rand_b
    )
    return _uuid.UUID(int=val)


def uuid4() -> _uuid.UUID:
    return _uuid.uuid4()
