"""Central configuration for the LangGraph CIM skeleton.

Everything is environment-driven so the same code runs on the Windows dev box
(no Mongo) and on EC2 (Mongo + S3). Sensible defaults keep it runnable out of
the box for local testing.
"""

from __future__ import annotations

import base64
import logging
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Base URL for Strapi-hosted assets (emotion mood board images, etc.)
STRAPI_MEDIA_URL = os.environ.get("STRAPI_MEDIA_URL", "").strip().rstrip("/")

# --- Prompts (editable by non-technical users — stays in the workbook B7) ----
PROMPT_WORKBOOK = os.environ.get(
    "PROMPT_WORKBOOK", str(REPO_ROOT / "agent_prompts.xlsx")
)

# Where the prompt workbook comes from:
#   "s3"       (default) — download agent_prompts.xlsx from S3 at load/reload.
#   "codebase"           — use the bundled local PROMPT_WORKBOOK (above).
# S3 mode falls back to the bundled local workbook if the fetch fails, so a
# misconfigured bucket never takes prompts fully down. Local dev sets "codebase".
PROMPT_SOURCE = os.environ.get("PROMPT_SOURCE", "s3").strip().lower()

# S3 location of the workbook. The prompt lives in the system-configuration
# bucket (NOT the RAG bucket), so the bucket must be set explicitly per env
# (e.g. dev-system-configuration). Key defaults to the known prefix; version
# pins a specific S3 object for rollback.
PROMPT_S3_BUCKET = os.environ.get("PROMPT_S3_BUCKET", "").strip()
PROMPT_S3_KEY = os.environ.get(
    "PROMPT_S3_KEY", "agentic_prompts/agent_prompts.xlsx"
).strip()
PROMPT_S3_VERSION = os.environ.get("PROMPT_S3_VERSION", "").strip()

# --- LLM prompt caching (OpenAI server-side prefix cache) --------------------
# When False (default) a per-request UUID is prepended to every system prompt so
# OpenAI never serves a stale cached prefix after a workbook reload or prompt edit.
# Set CEREBROZEN_LLM_PROMPT_CACHE=true only when prompt stability is confirmed and
# the input-token cost reduction is actively wanted.
#
# Disadvantages of enabling LLM prompt caching:
#   1. Stale prompts: after a workbook reload, the old cached prefix persists for
#      5-10 min at OpenAI — users in flight may receive old-prompt behaviour.
#   2. No on-demand invalidation: there is no API flag to purge the cache.
#   3. Hard to debug: impossible to tell if a response came from the current or
#      a stale cached prompt.
#   4. Cost illusion: cached-token prices appear lower, making cost estimates
#      unreliable (hit rate is non-deterministic and server-controlled).
#   5. Cache sharing: all sessions with identical system prompts share the same
#      slot — cross-user bleed cannot be fully ruled out.
LLM_PROMPT_CACHE_ENABLED: bool = (
    os.environ.get("CEREBROZEN_LLM_PROMPT_CACHE", "false").strip().lower() == "true"
)

# Per-request OpenAI timeout (seconds). Caps pathological hangs (one CIM call
# took 341s) so a turn fails fast instead of blowing past the UI timeout.
# 60s keeps it below the Streamlit UI's 90s request timeout, so a hang surfaces
# as a clean server error rather than the UI's "cannot connect".
try:
    OPENAI_TIMEOUT = float(os.environ.get("OPENAI_TIMEOUT", "60"))
except ValueError:
    OPENAI_TIMEOUT = 60.0

# OPENAI_TIMEOUT above is httpx's per-read-GAP timeout — a generation that keeps
# drip-feeding tokens never trips it (live incident 2026-07-06: an action_checkin
# stream ran indefinitely, zero telemetry, turn never completed). These bound the
# WHOLE generation instead: a hard wall-clock deadline on streamed generations,
# and a server-side output-token cap so a runaway can't generate unboundedly.
try:
    OPENAI_STREAM_DEADLINE_S = float(os.environ.get("OPENAI_STREAM_DEADLINE_S", "180"))
except ValueError:
    OPENAI_STREAM_DEADLINE_S = 180.0
try:
    # NOTE: for reasoning models max_output_tokens INCLUDES hidden reasoning tokens,
    # so this must sit well above the largest legitimate visible reply (~2.5K tokens
    # observed) to avoid clipping a reasoning-heavy turn — 8192 bounds a runaway
    # while leaving ~3x headroom. Env-tunable per environment.
    OPENAI_MAX_OUTPUT_TOKENS = int(os.environ.get("OPENAI_MAX_OUTPUT_TOKENS", "8192"))
except ValueError:
    OPENAI_MAX_OUTPUT_TOKENS = 8192

# --- Phase 2: LLM-call resilience (timeout already above; retry/cascade/breaker) ---
# Every request-path LLM call is bounded so a slow/failing OpenAI call can never
# hang or silently kill a turn. All env-overridable; defaults are conservative.
try:
    LLM_MAX_RETRIES = max(0, int(os.environ.get("CEREBROZEN_LLM_MAX_RETRIES", "2")))
except ValueError:
    LLM_MAX_RETRIES = 2
try:
    LLM_BACKOFF_BASE_S = float(os.environ.get("CEREBROZEN_LLM_BACKOFF_BASE_S", "0.5"))
except ValueError:
    LLM_BACKOFF_BASE_S = 0.5
try:
    LLM_BACKOFF_MAX_S = float(os.environ.get("CEREBROZEN_LLM_BACKOFF_MAX_S", "8"))
except ValueError:
    LLM_BACKOFF_MAX_S = 8.0
# Model fallback cascade: on persistent failure of the primary model, try the
# next (cheaper/faster) one — but only before any token has streamed to the user.
# Comma-separated; the node's requested model is always tried first regardless.
MODEL_CASCADE = [
    m.strip()
    for m in os.environ.get("CEREBROZEN_MODEL_CASCADE", "gpt-5-mini,gpt-5-nano").split(",")
    if m.strip()
]
# Circuit breaker: after N consecutive failures, short-circuit to the safe reply
# for COOLDOWN seconds instead of hammering a down OpenAI, then half-open to probe.
try:
    BREAKER_FAIL_THRESHOLD = max(1, int(os.environ.get("CEREBROZEN_BREAKER_FAILS", "5")))
except ValueError:
    BREAKER_FAIL_THRESHOLD = 5
try:
    BREAKER_COOLDOWN_S = float(os.environ.get("CEREBROZEN_BREAKER_COOLDOWN_S", "30"))
except ValueError:
    BREAKER_COOLDOWN_S = 30.0

# --- I/O tracing (verbose per-agent input/output, for execution tracing) -----
# ON by default. The `cerebrozen.trace` logger records, per turn: what profile_read
# extracted, each agent's INPUT (resolved system prompt + user message + history)
# and OUTPUT (raw model text + parsed reply/handoff/path/tokens/cost), and the
# background builders' input/output. Pure logging — no LLM calls, no added latency.
# Contains PII (profile, transcripts). Set CEREBROZEN_TRACE=false to disable.
# TRACE_CHARS truncates long fields (0 = no truncation / full text).
TRACE_IO = os.environ.get("CEREBROZEN_TRACE", "true").strip().lower() == "true"
try:
    TRACE_CHARS = int(os.environ.get("CEREBROZEN_TRACE_CHARS", "2000"))
