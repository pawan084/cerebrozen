"""Bot protection on the two public write endpoints — signup and waitlist (WC-90).

Written for the moment paid acquisition starts, because that is when the
economics change. Today a bot signup is a wasted row. The moment ads point at
the funnel it is a bot *farm*, and in this product a farmed account is not free:
each one draws `free_daily_messages` (50) of real LLM completion a day, and each
verification email spends sender reputation that is shared with every genuine
user's password reset.

Two layers, on purpose, because they fail in opposite directions.

**A challenge (Cloudflare Turnstile or hCaptcha).** The real defence, and the one
that needs an account we do not have yet — so it is a seam, inert until
`BOT_CHALLENGE_SECRET` is set, per the project rule that everything degrades
without keys. Nothing about the clients has to change until then; when the key
lands they render a widget and pass the token through.

**A throwaway-address check.** Needs no vendor, works today, and works on every
client including the native ones. It catches only the laziest bulk signup and is
not claimed to do more — see `_THROWAWAY` below for why the list is deliberately
short.

**Failure direction is a decision, not an accident.** If the challenge provider
answers "this token is invalid", that is a verdict and the signup is refused. If
the provider cannot be *reached* — an outage, a timeout, a 500 — the signup is
allowed and the failure is logged. Fail-closed there would mean a third-party
outage stops every new account in a mental-health product, including for someone
signing up at a bad moment; the IP and account rate limits are still standing in
the meantime. The two cases are distinguished deliberately, because collapsing
them is how a "secure default" quietly becomes an availability incident.
"""

from __future__ import annotations

import logging

import httpx
from fastapi import HTTPException, status

from app.core.config import settings

logger = logging.getLogger("cerebro.botcheck")

#: Machine-readable markers, kept as constants because clients branch on them
#: rather than on the status code — a refused challenge should re-render the
#: widget, a refused address should focus the email field. Both are 400s.
CHALLENGE_FAILED_CODE = "challenge_failed"
THROWAWAY_EMAIL_CODE = "throwaway_email"

VERIFY_URLS = {
    "turnstile": "https://challenges.cloudflare.com/turnstile/v0/siteverify",
    "hcaptcha": "https://hcaptcha.com/siteverify",
}

#: Deliberately short, and deliberately not maintained as an arms race.
#:
#: An exhaustive throwaway list is a moving target with thousands of entries and
#: a steady trickle of false positives, and the false positive here is somebody
#: being told they may not have an account. These are the handful of services
#: that exist ONLY to be disposable, where a match is unambiguous. Anything
#: cleverer belongs behind the challenge, which is what the challenge is for.
_THROWAWAY = frozenset({
    "mailinator.com",
    "guerrillamail.com",
    "10minutemail.com",
    "yopmail.com",
    "tempmail.com",
    "temp-mail.org",
    "throwawaymail.com",
    "trashmail.com",
    "sharklasers.com",
    "getnada.com",
    "dispostable.com",
    "maildrop.cc",
    "fakeinbox.com",
    "mailnesia.com",
})

#: Privacy relays, which look disposable and are not. This set is the reason the
#: check above is an allow-list-aware match rather than a heuristic.
#:
#: `privaterelay.appleid.com` is what Sign in with Apple hands us when a user
#: chooses "Hide My Email" — a supported sign-in path in this very product. A
#: blocklist built from "looks like a burner" would refuse those accounts, and
#: the people it refused would be exactly the ones who care most about privacy,
#: which is who this product is for. The others are the equivalent relays from
#: DuckDuckGo, Mozilla and Fastmask.
_RELAYS = frozenset({
    "privaterelay.appleid.com",
    "icloud.com",
    "duck.com",
    "mozmail.com",
    "fastmail.com",
})


def domain_of(email: str) -> str:
    return email.rsplit("@", 1)[-1].strip().lower() if "@" in email else ""


def is_throwaway(email: str) -> bool:
    """Whether this address is from a service that exists to be discarded.

    Relays win over the blocklist by construction: the check returns False for
    anything in `_RELAYS` before it consults `_THROWAWAY` at all, so adding a
    relay domain to both sets can never lock those users out.
    """
    domain = domain_of(email)
    if not domain or domain in _RELAYS:
        return False
    return domain in _THROWAWAY


async def verify_challenge(token: str | None, remote_ip: str | None = None) -> bool:
    """Ask the configured provider whether this challenge token is genuine.

    Returns True when there is nothing configured — an unconfigured seam must
    not refuse anybody — and True when the provider cannot be reached. Only an
    explicit negative verdict returns False. See the module docstring for why
    those two are not the same answer.
    """
    if not settings.bot_challenge_enabled:
        return True
    if not token:
        # Configured, and the client sent nothing. That is a verdict: either a
        # client that has not been updated, or something that never loaded the
        # widget at all.
        logger.info("bot_challenge_missing_token")
        return False

    url = settings.bot_challenge_verify_url or VERIFY_URLS.get(
        settings.bot_challenge_provider
    )
    if not url:
        logger.warning(
            "bot_challenge_unknown_provider provider=%s", settings.bot_challenge_provider
        )
        return True

    data = {"secret": settings.bot_challenge_secret, "response": token}
    if remote_ip:
        data["remoteip"] = remote_ip
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            reply = await client.post(url, data=data)
            reply.raise_for_status()
            body = reply.json()
    except (httpx.HTTPError, ValueError) as exc:
        # Unreachable, not "invalid". Let the signup through and say so loudly:
        # a spike of this line is the signal that the defence is down, and it
        # must not be confused with a spike of refusals.
        logger.warning("bot_challenge_unreachable error=%s", type(exc).__name__)
        return True

    if not body.get("success"):
        logger.info("bot_challenge_rejected codes=%s", body.get("error-codes"))
        return False
    return True


async def guard(email: str, challenge_token: str | None, remote_ip: str | None = None) -> None:
    """The single call sites use. Raises 400 with a structured detail, or returns.

    Order matters: the free check runs first, so a throwaway address costs no
    outbound request to a paid provider.
    """
    if is_throwaway(email):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": THROWAWAY_EMAIL_CODE,
                "message": (
                    "That looks like a temporary address. Please use one you can "
                    "receive mail at — account recovery and anything safety-related "
                    "goes there."
                ),
            },
        )
    if not await verify_challenge(challenge_token, remote_ip):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": CHALLENGE_FAILED_CODE,
                "message": "That verification didn't go through. Please try again.",
            },
        )
