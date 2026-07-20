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

from fastapi import Depends, HTTPException, Request

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


def require_plus(claims: Dict[str, Any] = Depends(require_auth)) -> Dict[str, Any]:
    """Gate a CereBro Plus-only route SERVER-SIDE (the paywall can't be client-only).

    Only an EXPLICIT ``plan == "free"`` is denied (402). An absent plan claim (auth off /
    dev, or a non-consumer token) passes — same 'absence is not refusal' rule as the free
    coaching cap — so premium routes stay usable offline and for enterprise seats, while
    plus/enterprise pass. The platform's entitlement matrix (models.entitlements_for) is
    the source of truth for WHICH features are Plus; these routes ARE those features.
    """
    if (claims or {}).get("plan") == "free":
        raise HTTPException(
            status_code=402, detail="This is a CereBro Plus feature. Upgrade to unlock it."
        )
    return claims


def require_adult(claims: Dict[str, Any] = Depends(require_auth)) -> Dict[str, Any]:
    """Gate a coaching turn on the 18+ attestation SERVER-SIDE — the onboarding age gate
    can't be client-only. The product is not for children (DPDP treats a child's data
    differently), and a client that skips onboarding must not still reach a coaching turn.

    Only an EXPLICIT ``adult == False`` is denied (403). An ABSENT claim (auth off / dev, or
    a pre-rollout token) passes — same 'absence is not refusal' rule as require_plus — so the
    enforcement rides in on the next 15-min rotation without a flag-day lockout. Every
    platform-issued consumer token carries the claim (True/False), so the gate is real; B2B
    seats and internal staff get ``adult == True`` by contract.
    """
    if (claims or {}).get("adult") is False:
        raise HTTPException(
            status_code=403,
            detail="You must confirm you are 18 or older before coaching can begin.",
        )
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