except ValueError:
    TRACE_CHARS = 2000

# --- LLM content logging (request payload + response text in structured logs) --
# ON by default. Each llm.request / openai.response log record carries the
# truncated system prompt, user message, and raw response text — required for
# CloudWatch debugging (query by request_id to see exactly what was sent and received).
# Contains PII (user prompts, responses); set CEREBROZEN_LLM_LOG_CONTENT=false to disable.
#   CEREBROZEN_LLM_LOG_CONTENT_CHARS=N      → per-field truncation limit
#   CEREBROZEN_LLM_LOG_CONTENT_CHARS=0      → no truncation (full payload, default)
CEREBROZEN_LLM_LOG_CONTENT: bool = (
    os.environ.get("CEREBROZEN_LLM_LOG_CONTENT", "true").strip().lower() == "true"
)
try:
    CEREBROZEN_LLM_LOG_CONTENT_CHARS = int(os.environ.get("CEREBROZEN_LLM_LOG_CONTENT_CHARS", "0"))
except ValueError:
    CEREBROZEN_LLM_LOG_CONTENT_CHARS = 0

# --- OpenTelemetry (traces + metrics → ADOT collector → X-Ray / Prometheus) ---
# Uses the STANDARD OTEL SDK env vars (same convention the existing services use,
# already in SSM under /<env>/bot): OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_TRACES_EXPORTER,
# OTEL_METRICS_EXPORTER, OTEL_TRACES_SAMPLER_ARG, OTEL_SERVICE_NAME. Enabled when an
# exporter is set to something other than "none"/"". Export is async (BatchSpanProcessor
# / PeriodicExportingMetricReader on a background thread) → NO user-facing latency.
# Trace IDs use the X-Ray format (AwsXRayIdGenerator) so segments are valid in X-Ray.
OTEL_EXPORTER_OTLP_ENDPOINT = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
# "grpc" (port 4317) or "http/protobuf" (port 4318). Defaults to grpc.
OTEL_EXPORTER_OTLP_PROTOCOL = (
    os.environ.get("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc").strip().lower()
)
OTEL_SERVICE_NAME = os.environ.get("OTEL_SERVICE_NAME", "cerebrozen-langgraph").strip()
# Deployment env tag on every span/metric (dev / qa / prod).
OTEL_ENV = os.environ.get("OTEL_ENV", os.environ.get("ENV", "dev")).strip()
# Standard sampler arg = head sampling ratio (1.0 = all; 0.1 = 10%, as in dev SSM).
try:
    OTEL_SAMPLE_RATIO = float(os.environ.get("OTEL_TRACES_SAMPLER_ARG", "1.0"))
except ValueError:
    OTEL_SAMPLE_RATIO = 1.0
# Exporters: "none"/"" = off. Anything else (e.g. "otlp") = on. An endpoint is required.
_traces_exp = os.environ.get("OTEL_TRACES_EXPORTER", "none").strip().lower()
_metrics_exp = os.environ.get("OTEL_METRICS_EXPORTER", "none").strip().lower()
OTEL_TRACES_ENABLED = bool(OTEL_EXPORTER_OTLP_ENDPOINT) and _traces_exp not in ("", "none")
OTEL_METRICS_ENABLED = bool(OTEL_EXPORTER_OTLP_ENDPOINT) and _metrics_exp not in ("", "none")
# True if any OTEL export is active (drives whether we build the providers at boot).
OTEL_ENABLED = OTEL_TRACES_ENABLED or OTEL_METRICS_ENABLED

# --- Mongo (checkpointer + profile_read). All optional for local dev. --------
MONGO_DB_URL = os.environ.get("MONGO_DB_URL", "").strip()
# On deployed envs the SSM-bootstrapped MONGO_DB_URL is credential-less
# (e.g. "mongodb://host:27017") while the credentials live in the separate
# MONGO_DB_USERNAME / MONGO_DB_PASSWORD params. A bare connection authenticates
# anonymously: ping passes but every real op fails with "requires authentication"
# (no checkpoints, no transcripts, no profile reads). If creds are present and the
# URL carries none, inject them — single source of truth, no password duplicated
# into the URL param. No-op when the URL already embeds an "@" (e.g. local dev),
# so it stays idempotent across envs.
_MONGO_USER = os.environ.get("MONGO_DB_USERNAME", "").strip()
_MONGO_PWD = os.environ.get("MONGO_DB_PASSWORD", "").strip()
if (
    MONGO_DB_URL
    and _MONGO_USER
    and _MONGO_PWD
    and "://" in MONGO_DB_URL
    and "@" not in MONGO_DB_URL.split("://", 1)[1]
):
    from urllib.parse import quote_plus

    _scheme, _rest = MONGO_DB_URL.split("://", 1)
    MONGO_DB_URL = f"{_scheme}://{quote_plus(_MONGO_USER)}:{quote_plus(_MONGO_PWD)}@{_rest}"
MONGO_BACKEND_DB = os.environ.get("MONGO_DB_BACKEND_DB", "cerebrozen").strip()
# RASA db holds the queryable per-(user+bot) conversation transcripts (mirrors
# the OLD repo's UserConversationStore). Env var name matches the old service.
MONGO_RASA_DB = os.environ.get("RASA_DB", "rasa").strip()
MONGO_USER_CONVERSATIONS_COLLECTION = os.environ.get(
    "MONGO_USER_CONVERSATIONS_COLLECTION", "user_conversations"
).strip()
# NOT renamed with the CereBroZen rebrand: this is the name of a Mongo database that
# EXISTS. Renaming it points the app at an empty database and orphans every
# checkpointed session. Rename the database first, then this default.
MONGO_CHECKPOINT_DB = os.environ.get("MONGO_CHECKPOINT_DB", "cerebrozen_langgraph").strip()
MONGO_AGENTIC_COLLECTION = os.environ.get(
    "MONGO_AGENTIC_COLLECTION", "users_agentic_conversation_context"
).strip()
MONGO_USERS_COLLECTION = os.environ.get("MONGO_DB_USERS_COLLECTION", "users").strip()
# Self-reported wellness content (journal entries, sleep logs, mood check-ins). It lives
# in the ENGINE, not the platform, because the platform's schema is the "counts, never
# content" firewall — a journal in the org database is a journal an HR query can reach.
MONGO_WELLNESS_COLLECTION = os.environ.get(
    "MONGO_WELLNESS_COLLECTION", "users_wellness"
).strip()
# Whole Brain (NBI) thinking-preference report + DISC behavioral scores. Both live
# in the backend (cerebrozen) db, keyed by `userId` (the user_id string), and feed
# the userThinkingPreference / userBehavioralPreference placeholders in profile_read.
MONGO_NBI_COLLECTION = os.environ.get("MONGO_DB_NBI_COLLECTION", "nbi_report").strip()
MONGO_DISC_COLLECTION = os.environ.get("MONGO_DB_DISC_COLLECTION", "user_disc_scores").strip()
try:
    MONGO_TIMEOUT_MS = int(os.environ.get("MONGO_TIMEOUT_MS", "4000"))
