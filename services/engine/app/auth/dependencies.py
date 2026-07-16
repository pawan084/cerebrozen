"""FastAPI auth dependency.

``require_auth`` enforces a valid Bearer JWT on protected routes. Mirrors the
reference service's local-optional behavior: running local with no secret set
skips auth (so local dev and the tester UI work without a token). Every other
case requires a valid token.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict

from fastapi import Request

from app import config
from app.auth.errors import AuthorizationError, ForbiddenError
from app.auth.jwt_validator import decode_token, extract_bearer
from app.tenancy import DEFAULT_ORG, ctx_org_id

logger = logging.getLogger("cerebrozen.auth")

# The dev bypass may only take effect in a development-class environment. It used to
# be honoured wherever it was set, which meant a single stray `AUTH_DEV_BYPASS=true`
# leaking into a deploy — a copied .env, an inherited task definition, a helm value
# nobody re-read — turned authentication off on every session endpoint, silently and
# with no error. An env var must not be able to do that. Anywhere outside this set the
# flag is refused and auth stays ON: the failure mode of a mistake is now "nobody can
# get in", not "everybody can".
_DEV_ENVS = {"local", "dev", "development", "test", "ci"}


def _bypass_requested() -> bool:
    return os.environ.get("AUTH_DEV_BYPASS", "").strip().lower() in ("1", "true", "yes")


def auth_enabled() -> bool:
    """True when a valid Bearer JWT is required.

    Auth is enforced everywhere except two development conveniences: an explicit
    ``AUTH_DEV_BYPASS``, or running local with no ``JWT_SECRET`` configured (so the
    browser UI can call the API tokenless). Both are gated on ``ENV`` being
    development-class — see ``_DEV_ENVS``.
    """
    if _bypass_requested():
        if config.ENV in _DEV_ENVS:
            return False
        # Refused, loudly. This is a misconfigured deploy, and it should be
        # discoverable in the logs the moment it boots rather than after a breach.
        logger.error(
            "auth.dev_bypass_refused env=%s reason=%s",
            config.ENV,
            "AUTH_DEV_BYPASS is a development-only flag; auth remains enforced",
        )
        return True
    return not (config.ENV == "local" and not config.JWT_SECRET)


async def require_auth(request: Request) -> Dict[str, Any]:
    """Validate the request's Bearer JWT and return its claims.

    Raises AuthorizationError (-> HTTP 401) on any failure. Claims are returned
    so callers *can* use them, but routing/identity here does not depend on them
    (no role or sender checks, per design).
    """
    if not auth_enabled():
        logger.warning("auth.skipped_local")
        ctx_org_id.set(DEFAULT_ORG)
        return {}
    token = extract_bearer(request.headers.get("Authorization"))
    claims = decode_token(token)
    org = str(claims.get("org_id") or "").strip()
    if not org:
        if config.REQUIRE_ORG_CLAIM:
            # Tenant scoping is only as strong as this claim: an org-less token
            # would silently read and write DEFAULT_ORG's data.
            raise AuthorizationError("token missing required org_id claim")
        org = DEFAULT_ORG
    ctx_org_id.set(org)
    return claims


#: The platform's role for CereBroZen's own operators (services/platform models.py).
#: An org's HR admin is ``org_admin`` — a customer, not an operator.
ROLE_INTERNAL_ADMIN = "internal_admin"


async def require_internal_admin(request: Request) -> Dict[str, Any]:
    """Validate the JWT *and* require the internal-admin role.

    The engine shipped with no role checks at all ("no role or sender checks, per
    design") — a straight inheritance from a single-tenant reference where every caller
    was the operator. That stopped being true when this became multi-tenant, and it was
    not a theoretical gap: an e2e run found any authenticated employee at any customer
    could GET /v1/prompts/download — the entire coaching workbook — and any token could
    PUT a stage, rewriting or disabling coaching agents for EVERY tenant, because the
    workbook is global rather than org-scoped.

    Use this on operator surfaces (the workbook, the compiled arc, the ops queues).
    Never on anything an employee legitimately needs: /v1/safety/helplines is the sharp
    example — gating a crisis screen behind an operator role would be catastrophic.
    """
    claims = await require_auth(request)
    if not auth_enabled():
        # Dev bypass (ENV=local with no JWT_SECRET) already makes everything reachable;
        # this dependency must not be the one thing that pretends otherwise.
        return claims
    role = str(claims.get("role") or "").strip()
    if role != ROLE_INTERNAL_ADMIN:
        # Log the refusal: an org_admin probing operator routes is worth seeing.
        logger.warning("auth.role_refused", extra={"role": role or "(none)", "path": request.url.path})
        raise ForbiddenError("this endpoint requires the internal_admin role")
    return claims
