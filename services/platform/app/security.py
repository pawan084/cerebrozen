"""Passwords, JWTs, and opaque tokens.

Passwords: stdlib PBKDF2-HMAC-SHA256 (no native-wheel dependency — bcrypt/argon2
DLLs are blocked on some managed dev machines; 600k iterations, per-user salt).

Access tokens carry the ENGINE'S claim contract: `org_id` (tenancy — the engine
401s without it) and `user.username` (how the engine identifies the user), plus
`sub`/`role` for the platform's own dependencies. Same HS512 + base64 secret
convention as the engine, so one shared secret serves both services.
"""

import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

import jwt

from app import config
from app.models import User

_SCHEME = "pbkdf2-sha256"


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac(
        "sha256", password.encode(), salt, config.PBKDF2_ITERATIONS
    )
    return f"{_SCHEME}${config.PBKDF2_ITERATIONS}${salt.hex()}${dk.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        scheme, iterations, salt_hex, dk_hex = stored.split("$")
        if scheme != _SCHEME:
            return False
        dk = hashlib.pbkdf2_hmac(
            "sha256", password.encode(), bytes.fromhex(salt_hex), int(iterations)
        )
        return hmac.compare_digest(dk.hex(), dk_hex)
    except Exception:  # noqa: BLE001 — malformed hash is a failed verify, not a 500
        return False


def issue_access_token(user: User, plan: str = "free", adult: bool = False) -> str:
    now = datetime.now(timezone.utc)
    claims = {
        "sub": user.id,
        "org_id": user.org_id or "internal",
        "role": user.role,
        "user": {"username": user.id},  # the engine's user identity claim
        # The 18+ attestation travels in the signed token so the engine can refuse to serve
        # a coaching turn to an un-attested consumer OFFLINE — the age gate can't be
        # client-only. True by contract for B2B seats/internal staff (they don't do consumer
        # onboarding); reflects the personal account's own attestation otherwise. Resolved
        # by _issue_pair; same 15-min staleness trade as plan/consent (attest rotates the
        # token so it takes effect at once).
        "adult": adult,
        # The consumer plan (free|plus|enterprise) travels in the signed token so the
        # engine can enforce entitlements (free-tier coaching cap, premium gating)
        # offline — same staleness trade as consent (takes effect on the next 15-min
        # rotation; /billing/me is the immediate source of truth for the UI). Resolved
        # by _issue_pair from the user's org + subscription.
        "plan": plan,
        # Consent travels IN THE SIGNED TOKEN. The engine holds the content and must
        # enforce the person's choices, but it cannot read this database — and a
        # per-request call back to us would put an outage between someone and their own
        # journal. A signed claim is enforceable offline, and it cannot be forged by the
        # client. The trade is staleness: a revoked consent takes effect when the access
        # token next rotates (ACCESS_TTL_MIN, 15 minutes) rather than instantly, which is
        # why the PLATFORM stops serving the data immediately too.
        "consent": user.consents(),
        "iat": now,
        "exp": now + timedelta(minutes=config.ACCESS_TTL_MIN),
    }
    return jwt.encode(claims, config.JWT_SECRET, algorithm=config.JWT_ALGORITHM)


def decode_access_token(token: str) -> dict:
    """Raises jwt exceptions on anything invalid/expired — callers map to 401."""
    return jwt.decode(token, config.JWT_SECRET, algorithms=[config.JWT_ALGORITHM])


def new_opaque_token() -> tuple[str, str]:
    """(raw token to hand out once, sha256 hash to store)."""
    raw = secrets.token_urlsafe(48)
    return raw, hash_opaque(raw)


def hash_opaque(raw: str) -> str:
    return hashlib.sha256(raw.encode()).hexdigest()


def hash_email(email: str) -> str:
    """Deletion-ledger email hash: salted with the JWT secret so the ledger is
    not a rainbow-table lookup, but 'was this address deleted?' stays answerable."""
    return hashlib.sha256(config.JWT_SECRET + email.lower().strip().encode()).hexdigest()