except ValueError:
    # A typo here must not take the whole service down at import — see the other 29
    # numeric settings in this file, all of which fall back rather than raise.
    MONGO_TIMEOUT_MS = 4000
# User ids whose LLM calls are stubbed (smoke tests without burning tokens).
# Default EMPTY, deliberately: this used to ship two real production Mongo ObjectIds
# from the previous client, which both identified their users AND silently stubbed the
# coach for them in any environment that shared those ids. Set it per environment.
CEREBROZEN_TEST_USERS = {
    u.strip() for u in os.environ.get("CEREBROZEN_TEST_USERS", "").split(",") if u.strip()
}

# --- Redis hot-state tier (Phase 9) — per-session lock + profile cache --------
# Config-driven, optional for local dev (mirrors the optional-Mongo pattern). When
# REDIS_URL is set we connect to it (Docker locally / managed in prod); when it is
# unset OR the connection fails, we fall back to an in-process fakeredis so local
# runs need no Redis server. The Mongo checkpointer stays the DURABLE source of
# truth (Art. 8.1); Redis is only a hot tier (the per-session lock — Art. 8.4 — and
# a short-TTL profile_read cache).
REDIS_URL = os.environ.get("REDIS_URL", "").strip()
# Per-session turn lock (Art. 8.4: one in-flight turn per session, no checkpoint
# races). LOCK_TTL caps how long a held lock survives a crashed worker (auto-expire);
# LOCK_WAIT is how long a second concurrent turn blocks for the lock before it is
# rejected (0 = reject immediately when busy).
try:
    REDIS_LOCK_TTL_MS = max(1000, int(os.environ.get("CEREBROZEN_LOCK_TTL_MS", "120000")))
except ValueError:
    REDIS_LOCK_TTL_MS = 120000
try:
    REDIS_LOCK_WAIT_MS = max(0, int(os.environ.get("CEREBROZEN_LOCK_WAIT_MS", "30000")))
except ValueError:
    REDIS_LOCK_WAIT_MS = 30000
# profile_read cache TTL (per user). Helps a user who opens several sessions in
# quick succession skip repeat Mongo reads. 0 disables the cache.
try:
    REDIS_PROFILE_TTL_S = max(0, int(os.environ.get("CEREBROZEN_PROFILE_TTL_S", "60")))
except ValueError:
    REDIS_PROFILE_TTL_S = 60

# --- Voice (Phase 10): LiveKit cascade — Deepgram STT + ElevenLabs TTS -------
# A voice turn shares the SAME session_id (and thus brain/checkpoint) as text, so a
# user can switch between typing and talking in one session. The token endpoint
# mints a LiveKit room token; a separate agent worker (app/voice/agent.py) joins the
# room and runs STT -> the existing LangGraph brain -> TTS, with barge-in.
LIVEKIT_URL = os.environ.get("LIVEKIT_URL", "").strip()          # wss://<project>.livekit.cloud
LIVEKIT_API_KEY = os.environ.get("LIVEKIT_API_KEY", "").strip()
LIVEKIT_API_SECRET = os.environ.get("LIVEKIT_API_SECRET", "").strip()
# STT/TTS provider keys (read by the agent worker's plugins; accepted under either
# common spelling for ElevenLabs).
DEEPGRAM_API_KEY = os.environ.get("DEEPGRAM_API_KEY", "").strip()
ELEVEN_API_KEY = (
    os.environ.get("ELEVENLABS_API_KEY") or os.environ.get("ELEVEN_API_KEY") or ""
).strip()
# Model/voice knobs (overridable per env).
VOICE_STT_MODEL = os.environ.get("CEREBROZEN_VOICE_STT_MODEL", "nova-3").strip()
# Deepgram STT language. "multi" enables nova-3 multilingual / code-switching
# (English + Hindi in one utterance). Use "hi" to force Hindi-only, "en" for
# English-only, or "" to keep the plugin default. Requires the nova-3 model for "multi".
VOICE_STT_LANGUAGE = os.environ.get("CEREBROZEN_VOICE_STT_LANGUAGE", "multi").strip()
# TTS provider: "elevenlabs" (default) or "deepgram" (Aura). Deepgram Aura reuses
# the Deepgram key/credit — useful when the ElevenLabs free quota (10k chars/mo) is
# exhausted. Swap with CEREBROZEN_VOICE_TTS_PROVIDER.
VOICE_TTS_PROVIDER = os.environ.get("CEREBROZEN_VOICE_TTS_PROVIDER", "elevenlabs").strip().lower()
# eleven_v3 = best quality (only stability param supported; emotion via audio tags).
# eleven_multilingual_v2 = studio/v2. eleven_turbo_v2_5 / eleven_flash_v2_5 for speed.
VOICE_TTS_MODEL = os.environ.get("CEREBROZEN_VOICE_TTS_MODEL", "eleven_multilingual_v2").strip()
VOICE_DEEPGRAM_TTS_MODEL = os.environ.get(
    "CEREBROZEN_VOICE_DEEPGRAM_TTS_MODEL", "aura-2-andromeda-en"
).strip()
VOICE_TTS_VOICE_ID = os.environ.get("CEREBROZEN_VOICE_TTS_VOICE_ID", "").strip()  # "" → plugin default

# Voice Lab: voices available in the boss's tuning console.
# Format: "voice_id:Display Name,voice_id:Display Name,..."
# The currently configured voice is always prepended if not already in the list.
_voice_ids_raw = os.environ.get("CEREBROZEN_VOICE_AVAILABLE_IDS", "").strip()
VOICE_AVAILABLE_IDS: list[dict] = []
for _pair in _voice_ids_raw.split(","):
    _pair = _pair.strip()
    if ":" in _pair:
        _vid, _vname = _pair.split(":", 1)
        VOICE_AVAILABLE_IDS.append({"id": _vid.strip(), "name": _vname.strip()})
    elif _pair:
        VOICE_AVAILABLE_IDS.append({"id": _pair, "name": _pair})
if VOICE_TTS_VOICE_ID and not any(v["id"] == VOICE_TTS_VOICE_ID for v in VOICE_AVAILABLE_IDS):
    VOICE_AVAILABLE_IDS.insert(0, {"id": VOICE_TTS_VOICE_ID, "name": "Current"})

# ElevenLabs VoiceSettings — match the sliders shown in the ElevenLabs console for
# the chosen voice. When none are set here the plugin sends no voice_settings block,
# which lets ElevenLabs use whatever its backend default is (may differ from the
# console preview if the voice has custom saved settings). Set these explicitly so
# the integration sounds identical to the console. All tunable via env vars.
try:
    VOICE_TTS_STABILITY = float(os.environ.get("CEREBROZEN_VOICE_STABILITY", "0.5"))
