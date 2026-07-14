"""Pure-Python shim for uuid_utils — local dev only (see compat.py)."""
from uuid import UUID  # noqa: F401  (stdlib UUID is the compat return type)

from .compat import uuid4, uuid7  # noqa: F401

__version__ = "0.0.0+local-pure-python-shim"
