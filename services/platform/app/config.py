"""Platform configuration. Everything degrades without keys; production
refuses insecure defaults (the boot-guard pattern from the references)."""

import base64
import logging
import os
import secrets

logger = logging.getLogger("cerebrozen.platform.config")

ENV = os.environ.get("ENV", "local").strip().lower()
_DEV_ENVS = {"local", "dev", "development", "test", "ci"}

DATABASE_URL = os.environ.get(
    "DATABASE_URL", "sqlite+aiosqlite:///./platform.db"
).strip()

# JWT secret is base64-encoded — the same convention the coaching engine uses,
# so both services can share one HS512 secret and the engine accepts our tokens.
_JWT_SECRET_B64 = os.environ.get("JWT_SECRET", "").strip()


def decode_secret(b64: str) -> bytes:
    try:
        return base64.b64decode(b64) if b64 else b""
    except Exception:  # noqa: BLE001 — malformed secret -> treat as unset
        return b""


JWT_SECRET: bytes = decode_secret(_JWT_SECRET_B64)
if not JWT_SECRET:
    # Dev convenience: a per-process random secret. Tokens die with the process,
    # which is exactly right for a secret nobody configured.
    JWT_SECRET = secrets.token_bytes(64)
    logger.info("config.jwt_secret_generated (dev only — set JWT_SECRET in prod)")

JWT_ALGORITHM = os.environ.get("JWT_ALGORITHM", "HS512").strip()
ACCESS_TTL_MIN = int(os.environ.get("CEREBROZEN_ACCESS_TTL_MIN", "15"))
REFRESH_TTL_DAYS = int(os.environ.get("CEREBROZEN_REFRESH_TTL_DAYS", "30"))
PBKDF2_ITERATIONS = int(os.environ.get("CEREBROZEN_PBKDF2_ITERATIONS", "600000"))

# Dev seed: an internal admin so a fresh checkout is usable immediately.
SEED_DEV_ADMIN = os.environ.get("CEREBROZEN_SEED_DEV_ADMIN", "true").strip().lower() != "false"
DEV_ADMIN_EMAIL = "admin@cerebrozen.in"
DEV_ADMIN_PASSWORD = "admin12345"

# ...plus a demo tenant with an HR admin and a member, so BOTH clients have a real
# persona to sign in as: the admin console needs an org_admin to show the HR surface at
# all, and the employee app needs a member who actually belongs to an org (an org-less
# internal_admin makes the engine's tenancy meaningless). Same SEED_DEV_ADMIN gate, and
# guard_production() refuses to boot a deployed env with that flag on — so these accounts
# cannot exist in production. The clients only offer them as one-click fill in dev
# (NEXT_PUBLIC_DEMO_LOGIN / NODE_ENV), never in a production build.
DEMO_ORG_NAME = "Demo Co"
DEMO_ORG_SLUG = "demo-co"
DEMO_HR_EMAIL = "hr@cerebrozen.in"
DEMO_MEMBER_EMAIL = "demo@cerebrozen.in"
DEMO_PASSWORD = "demo12345"

INVITATION_TTL_DAYS = int(os.environ.get("CEREBROZEN_INVITATION_TTL_DAYS", "14"))

# k-anonymity floor for HR analytics: a behavioral metric computed from fewer
# distinct people than this is SUPPRESSED (returned as null). Contract-level;
# 8 is the documented default (docs/SECURITY.md). Enforced in the aggregation
# layer, never left to the UI.
COHORT_FLOOR = int(os.environ.get("CEREBROZEN_COHORT_FLOOR", "8"))

# Invitation email delivery — same SMTP variables as the marketing demo form,
# so one mailbox serves both. Unset => invitations are shared manually.
SMTP_HOST = os.environ.get("SMTP_HOST", "").strip()
SMTP_PORT = int(os.environ.get("SMTP_PORT", "465") or "465")
SMTP_USER = os.environ.get("SMTP_USER", "").strip()
SMTP_PASS = os.environ.get("SMTP_PASS", "").strip()
# Where accept links point (the admin app's public URL).
ADMIN_BASE_URL = os.environ.get(
    "CEREBROZEN_ADMIN_BASE_URL", "http://localhost:3001"
).strip().rstrip("/")

# Browser origins allowed to call this API (the admin/app frontends). In prod
# Caddy serves everything under one apex, but the admin still runs on its own
# subdomain — set this explicitly there.
CORS_ORIGINS = [
    o.strip()
    for o in os.environ.get(
        "CEREBROZEN_CORS_ORIGINS",
        "http://localhost:3000,http://localhost:3001,http://localhost:3002",
    ).split(",")
    if o.strip()
]


def guard_production() -> None:
    """Refuse to boot a production deployment on development defaults.

    The failure mode of a missed env var must be "it will not start", never
    "it started wide open" (reference boot-guard rule).
    """
    if ENV in _DEV_ENVS:
        return
    problems = []
    if not _JWT_SECRET_B64:
        problems.append("JWT_SECRET is unset (tokens would use a process-random secret)")
    if DATABASE_URL.startswith("sqlite"):
        problems.append("DATABASE_URL is sqlite (production needs Postgres)")
    if SEED_DEV_ADMIN:
        problems.append("CEREBROZEN_SEED_DEV_ADMIN must be false in production")
    if problems:
        raise RuntimeError(
            "refusing to start in production with insecure defaults: " + "; ".join(problems)
        )