except ValueError:
    VOICE_TTS_STABILITY = 0.5
try:
    VOICE_TTS_SIMILARITY_BOOST = float(os.environ.get("CEREBROZEN_VOICE_SIMILARITY", "0.75"))
except ValueError:
    VOICE_TTS_SIMILARITY_BOOST = 0.75
try:
    VOICE_TTS_STYLE = float(os.environ.get("CEREBROZEN_VOICE_STYLE", "0.0"))
except ValueError:
    VOICE_TTS_STYLE = 0.0
try:
    VOICE_TTS_SPEED = float(os.environ.get("CEREBROZEN_VOICE_SPEED", "0.86"))
except ValueError:
    VOICE_TTS_SPEED = 0.86
VOICE_TTS_SPEAKER_BOOST = (
    os.environ.get("CEREBROZEN_VOICE_SPEAKER_BOOST", "true").strip().lower() == "true"
)
try:
    VOICE_TOKEN_TTL_S = max(60, int(os.environ.get("CEREBROZEN_VOICE_TOKEN_TTL_S", "3600")))
except ValueError:
    VOICE_TOKEN_TTL_S = 3600
# Turn endpointing: how long to wait after you stop speaking before committing the
# turn. Raised to 3.0s (from 2.0s) — at 2.0s a natural thinking pause was still
# triggering early submission before the user finished their thought.
# Combined with Deepgram's endpointing_ms below, the effective gate is whichever
# fires last; 3.0s ensures a deliberate 3-second silence is required before the
# brain fires. Lower with CEREBROZEN_VOICE_MIN_ENDPOINT_S for snappier replies.
try:
    VOICE_MIN_ENDPOINTING_DELAY = float(os.environ.get("CEREBROZEN_VOICE_MIN_ENDPOINT_S", "3.0"))
except ValueError:
    VOICE_MIN_ENDPOINTING_DELAY = 3.0
try:
    VOICE_MAX_ENDPOINTING_DELAY = float(os.environ.get("CEREBROZEN_VOICE_MAX_ENDPOINT_S", "6.0"))
except ValueError:
    VOICE_MAX_ENDPOINTING_DELAY = 6.0
# Deepgram silence-before-final. The plugin default (25ms) is too eager; 2000ms
# still finalized on 2.0s mid-thought pauses and triggered early turns. 3000ms
# requires a clearly deliberate 3-second pause before Deepgram emits a final, which
# aligns with the min_endpointing_delay above. Tune with CEREBROZEN_VOICE_STT_ENDPOINT_MS.
try:
    VOICE_STT_ENDPOINTING_MS = max(25, int(os.environ.get("CEREBROZEN_VOICE_STT_ENDPOINT_MS", "3000")))
except ValueError:
    VOICE_STT_ENDPOINTING_MS = 3000
# Interruption hardening: a stray/late STT final (often 1–2 words) must NOT cut
# CereBroZen off mid-reply. Require N words to count as a real interruption, and if an
# "interruption" turns out false (no sustained speech), resume his reply.
try:
    VOICE_MIN_INTERRUPTION_WORDS = max(0, int(os.environ.get("CEREBROZEN_VOICE_MIN_INTERRUPT_WORDS", "3")))
except ValueError:
    VOICE_MIN_INTERRUPTION_WORDS = 3
try:
    VOICE_FALSE_INTERRUPTION_TIMEOUT = float(os.environ.get("CEREBROZEN_VOICE_FALSE_INTERRUPT_S", "2.0"))
except ValueError:
    VOICE_FALSE_INTERRUPTION_TIMEOUT = 2.0

# Barge-in: whether speaking WHILE CereBroZen is talking interrupts him. Disabled by
# default — he finishes his sentence, then listens. This avoids stray STT finals
# cutting him off and pairs with the longer endpointing above. Re-enable with
# CEREBROZEN_VOICE_BARGE_IN=true → AgentSession.allow_interruptions=True.
VOICE_BARGE_IN = os.environ.get("CEREBROZEN_VOICE_BARGE_IN", "false").strip().lower() == "true"

# UI events over the voice data channel (Phase 10 follow-up): publish the turn's
# non-speech fields (phase_buttons, active_phase, coaching_path, handoff_ready, ...)
# to the client over LiveKit's reliable data channel, alongside the spoken reply.
# Flag-gated (still independently toggleable from the turn-reliability fix — if QA
# sees something new, disabling it in isolation tells us whether this code path is
# responsible), but ON by default now that it's shipped.
# CEREBROZEN_VOICE_UI_EVENTS=false to disable.
VOICE_UI_EVENTS_ENABLED = os.environ.get("CEREBROZEN_VOICE_UI_EVENTS", "true").strip().lower() == "true"

# Inline action cards on voice UI events. Default TRUE (ship them, unchanged
# behaviour) — the FE's voice→text auto-switch contract is unconfirmed, so the
# single-source suppression is OPT-IN per environment: set
# CEREBROZEN_VOICE_INLINE_ACTIONS=false to publish voice events with actions → []
# so each card is delivered exactly once, via the text-mode turn payload /
# history where the user can act on it. (In voice the cards are un-actionable;
# the voice-channel copy rendered a dead row that duplicated the text-mode copy
# after the auto-switch — QA 2026-07-11 duplicate-inlay-card report. The `stage`
# field rides the event either way, so the auto-switch keeps its signal.)
VOICE_INLINE_ACTIONS_ENABLED = os.environ.get("CEREBROZEN_VOICE_INLINE_ACTIONS", "true").strip().lower() == "true"

# --- Input validation: user-message length cap -------------------------------
# Caps the client-supplied `message`/`text` on every turn request. The session
# `title` is stored verbatim as the FIRST user message (see
# app/stores/conversation.py), so this single cap bounds both. Configurable per
# env; default 5000 chars.
try:
    MAX_USER_MESSAGE_CHARS = max(
        1, int(os.environ.get("CEREBROZEN_MAX_USER_MESSAGE_CHARS", "5000"))
    )
except ValueError:
    MAX_USER_MESSAGE_CHARS = 5000

# --- Chat title generation (LLM-generated, Claude-style short titles) --------
# Replaces the old behaviour of storing the user's first message verbatim as the
# session title (see app/llm/title_generator.py). Cheap/fast model — a title is a
# few words, not a coaching reply.
TITLE_GENERATION_MODEL = os.environ.get("CEREBROZEN_TITLE_MODEL", "gpt-5-nano").strip()

# --- Home-screen greeting (LLM-generated, varying) ----------------------------
# See app/llm/greeting_generator.py. Cheap/fast model — a greeting is one short
# line, not a coaching reply.
GREETING_GENERATION_MODEL = os.environ.get("CEREBROZEN_GREETING_MODEL", "gpt-5-nano").strip()

