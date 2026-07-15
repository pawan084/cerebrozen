r"""Load local env vars from env-dev.ps1 so `python api_server.py` works even when
the shell wasn't sourced.

Env vars are per-terminal on Windows; forgetting `. .\env-dev.ps1` left the
server without OPENAI_API_KEY. This parses the `$env:NAME = "value"` lines out of
env-dev.ps1 and populates os.environ — WITHOUT overriding anything already set
(an explicitly sourced shell still wins). Pure stdlib, no secrets logged.
"""

from __future__ import annotations

import logging
import os
import re
from pathlib import Path

logger = logging.getLogger("cerebrozen.env")

# $env:NAME = "value"  |  $env:NAME='value'  |  $env:NAME=value
_LINE = re.compile(
    r'^\s*\$env:(\w+)\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s#]+))', re.MULTILINE
)

# Plain dotenv line:  NAME=value  |  NAME="value"  |  export NAME=value
_DOTENV_LINE = re.compile(
    r'^\s*(?:export\s+)?(\w+)\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^#\r\n]*))', re.MULTILINE
)


def load_env_file(path: str | Path) -> int:
    path = Path(path)
    if not path.exists():
        return 0
    loaded = 0
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    for match in _LINE.finditer(text):
        name = match.group(1)
        value = next((g for g in match.groups()[1:] if g is not None), "")
        if name in os.environ and os.environ[name]:
            continue  # never override an explicitly-set var
        os.environ[name] = value
        loaded += 1
    return loaded


def load_dotenv_file(path: str | Path) -> int:
    """Load a plain KEY=value .env file (non-PowerShell), same non-override rule."""
    path = Path(path)
    if not path.exists():
        return 0
    loaded = 0
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        m = _DOTENV_LINE.match(line)
        if not m:
            continue
        name = m.group(1)
        value = next((g for g in m.groups()[1:] if g is not None), "")
        value = value.strip()
        # A var present in os.environ but EMPTY should still be overridable by the
        # .env (uvicorn inherits an empty OPENAI_API_KEY etc.); only a non-empty
        # explicit value wins.
        if os.environ.get(name):
            continue
        os.environ[name] = value
        loaded += 1
    return loaded


def load_local_env() -> None:
    """Load env-dev.ps1 and/or a plain .env (next to this repo) if present.

    ``CEREBROZEN_SKIP_DOTENV`` disables the whole thing. The test suite sets it,
    and it must: a suite whose offline guarantee rests on "the variable is empty"
    is not offline at all, because `load_dotenv_file` deliberately treats an empty
    var as overridable (uvicorn inherits empty vars — see there). That is how a
    developer's own POSTGRES_URL silently redirected every store test at a real
    database. The guarantee has to be "the .env is not read", not "the value we
    care about happens to be missing from it".
    """
    if os.environ.get("CEREBROZEN_SKIP_DOTENV", "").strip().lower() in ("1", "true", "yes"):
        logger.info("env.skipped", extra={"reason": "CEREBROZEN_SKIP_DOTENV"})
        return
    repo_root = Path(__file__).resolve().parent.parent
    ps1 = repo_root / "env-dev.ps1"
    n = load_env_file(ps1)
    if n:
        logger.info("env.loaded", extra={"path": str(ps1), "vars_set": n})
    dotenv = repo_root / ".env"
    m = load_dotenv_file(dotenv)
    if m:
        logger.info("env.loaded", extra={"path": str(dotenv), "vars_set": m})
    if not n and not m:
        logger.info("env.skipped", extra={"path": str(ps1)})
