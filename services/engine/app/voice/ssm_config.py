"""SSM Parameter Store helpers for voice configuration.

Reads and writes the 7 voice params under /{ENV}/bot/CEREBROZEN_VOICE_*.
Called via asyncio.to_thread() from the async entrypoint so the boto3
sync client doesn't block the event loop.

Silently returns {} / no-ops when:
  - boto3 is not installed
  - AWS credentials are unavailable (local dev without AWS)
  - SSM is unreachable
This lets the agent fall back to config.py / env var defaults seamlessly.
"""

from __future__ import annotations

import logging

logger = logging.getLogger("cerebrozen.voice.ssm")

# The 7 voice param names stored in SSM (last segment of the path).
VOICE_PARAM_KEYS = [
    "CEREBROZEN_VOICE_TTS_VOICE_ID",
    "CEREBROZEN_VOICE_TTS_MODEL",
    "CEREBROZEN_VOICE_STABILITY",
    "CEREBROZEN_VOICE_SIMILARITY",
    "CEREBROZEN_VOICE_STYLE",
    "CEREBROZEN_VOICE_SPEED",
    "CEREBROZEN_VOICE_SPEAKER_BOOST",
]


def _prefix(env: str) -> str:
    return f"/{env}/bot/"


_SSM_REGION = "ap-south-1"


def _ssm_client():
    # Explicit region required: the FastAPI container has AWS_REGION=us-east-1 in its
    # env, which botocore picks up before IMDS, so without region_name both API writes
    # and reads silently go to us-east-1 while the LiveKit subprocess (no inherited
    # AWS_REGION) reads from ap-south-1 — causing the Voice Lab save to be invisible
    # to the voice agent. Hardcoding the actual EC2 region makes all processes
    # consistent. Prod is protected by IAM (PutParameter restricted to /dev/ and /qa/
    # paths), not by region.
    import boto3
    return boto3.client("ssm", region_name=_SSM_REGION)


def read_voice_params(env: str) -> dict[str, str]:
    """Fetch voice params from SSM. Returns {PARAM_NAME: value} or {} on failure."""
    try:
        ssm = _ssm_client()
        names = [f"{_prefix(env)}{k}" for k in VOICE_PARAM_KEYS]
        resp = ssm.get_parameters(Names=names, WithDecryption=True)
        result = {}
        for p in resp.get("Parameters", []):
            key = p["Name"].split("/")[-1]
            result[key] = p["Value"]
        logger.info("voice.ssm_read", extra={"env": env, "keys": list(result.keys())})
        return result
    except Exception as exc:  # noqa: BLE001
        logger.debug("voice.ssm_read_skip", extra={"reason": str(exc)})
        return {}


def write_voice_params(env: str, params: dict[str, str]) -> list[str]:
    """Write voice params to SSM. Returns list of param names written."""
    try:
        ssm = _ssm_client()
        written = []
        for key, value in params.items():
            if key not in VOICE_PARAM_KEYS:
                continue
            ssm.put_parameter(
                Name=f"{_prefix(env)}{key}",
                Value=str(value),
                Type="String",
                Overwrite=True,
            )
            written.append(key)
        logger.info("voice.ssm_write", extra={"env": env, "keys": written})
        return written
    except Exception as exc:  # noqa: BLE001
        logger.error("voice.ssm_write_failed", extra={"reason": str(exc)})
        raise