# --- CORS (browser frontends call /v1/webhook cross-origin) ------------------
# Comma-separated allowed origins. Default "*" (any origin). Browsers forbid the
# "*" wildcard together with credentials, so credentials are only enabled when
# explicit origins are configured. Restrict in prod, e.g.:
#   CEREBROZEN_CORS_ORIGINS="https://qa.example.com,https://app.example.com"
CORS_ALLOW_ORIGINS = [
    o.strip() for o in os.environ.get("CEREBROZEN_CORS_ORIGINS", "*").split(",") if o.strip()
]

# --- Auth (JWT, HS512 shared secret — same scheme as the other CereBroZen --------
# services, so tokens validate across services). Enforced whenever a secret is
# configured; running local (ENV=local) with no secret skips auth for dev.
ENV = os.environ.get("ENV", "local").strip().lower()

# Production must never run with wildcard CORS. In a dev-class env "*" is a convenience;
# anywhere else it lets ANY origin call the coaching API from a browser with a token, so a
# forgotten CEREBROZEN_CORS_ORIGINS becomes a silent hole. Refuse to boot instead — the
# failure mode of the mistake is "won't start", not "wide open" (same posture as the
# platform's guard_production and the AUTH_DEV_BYPASS refusal in auth/dependencies.py).
_CORS_DEV_ENVS = {"local", "dev", "development", "test", "ci"}
if ENV not in _CORS_DEV_ENVS and CORS_ALLOW_ORIGINS == ["*"]:
    raise RuntimeError(
        f"CEREBROZEN_CORS_ORIGINS is '*' (any origin) but ENV={ENV!r} is not a dev "
        "environment. Set an explicit comma-separated allowlist of origins for production."
    )

# --- At-rest encryption (datastore-layer, attested) -----------------------------
# At-rest encryption of transcripts/coaching content is a DATASTORE concern for this
# stack: Postgres/Mongo transparent encryption, or an encrypted volume. It is deliberately
# NOT app-layer field encryption — that would add a native crypto dependency the project
# avoids (see the platform's stdlib-PBKDF2 note) and break content queryability, for
# defense that the datastore layer already provides. The app CANNOT verify the datastore
# is encrypted; it carries the operator's ATTESTATION so a deployment can't silently
# believe it has at-rest encryption it never turned on. See docs/SECURITY.md.
#   unset/unrecognized -> None ("unknown"): warned in a deployed env, surfaced at /health.
#   "true"/"false" (and yes/no/on/off/1/0) as declared.
def _parse_bool_attestation(raw: str):
    """Parse an attestation flag to True / False / None(unknown). Pure — testable."""
    return {
        "true": True, "1": True, "yes": True, "on": True,
        "false": False, "0": False, "no": False, "off": False,
    }.get((raw or "").strip().lower())


DATASTORE_ENCRYPTED = _parse_bool_attestation(
    os.environ.get("CEREBROZEN_DATASTORE_ENCRYPTED", "")
)


def _datastore_attestation_warning(env: str, attested):
    """The boot warning when a DEPLOYED env hasn't attested at-rest encryption, else None.

    Not a hard refuse (unlike wildcard CORS): the app fully controls CORS but cannot turn
    on datastore encryption — only the operator can. So an unattested deployed env gets a
    loud boot warning + an honest /health, not a crash.
    """
    if env in _CORS_DEV_ENVS or attested is True:
        return None
    return (
        "CEREBROZEN_DATASTORE_ENCRYPTED is not 'true' in a deployed env — transcripts and "
        "coaching content may be at rest UNENCRYPTED. Enable datastore/volume encryption "
        "and set CEREBROZEN_DATASTORE_ENCRYPTED=true (docs/SECURITY.md)."
    )


_enc_warning = _datastore_attestation_warning(ENV, DATASTORE_ENCRYPTED)
if _enc_warning:
    logging.getLogger("cerebrozen.config").warning(
        "config.datastore_encryption_unattested",
        extra={"env": ENV,
               "declared": os.environ.get("CEREBROZEN_DATASTORE_ENCRYPTED", "").strip() or "(unset)",
               "detail": _enc_warning},
    )

# The tenant-defaults guard lives at the BOTTOM of this module — it has to inspect values
# (RAG_S3_BUCKET) that are not defined until much further down. See "TENANT-SPECIFIC
# RESOURCE NAMES" at the end of the file.

JWT_ALGORITHM = os.environ.get("JWT_ALGORITHM", "HS512").strip()
# App-layer tenancy: when auth is enforced, tokens must carry an `org_id` claim —
# an org-less token would silently read/write the default org's data. Only set
# this to false for single-tenant deployments that consciously accept that.
REQUIRE_ORG_CLAIM = os.environ.get("CEREBROZEN_REQUIRE_ORG_CLAIM", "true").strip().lower() != "false"
# JWT_SECRET is base64-encoded (matches the reference service); decode to bytes.
_JWT_SECRET_B64 = os.environ.get("JWT_SECRET", "").strip()
try:
    JWT_SECRET = base64.b64decode(_JWT_SECRET_B64) if _JWT_SECRET_B64 else b""
except Exception:  # noqa: BLE001 — malformed secret -> treat as unset
    JWT_SECRET = b""

# --- challenge_context: real multi-turn discovery agent (default ON). It runs the
# authored prompt, explores the challenge, and emits coaching_path (CIM/CBT/CH) via
# structured output. Set CEREBROZEN_STUB_CHALLENGE=true to fall back to the Phase-1
# stub (no LLM, hardcodes CIM, hands off after one turn) for CIM-only validation.
STUB_CHALLENGE = os.environ.get("CEREBROZEN_STUB_CHALLENGE", "false").strip().lower() != "false"

# --- Repeat-user check-in: the 7-day rule ------------------------------------
# A prior session's committed actions become eligible for check-in exactly
# `CHECKIN_DUE_DAYS` days after the session that produced them (BRD §3.1, R1):
# session_date + CHECKIN_DUE_DAYS <= today (compared as calendar dates). A repeat
# user who returns BEFORE the window elapses gets no check-in (and intake skips
# too) → straight to coaching. Tunable per env without a redeploy.
try:
    CHECKIN_DUE_DAYS = max(0, int(os.environ.get("CEREBROZEN_CHECKIN_DUE_DAYS", "7")))
except ValueError:
    CHECKIN_DUE_DAYS = 7

# --- Multi-path routing (CBT / Capability) — default ON ----------------------
# The router honours the coaching_path challenge_context emits: CIM→core, CBT→cbt,
# CH→capability. A path whose prompt isn't authored yet (e.g. Capability) falls back
# to CIM in _run_path_stage, so this is safe to leave on. Set
# CEREBROZEN_ENABLE_MULTIPATH=false to force CIM-only routing for validation.
ENABLE_MULTIPATH = os.environ.get("CEREBROZEN_ENABLE_MULTIPATH", "true").strip().lower() == "true"

# --- Agent enable/disable lives in the workbook Catalog tab, NOT here ---------
# The repeat-user check-in, simulation (role-play / SJT), learning-aid, and the
# closing feedback/mood-capture agents are switched on/off by the `enabled` column
# of the Catalog tab in agent_prompts.xlsx — control sits with the prompt
# engineers, not in code/env. The graph reads it via PromptRegistry.is_enabled()
# (an agent absent from the catalog is treated as DISABLED). The closing
# feedback/mood-capture agent is the SOLE legitimate path to a terminal `close`
# (ALWAYS_ENABLED in the registry), so a session can never end before it.
#
# Which simulation runs is decided by simulation_decision_agent (when enabled in
# the Catalog) or by the deterministic `specific_person_identified` gate — there
# is no code-level default to pin, so no DEFAULT_SIMULATION setting.

# Test-only: force the named stages to hand off right after their real LLM call,
# so a live end-to-end run can advance coaching -> simulation -> close without
# manually playing each agent's full arc. Empty by default (no effect on prod).
# CEREBROZEN_FORCE_HANDOFF="all" (every LLM stage) or a comma list of sheet-names,
# e.g. "core_coaching_agent,role_play_agent".
_force_handoff = os.environ.get("CEREBROZEN_FORCE_HANDOFF", "").strip()
FORCE_HANDOFF_STAGES = (
    {"__all__"}
    if _force_handoff.lower() in {"true", "all", "1"}
    else {s.strip() for s in _force_handoff.split(",") if s.strip()}
)

# --- Structured-output enforcement (JSON-object mode) ------------------------
# Stages listed here call the LLM with `text.format = json_object`, forcing the whole
# response to be a valid JSON object so the model can't drop the envelope for plain
# prose (the CH phase-routing / milestone bug). Comma-list of stage names, or "all".
# OFF by default — enable per stage after QA validation, e.g.
#   CEREBROZEN_JSON_OUTPUT_STAGES=CH_coaching_agent
_json_output = os.environ.get("CEREBROZEN_JSON_OUTPUT_STAGES", "").strip()
JSON_OUTPUT_STAGES = (
    {"__all__"}
    if _json_output.lower() in {"true", "all", "1"}
    else {s.strip() for s in _json_output.split(",") if s.strip()}
)

# --- Background builders (actions/insights + user-context) -------------------
# Always-on, OFF the request path (run async after a turn, never block the reply).
# They CAPTURE actions/insights per coaching turn and the user-context model at
# session close, persisting to the per-user agentic store so user_profile_retrieval
# (profile_read) can read them back next session. ON by default since they don't
# touch request latency; set CEREBROZEN_ENABLE_BUILDERS=false to disable.
ENABLE_BUILDERS = os.environ.get("CEREBROZEN_ENABLE_BUILDERS", "true").strip().lower() != "false"
# Right-sized cheaper model for the silent builders (override per env).
BUILDER_MODEL = os.environ.get("CEREBROZEN_BUILDER_MODEL", "gpt-5-mini").strip()
# Actions/insights generation is now handled by dynamic_actions_insights_node
# (a first-class graph node) rather than a background builder triggered by agent
# completion. This set is kept as an empty sentinel so scripts/tests that reference
# it don't break; it is no longer consulted by the engine.
BUILDER_TRIGGER_AGENTS: set = set()

# Cross-session memory: max characters of prior-session VERBATIM transcript injected
# into a returning user's context (via the {pastConversation} placeholder). Bounds
# the context so very long histories stay sane — the most recent content is kept and
# the oldest is trimmed. Set 0 to disable trimming (inject everything).
try:
    PAST_CONVERSATION_MAX_CHARS = int(
        os.environ.get("CEREBROZEN_PAST_CONVERSATION_MAX_CHARS", "40000")
    )
except ValueError:
    # As above: undefended int() at import = every worker dies on boot from one typo.
    PAST_CONVERSATION_MAX_CHARS = 40000

# FALLBACK ROI-metric ("Development Area") catalogue. The live list is parsed from
# the dynamic_actions_insights_agent prompt's "ROI Metric Mapping" block at runtime
# (see app/roi_metrics.py) so the picker always matches what the agent emits and the
# workbook stays the single source of truth. This bundled copy is used ONLY when the
# prompt is missing/unparseable, so the picker never goes down. Keep it roughly in
# sync as a safety net.
ROI_METRICS = [
    "Mental & emotional state",
    "Inspiration",
    "Managing conflicts",
    "Future orientation",
    "Creativity & innovation",
    "Decision making",
    "Inclusion",
    "Contribution & giving back",
    "Delegation",
    "Clarity of purpose",
    "Upskilling",
    "Level of stress",
    "Collaboration",
    "Self-confidence",
    "Job satisfaction",
    "Goal setting",
    "Resilience",
    "Intellectual growth",
    "Ownership",
    "Building relationships",
    "Continuous improvement & learning",
    "Communication",
    "Time management",
    "Assertiveness",
]

# --- Strangler-fig selector (kill-switch + gradual rollout) ------------------
# Everything runs on the new graph; there is no legacy backend (the old system is
# reference-only). The selector is the reversible safety valve in front of the
# engine: a global on/off flag, force-on allowlist, force-off blocklist, a %
# rollout, and a per-request override. When a request is NOT enabled it gets a
# clean "service disabled" reply (no crash, no proxy). Default: ON for everyone.
GRAPH_ENABLED = os.environ.get("CEREBROZEN_GRAPH_ENABLED", "true").strip().lower() != "false"
GRAPH_ALLOWLIST = {
    u.strip() for u in os.environ.get("CEREBROZEN_GRAPH_ALLOWLIST", "").split(",") if u.strip()
}
GRAPH_BLOCKLIST = {
    u.strip() for u in os.environ.get("CEREBROZEN_GRAPH_BLOCKLIST", "").split(",") if u.strip()
}
try:
    GRAPH_PERCENT = max(0, min(100, int(os.environ.get("CEREBROZEN_GRAPH_PERCENT", "100") or 100)))
except ValueError:
    GRAPH_PERCENT = 100

# Reply returned when the selector gates a request off (kill-switch engaged).
GRAPH_DISABLED_MESSAGE = os.environ.get(
    "CEREBROZEN_GRAPH_DISABLED_MESSAGE",
    "Coaching is briefly unavailable right now. Please try again shortly.",
)

# --- RAG (CSKB + SSKB as in-process tools, not agents) -----------------------
# The retrieval layer is a library inside this process (constitution: "data is
# tools, not agents; own FAISS/vector store stays in-process, not MCP"). Two
# LanceDB tables keep SSKB (global) and CSKB (per-org) structurally isolated, so
# a missing org filter can never leak one client's docs into another's.
#
# S3 layout (single RAG data bucket):
#   s3://<RAG_S3_BUCKET>/<RAG_SSKB_PREFIX>/...                (CereBroZen-global docs)
#   s3://<RAG_S3_BUCKET>/<RAG_CSKB_PREFIX>/<orgId>/<group>/.. (per-client docs)
RAG_S3_BUCKET = (
    os.environ.get("RAG_S3_BUCKET")
    or os.environ.get("S3_BUCKET_NAME")
    # NOT renamed with the CereBroZen rebrand: this is a REAL S3 bucket name. Renaming it
    # points RAG at a bucket that does not exist (every extraction then returns null,
    # silently — the coach just loses its concepts and learning aids).
    or "dev-cerebrozen-rag-agent-data"
).strip()
RAG_SSKB_PREFIX = os.environ.get("RAG_SSKB_PREFIX", "sskb").strip().strip("/")
RAG_CSKB_PREFIX = os.environ.get("RAG_CSKB_PREFIX", "cskb").strip().strip("/")
AWS_REGION = os.environ.get("AWS_REGION", "us-east-1").strip()

# The "embeddings folder" prefix inside the bucket — ingestion IGNORES any source
# key under it so the vector store's own files are never mistaken for documents.
# Code default; override via the RAG_LANCEDB_PREFIX param.
RAG_LANCEDB_PREFIX = os.environ.get("RAG_LANCEDB_PREFIX", "lancedb").strip().strip("/")
# The vector store lives in S3, in the SAME bucket under RAG_LANCEDB_PREFIX. The URI
# is CONSTRUCTED from the bucket (no separate param) so the index is durable across
# EC2 recycling and shared across instances. Falls back to a repo-local dir only
# when no bucket is configured (pure-offline / tests).
RAG_LANCEDB_URI = (
    f"s3://{RAG_S3_BUCKET}/{RAG_LANCEDB_PREFIX}" if RAG_S3_BUCKET else str(REPO_ROOT / ".lancedb")
)
RAG_SSKB_TABLE = os.environ.get("RAG_SSKB_TABLE", "sskb").strip()
RAG_CSKB_TABLE = os.environ.get("RAG_CSKB_TABLE", "cskb").strip()

# Startup ingestion behaviour (see app/rag/startup.py):
#   RAG_INGEST_ON_STARTUP (default true): on boot, connect to the existing index
#     and INCREMENTALLY embed only docs not already indexed (dedup by S3 key+ETag);
#     already-indexed docs are skipped — no re-embedding.
#   RAG_REINDEX (default false): drop the vector store and re-embed everything from
#     scratch. Set it for a one-off rebuild, then unset (else every boot re-embeds).
RAG_INGEST_ON_STARTUP = os.environ.get("RAG_INGEST_ON_STARTUP", "true").strip().lower() != "false"
RAG_REINDEX = os.environ.get("RAG_REINDEX", "false").strip().lower() == "true"

# Models. Embedding is shared with the old service so vectors are comparable;
# the (optional) extraction LLM is the cheap RAG-tier model, never the coaching
# model, and only runs in the pre-step (never the streamed turn).
RAG_EMBED_MODEL = os.environ.get("RAG_EMBED_MODEL", "text-embedding-3-small").strip()
RAG_LLM_MODEL = os.environ.get("RAG_LLM_MODEL", "gpt-4o-mini").strip()
try:
    RAG_TOP_K = max(1, int(os.environ.get("RAG_TOP_K", "5")))
except ValueError:
    RAG_TOP_K = 5

# RAG result cache (Phase 9/voice latency): once an extraction is fetched, cache its
# result (in the Redis hot tier) keyed by org + extraction + resolved-query hash, so
# subsequent turns/sessions with the same retrieval load from cache instead of
# re-running embedding + vector search (+ the cheap RAG LLM). Tenant-isolated by
# org_id. 0 disables. Org/user-stable extractions (values, competencies, learning
# aids) get near-100% hit rate; query-varying ones re-fetch correctly (different
# query → different key).
try:
    RAG_CACHE_TTL_S = max(0, int(os.environ.get("RAG_CACHE_TTL_S", "3600")))
except ValueError:
    RAG_CACHE_TTL_S = 3600

# The extraction registry sheet lives in the SAME workbook as the prompts, so the
# business team edits queries/criteria/output specs in Excel with the same
# versioned/reversible S3 flow. Empty/missing sheet -> the in-code seed is used.
# Sheet is named "extraction" (singular) in agent_prompts.xlsx — must match exactly,
# _load_sheet() does a case-sensitive exact match and silently no-ops otherwise.
RAG_REGISTRY_SHEET = os.environ.get("RAG_REGISTRY_SHEET", "extraction").strip()

# Org collection (Mongo) — Extract3 (client values) reads from the DB, NOT S3.
# Field mapping is finalized once a sample `org` document is provided.
MONGO_ORG_COLLECTION = os.environ.get("MONGO_DB_ORG_COLLECTION", "org").strip()

# Dynamic session variable store: one document per user, keyed by user_id.
# Captures variables_set emitted by any agent node and persists them with
# per-variable provenance (session_id, stage, turn_seq, generated_at) so
# CloudWatch queries can trace exactly which turn/agent produced each value.
# Once-in-lifetime vars (intake) are never overwritten once set.
MONGO_DYNAMIC_VARS_COLLECTION = os.environ.get(
    "MONGO_DYNAMIC_VARS_COLLECTION", "agentic_session_dynamic_variables"
).strip()

# Warm the NEXT agent's prompt prefix into the local model's KV cache the moment a
# stage hands off (offline/Ollama only — OpenAI caches server-side). A 27K-token
# prompt costs ~10s to re-read cold; prewarming moves that off the user's turn.
ENABLE_PREWARM = os.environ.get("CEREBROZEN_ENABLE_PREWARM", "true").strip().lower() == "true"

# --- White-label branding ------------------------------------------------------
# The product name the COACH calls itself, and the UI displays. Set per client so a
# new deployment is a config change, not a fork.
#
# NOTE: this covers CODE (the identity line in guardrails, greeting/title prompts,
# the UI). The brand also appears ~91 times inside the PROMPT WORKBOOK, which is
# client-editable content — a new client edits those in their own workbook.
BRAND_NAME = os.environ.get("CEREBROZEN_BRAND_NAME", "CereBroZen").strip() or "CereBroZen"


# ── TENANT-SPECIFIC RESOURCE NAMES ───────────────────────────────────────────
#
# Every default below names a resource belonging to the FIRST tenant. They are real: a
# Mongo database and an S3 bucket that exist, hold data, and answer. They are deliberately
# NOT renamed to match the CereBroZen rebrand, because renaming a default does not rename the
# resource — it just points the app at a database that does not exist and orphans every
# checkpointed session. (Learned the hard way: this rename was attempted and reverted.)
#
# That makes them the sharpest edge in white-labelling this product. A second tenant who
# forgets one of these env vars does not get an error. They get:
#
#   MONGO_CHECKPOINT_DB → sessions written into a database named after another client.
#   RAG_S3_BUCKET       → RAG pointed at another client's bucket, which their AWS account
#                         cannot read, so every extraction returns null and the coach
#                         silently loses its concepts, frameworks and learning aids. It
#                         degrades CLEANLY, with no error. Nobody notices. That is the
#                         entire danger — it is exactly the state this dev box is in today.
#
# Hence two guards, both defined here:
#
#   1. On a deployed ENV, falling through to any default is warned about (as it always was
#      for the Mongo DB names — now extended to the checkpoint DB and the RAG bucket, which
#      were the two it did not cover, and the two that matter most for resale).
#   2. A second tenant sets CEREBROZEN_STRICT_TENANT=true and any incumbent default becomes a
#      HARD startup failure. They cannot ship pointing at the first client's infrastructure
#      even by accident. The incumbent does not set the flag and is entirely unaffected.
#
# See docs/WHITE_LABEL.md for the handover checklist this enforces.
_TENANT_DEFAULTS = {
    "MONGO_DB_BACKEND_DB": ("cerebrozen", MONGO_BACKEND_DB),
    "RASA_DB": ("rasa", MONGO_RASA_DB),
    "MONGO_CHECKPOINT_DB": ("cerebrozen_langgraph", MONGO_CHECKPOINT_DB),
    "RAG_S3_BUCKET": ("dev-cerebrozen-rag-agent-data", RAG_S3_BUCKET),
}

STRICT_TENANT = os.environ.get("CEREBROZEN_STRICT_TENANT", "").strip().lower() in ("1", "true", "yes")


def tenant_values_at_incumbent_default() -> list:
    """Which tenant-specific settings still name the FIRST tenant's real resources."""
    return [param for param, (default, actual) in _TENANT_DEFAULTS.items() if actual == default]


_at_default = tenant_values_at_incumbent_default()

if STRICT_TENANT and _at_default:
    raise RuntimeError(
        "CEREBROZEN_STRICT_TENANT is set, but these settings still name the FIRST TENANT's "
        f"real resources: {', '.join(_at_default)}. This deployment would read or write "
        "another client's infrastructure. Point them at your own, or unset "
        "CEREBROZEN_STRICT_TENANT if you are the incumbent. See docs/WHITE_LABEL.md."
    )

if ENV not in ("local", "") and _at_default:
    import logging as _logging

    _cfg_log = _logging.getLogger("cerebrozen.config")
    for _param in _at_default:
        _cfg_log.warning(
            "config.tenant_value_at_default",
            extra={
                "param": _param,
                "value": _TENANT_DEFAULTS[_param][1],
                "env": ENV,
                "detail": (
                    f"ENV={ENV!r} but {_param} was not found in SSM (/{ENV}/bot/{_param}); "
                    f"falling back to the hard-coded default "
                    f"'{_TENANT_DEFAULTS[_param][1]}', which names the FIRST TENANT's "
                    "infrastructure. Data may land in — or be read from — the wrong place. "
                    "Add the param to SSM Parameter Store."
                ),
            },
        )


# ── REGULATED-WORKPLACE MODE (EU AI Act and equivalents) ─────────────────────
#
# This product does two things that are, in an EMPLOYMENT context, legally loaded:
#
#   1. It INFERS EMOTIONS. `feedback_mood_capture_agent` maps each session onto a canonical
#      set of emotions and persists them per user (`mapped_emotions`, `positive_emotions`,
#      `negative_emotions` — see stores/agentic._MOOD_FIELDS).
#   2. It SCORES THE PERSON. The Coachable Index is 8 dimensions plus a weighted
#      `coachability_score`, captured ONCE IN A LIFETIME — a durable rating of a worker.
#
# Under the EU AI Act, inferring emotions of a natural person IN THE WORKPLACE is a
# PROHIBITED practice (Art. 5), and AI used in employment / worker management is HIGH-RISK
# (Annex III). A coaching tool sold to an employer, that reads its users' emotions and
# scores them, is not on the edge of that — it is in the middle of it.
#
# This is not legal advice and it does not substitute for counsel. What it does is make the
# question ANSWERABLE, and make the answer enforceable in one line of config: a deployment
# that must not do these things can be configured not to, and a test proves it doesn't.
#
# CEREBROZEN_EMOTION_CAPTURE=false  → no emotion is inferred, mapped, or stored. Ever.
# CEREBROZEN_PERSON_SCORING=false   → no durable per-person score (ci_*, coachability_score)
#                                   is captured or persisted.
#
# REGULATED MODE IS THE DEFAULT. CereBroZen's stated posture (docs/SECURITY.md in the
# product repo) is that a new tenant starts with emotion inference and person-scoring
# OFF; turning them on is a conscious, contract-level decision made with counsel —
# `CEREBROZEN_REGULATED_WORKPLACE=false` — not something a deployment drifts into
# because nobody set a flag. (The reference project defaulted these ON for its
# incumbent tenant; we have no incumbent, so the safe direction wins.)
_REGULATED = (
    os.environ.get("CEREBROZEN_REGULATED_WORKPLACE", "").strip().lower() or "true"
) in ("1", "true", "yes")


def _feature_on(name: str) -> bool:
    raw = os.environ.get(name, "").strip().lower()
    if raw in ("0", "false", "no"):
        return False
    if raw in ("1", "true", "yes"):
        return True
    return not _REGULATED          # unset → on, unless regulated mode says otherwise


EMOTION_CAPTURE_ENABLED = _feature_on("CEREBROZEN_EMOTION_CAPTURE")
PERSON_SCORING_ENABLED = _feature_on("CEREBROZEN_PERSON_SCORING")

# ── SELF-REPORT IS NOT INFERENCE ─────────────────────────────────────────────
#
# The two flags above govern what the SYSTEM does TO a person: it reads their emotions
# from what they wrote, and it scores them. That is the prohibited/high-risk direction,
# and regulated mode turns it off.
#
# A journal entry, a sleep log, a mood slider the person moved themselves is the opposite
# direction: the person's own record, written by them, for them. Nobody inferred anything
# and nobody is scored. Storing a diary for its author is not emotion recognition, and a
# regulated tenant does not lose their diary — so this does NOT hang off _REGULATED.
#
# It is still a separate switch, because a tenant whose counsel wants NO affect-adjacent
# data on the vendor's disks at all must be able to say so in one line, and have a test
# prove it. Default ON: the feature exists, and the person owns what it holds.
#
# The invariant that makes this safe is not this flag, it is the storage location: wellness
# content lives in the ENGINE, and no HR/admin surface in the platform can reach it. See
# tests/test_wellness.py — "counts, never content" (docs/SECURITY.md).
SELF_REPORT_WELLNESS_ENABLED = (
    os.environ.get("CEREBROZEN_SELF_REPORT_WELLNESS", "").strip().lower()
    not in ("0", "false", "no")
)

if not (EMOTION_CAPTURE_ENABLED and PERSON_SCORING_ENABLED):
    import logging as _reg_log

    _reg_log.getLogger("cerebrozen.config").info(
        "config.regulated_workplace_mode",
        extra={
            "emotion_capture": EMOTION_CAPTURE_ENABLED,
            "person_scoring": PERSON_SCORING_ENABLED,
            "detail": "emotion inference and/or durable person-scoring are DISABLED for this tenant",
        },
    )
