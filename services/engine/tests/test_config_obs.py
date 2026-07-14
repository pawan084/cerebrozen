"""Config parsing + the observability plumbing around it.

The thing these modules have in common is that NONE of them may ever take the app
down, and all of them are driven by strings a human typed into SSM.

  * app/config.py is executed at IMPORT. A `ValueError` in it is not a bad config
    value — it is a process that will not start, on every box, at once. So the
    contract under test is: a malformed env var must degrade to the DEFAULT, never
    raise. (Two vars still don't; they are pinned as xfail below.)
  * app/tracing_otel.py is telemetry. Telemetry that crashes the service it watches
    is worse than no telemetry, so every failure mode — no endpoint, missing
    packages, exploding exporter — must land on "disabled", not on an exception.
  * app/selector.py is the kill-switch in front of the engine. If it is wrong, a
    rollout is not reversible.

Because config is read at import time, its tests reload the module under a patched
environment. `reload_config` owns that: it snapshots os.environ, and on teardown it
restores the environment AND reloads config from it, so a reload can never leak into
another test file.
"""
import importlib
import logging
import os
import sys

import pytest

from app import config


# ── reloading config safely ──────────────────────────────────────────────────


@pytest.fixture
def reload_config():
    """Reload app.config under a patched environment; restore both afterwards.

    Deliberately NOT built on monkeypatch: monkeypatch's env undo runs AFTER this
    fixture's teardown (finalisers are LIFO), so config would be reloaded while the
    patched env was still in place and the patched values would leak into every test
    that ran next. Snapshotting os.environ here and restoring it before the final
    reload is the only ordering that actually restores.

    Pass None as a value to UNSET a var. CEREBROZEN_STRICT_TENANT is cleared by default
    so an ambient/leaked value can't turn an unrelated reload into a RuntimeError.
    """
    saved = dict(os.environ)

    def _reload(**env):
        env.setdefault("CEREBROZEN_STRICT_TENANT", None)
        for key, value in env.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        return importlib.reload(config)

    yield _reload

    os.environ.clear()
    os.environ.update(saved)
    importlib.reload(config)


# ═════════════════════════════════════════════════════════════════════════════
# app/config.py — a typo in SSM must not be an outage
# ═════════════════════════════════════════════════════════════════════════════

# (env var, config attribute, value it must fall back to). Every numeric setting the
# module parses. A junk value in ANY of them is a plausible SSM typo.
NUMERIC_SETTINGS = [
    ("OPENAI_TIMEOUT", "OPENAI_TIMEOUT", 60.0),
    ("OPENAI_STREAM_DEADLINE_S", "OPENAI_STREAM_DEADLINE_S", 180.0),
    ("OPENAI_MAX_OUTPUT_TOKENS", "OPENAI_MAX_OUTPUT_TOKENS", 8192),
    ("CEREBROZEN_LLM_MAX_RETRIES", "LLM_MAX_RETRIES", 2),
    ("CEREBROZEN_LLM_BACKOFF_BASE_S", "LLM_BACKOFF_BASE_S", 0.5),
    ("CEREBROZEN_LLM_BACKOFF_MAX_S", "LLM_BACKOFF_MAX_S", 8.0),
    ("CEREBROZEN_BREAKER_FAILS", "BREAKER_FAIL_THRESHOLD", 5),
    ("CEREBROZEN_BREAKER_COOLDOWN_S", "BREAKER_COOLDOWN_S", 30.0),
    ("CEREBROZEN_TRACE_CHARS", "TRACE_CHARS", 2000),
    ("CEREBROZEN_LLM_LOG_CONTENT_CHARS", "CEREBROZEN_LLM_LOG_CONTENT_CHARS", 0),
    ("OTEL_TRACES_SAMPLER_ARG", "OTEL_SAMPLE_RATIO", 1.0),
    ("CEREBROZEN_LOCK_TTL_MS", "REDIS_LOCK_TTL_MS", 120000),
    ("CEREBROZEN_LOCK_WAIT_MS", "REDIS_LOCK_WAIT_MS", 30000),
    ("CEREBROZEN_PROFILE_TTL_S", "REDIS_PROFILE_TTL_S", 60),
    ("CEREBROZEN_VOICE_STABILITY", "VOICE_TTS_STABILITY", 0.5),
    ("CEREBROZEN_VOICE_SIMILARITY", "VOICE_TTS_SIMILARITY_BOOST", 0.75),
    ("CEREBROZEN_VOICE_STYLE", "VOICE_TTS_STYLE", 0.0),
    ("CEREBROZEN_VOICE_SPEED", "VOICE_TTS_SPEED", 0.86),
    ("CEREBROZEN_VOICE_TOKEN_TTL_S", "VOICE_TOKEN_TTL_S", 3600),
    ("CEREBROZEN_VOICE_MIN_ENDPOINT_S", "VOICE_MIN_ENDPOINTING_DELAY", 3.0),
    ("CEREBROZEN_VOICE_MAX_ENDPOINT_S", "VOICE_MAX_ENDPOINTING_DELAY", 6.0),
    ("CEREBROZEN_VOICE_STT_ENDPOINT_MS", "VOICE_STT_ENDPOINTING_MS", 3000),
    ("CEREBROZEN_VOICE_MIN_INTERRUPT_WORDS", "VOICE_MIN_INTERRUPTION_WORDS", 3),
    ("CEREBROZEN_VOICE_FALSE_INTERRUPT_S", "VOICE_FALSE_INTERRUPTION_TIMEOUT", 2.0),
    ("CEREBROZEN_MAX_USER_MESSAGE_CHARS", "MAX_USER_MESSAGE_CHARS", 5000),
    ("CEREBROZEN_CHECKIN_DUE_DAYS", "CHECKIN_DUE_DAYS", 7),
    ("CEREBROZEN_GRAPH_PERCENT", "GRAPH_PERCENT", 100),
    ("RAG_TOP_K", "RAG_TOP_K", 5),
    ("RAG_CACHE_TTL_S", "RAG_CACHE_TTL_S", 3600),
    # ── THE TWO THAT ARE NOT DEFENDED ────────────────────────────────────────
    # These two are the ONLY numeric settings in config.py whose int() is not wrapped
    # in a try/except. A single mistyped character in either SSM parameter raises
    # ValueError while `app.config` is being imported — which is not a bad setting, it
    # is a service that cannot boot, on every instance, simultaneously, with a
    # traceback and no log line naming the parameter. Every one of the 29 settings
    # above already defends against exactly this.
    #
    # These two were the outliers: bare int() calls, while the other 29 numeric settings in
    # config.py were all wrapped. config.py executes at IMPORT, so one mistyped character in
    # either SSM parameter raised ValueError during `import app.config` — every worker dead
    # on boot, simultaneously, with a traceback naming int() rather than the parameter that
    # was actually wrong. Both are defended now, and the AST guard below keeps it that way.
    ("MONGO_TIMEOUT_MS", "MONGO_TIMEOUT_MS", 4000),
    ("CEREBROZEN_PAST_CONVERSATION_MAX_CHARS", "PAST_CONVERSATION_MAX_CHARS", 40000),
]


@pytest.mark.parametrize(
    "env_var, attr, default",
    NUMERIC_SETTINGS,
    ids=[p.values[0] if isinstance(p, type(pytest.param())) else p[0] for p in NUMERIC_SETTINGS],
)
def test_a_malformed_number_falls_back_to_the_default_instead_of_killing_the_process(
    reload_config, env_var, attr, default
):
    """Someone pastes `60 ` with a stray unit, or a YAML anchor, or an empty-looking
    value, into one SSM parameter. config.py runs at import, so a ValueError there is a
    total outage — every worker dies on boot, and the traceback names `int()`, not the
    parameter. The service must instead come up on the documented default and keep
    serving coaching turns.
    """
    cfg = reload_config(**{env_var: "not-a-number"})

    assert getattr(cfg, attr) == default
    assert type(getattr(cfg, attr)) is type(default), "the fallback must keep the type"


def test_every_numeric_setting_in_config_is_defended(reload_config):
    """The guard on the guard: this asserts the SHAPE of the module, so a numeric env
    var added tomorrow without a try/except is caught by this test rather than by a
    failed deploy. It is the only test here that will notice a NEW undefended var.
    """
    import ast
    import pathlib

    source = pathlib.Path(config.__file__).read_text()
    tree = ast.parse(source)

    inside_try = {id(n) for t in ast.walk(tree) if isinstance(t, ast.Try) for n in ast.walk(t)}
    undefended = sorted(
        node.lineno
        for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id in ("int", "float")
        and id(node) not in inside_try
    )

    assert undefended == [], (
        "config.py gained (or fixed) an undefended numeric parse. A bare int()/float() "
        f"over os.environ crashes the app at import on a typo. Lines: {undefended}"
    )


@pytest.mark.parametrize(
    "env_var, attr, given, expected",
    [
        # Negative retries would make range(-1) silently skip the call entirely.
        ("CEREBROZEN_LLM_MAX_RETRIES", "LLM_MAX_RETRIES", "-1", 0),
        # A 0-failure breaker would trip before the first call and serve nothing but
        # the safe reply, forever.
        ("CEREBROZEN_BREAKER_FAILS", "BREAKER_FAIL_THRESHOLD", "0", 1),
        # A sub-second lock TTL expires mid-turn and lets a second turn race the
        # checkpoint (Art. 8.4).
        ("CEREBROZEN_LOCK_TTL_MS", "REDIS_LOCK_TTL_MS", "5", 1000),
        ("CEREBROZEN_LOCK_WAIT_MS", "REDIS_LOCK_WAIT_MS", "-5", 0),
        ("CEREBROZEN_PROFILE_TTL_S", "REDIS_PROFILE_TTL_S", "-1", 0),
        # A 10-second LiveKit token expires before the room is even joined.
        ("CEREBROZEN_VOICE_TOKEN_TTL_S", "VOICE_TOKEN_TTL_S", "10", 60),
        ("CEREBROZEN_VOICE_STT_ENDPOINT_MS", "VOICE_STT_ENDPOINTING_MS", "5", 25),
        ("CEREBROZEN_VOICE_MIN_INTERRUPT_WORDS", "VOICE_MIN_INTERRUPTION_WORDS", "-3", 0),
        # A 0-char cap would reject every message the user could possibly send.
        ("CEREBROZEN_MAX_USER_MESSAGE_CHARS", "MAX_USER_MESSAGE_CHARS", "0", 1),
        ("CEREBROZEN_CHECKIN_DUE_DAYS", "CHECKIN_DUE_DAYS", "-1", 0),
        # A top-k of 0 returns no documents and the coach silently loses its concepts.
        ("RAG_TOP_K", "RAG_TOP_K", "0", 1),
        ("RAG_CACHE_TTL_S", "RAG_CACHE_TTL_S", "-1", 0),
        # A rollout percentage is a percentage.
        ("CEREBROZEN_GRAPH_PERCENT", "GRAPH_PERCENT", "250", 100),
        ("CEREBROZEN_GRAPH_PERCENT", "GRAPH_PERCENT", "-5", 0),
        # Empty string is the SSM-parameter-exists-but-is-blank case.
        ("CEREBROZEN_GRAPH_PERCENT", "GRAPH_PERCENT", "", 100),
    ],
)
def test_an_out_of_range_number_is_clamped_to_something_survivable(
    reload_config, env_var, attr, given, expected
):
    """A value that PARSES but is nonsense is the more dangerous case — it doesn't
    crash, it just quietly makes the system behave in a way nobody asked for. Each
    clamp below is the difference between a degraded setting and a dead feature.
    """
    cfg = reload_config(**{env_var: given})

    assert getattr(cfg, attr) == expected


# ── model cascade ────────────────────────────────────────────────────────────


def test_the_model_cascade_survives_the_way_humans_actually_type_lists(reload_config):
    """The cascade is what the LLM layer falls back to when the primary model is down —
    it is load-bearing precisely when OpenAI is having a bad day. A trailing comma or a
    space after a comma is how a list gets typed into an SSM parameter by hand, and
    either one must not inject an empty-string model name that then 400s on every
    retry, turning a partial outage into a total one.
    """
    cfg = reload_config(CEREBROZEN_MODEL_CASCADE=" gpt-5-mini , , gpt-4o-mini ,")

    assert cfg.MODEL_CASCADE == ["gpt-5-mini", "gpt-4o-mini"]
    assert "" not in cfg.MODEL_CASCADE


def test_an_emptied_cascade_disables_fallback_rather_than_inventing_a_model(reload_config):
    """Blanking the parameter is the documented way to turn model fallback OFF. It must
    yield an empty list, not [""] — a cascade containing an empty model name would fail
    every fallback attempt with a confusing 400 from OpenAI."""
    cfg = reload_config(CEREBROZEN_MODEL_CASCADE="")

    assert cfg.MODEL_CASCADE == []


def test_the_default_cascade_is_the_documented_one(reload_config):
    cfg = reload_config(CEREBROZEN_MODEL_CASCADE=None)

    assert cfg.MODEL_CASCADE == ["gpt-5-mini", "gpt-5-nano"]


# ── CORS ─────────────────────────────────────────────────────────────────────


def test_cors_defaults_to_open_and_narrows_to_exactly_what_is_configured(reload_config):
    """CORS is the only thing standing between a browser on any origin and the turn
    endpoint. The default is "*" (a browser frontend has to work out of the box), so the
    thing that must be true is that CONFIGURING it actually restricts it — a parse that
    silently kept "*" in the list alongside the real origins would look locked down and
    be wide open.
    """
    assert reload_config(CEREBROZEN_CORS_ORIGINS=None).CORS_ALLOW_ORIGINS == ["*"]

    cfg = reload_config(
        CEREBROZEN_CORS_ORIGINS=" https://qa.example.com , https://app.example.com ,"
    )

    assert cfg.CORS_ALLOW_ORIGINS == ["https://qa.example.com", "https://app.example.com"]
    assert "*" not in cfg.CORS_ALLOW_ORIGINS, "configuring origins must REPLACE the wildcard"
    assert "" not in cfg.CORS_ALLOW_ORIGINS, "an empty origin would match nothing (or worse)"


# ── JWT secret ───────────────────────────────────────────────────────────────


def test_the_jwt_secret_is_base64_decoded_to_the_bytes_the_other_services_sign_with(
    reload_config,
):
    """The secret is shared with the sibling CereBroZen services, which store it base64.
    Decoding it wrong doesn't fail loudly — it produces a key that simply never
    validates a real token, and every authenticated user gets a 401."""
    import base64

    secret = b"\x00\x01super-secret-bytes\xff"
    cfg = reload_config(JWT_SECRET=base64.b64encode(secret).decode())

    assert cfg.JWT_SECRET == secret


def test_a_corrupt_jwt_secret_leaves_auth_unconfigured_instead_of_crashing_boot(
    reload_config,
):
    """A truncated / non-base64 secret must not raise at import. It resolves to b"",
    which is the "no secret configured" state the auth layer already handles (it refuses
    to serve deployed traffic without one) — a locked door, not a dead process."""
    assert reload_config(JWT_SECRET="!!! not base64 !!!").JWT_SECRET == b""
    assert reload_config(JWT_SECRET=None).JWT_SECRET == b""


# ── Mongo credential injection ───────────────────────────────────────────────


def test_mongo_credentials_are_injected_into_a_credential_less_url(reload_config):
    """On deployed envs SSM hands over a bare `mongodb://host:27017` plus separate
    username/password params. A bare URL CONNECTS and PINGS FINE and then fails every
    real operation with "requires authentication" — no checkpoints, no transcripts, no
    profile reads, and a health check that stays green. Hence the injection.

    The password is url-quoted: a `@` or `/` in it (both legal, both common in
    generated passwords) would otherwise split the URL and point the driver at a host
    that does not exist.
    """
    cfg = reload_config(
        MONGO_DB_URL="mongodb://host:27017",
        MONGO_DB_USERNAME="svc@cerebrozen",
        MONGO_DB_PASSWORD="p@ss/w:rd",
    )

    assert cfg.MONGO_DB_URL == "mongodb://svc%40cerebrozen:p%40ss%2Fw%3Ard@host:27017"


def test_a_url_that_already_carries_credentials_is_left_alone(reload_config):
    """Local dev embeds the credentials in the URL. Injecting a second set would produce
    `user:pw@user:pw@host` — an unparseable URL and a dead app. The guard has to stay
    idempotent across envs."""
    cfg = reload_config(
        MONGO_DB_URL="mongodb://alice:already@host:27017",
        MONGO_DB_USERNAME="bob",
        MONGO_DB_PASSWORD="other",
    )

    assert cfg.MONGO_DB_URL == "mongodb://alice:already@host:27017"


@pytest.mark.parametrize(
    "user, password",
    [("", ""), ("svc", ""), ("", "pw")],
)
def test_a_half_configured_credential_pair_is_not_injected(reload_config, user, password):
    """Only inject when BOTH halves are present. Building `mongodb://svc:@host` from a
    username with no password is a URL that authenticates as nobody and fails every op —
    strictly worse than the anonymous connection it replaced."""
    cfg = reload_config(
        MONGO_DB_URL="mongodb://host:27017",
        MONGO_DB_USERNAME=user,
        MONGO_DB_PASSWORD=password,
    )

    assert cfg.MONGO_DB_URL == "mongodb://host:27017"


# ── voice lab id list ────────────────────────────────────────────────────────


def test_the_voice_list_parses_both_id_and_id_colon_name_entries(reload_config):
    """This list drives the boss's voice-tuning console. Two spellings are accepted
    ("id:Name" and a bare id) because a bare id is what you get when someone pastes one
    out of the ElevenLabs dashboard."""
    cfg = reload_config(
        CEREBROZEN_VOICE_AVAILABLE_IDS=" v1:Rachel , v2 , , v3:Adam ",
        CEREBROZEN_VOICE_TTS_VOICE_ID=None,
    )

    assert cfg.VOICE_AVAILABLE_IDS == [
        {"id": "v1", "name": "Rachel"},
        {"id": "v2", "name": "v2"},
        {"id": "v3", "name": "Adam"},
    ]


def test_the_voice_actually_in_use_is_always_offered_in_the_console(reload_config):
    """If the configured voice is missing from the list, the console renders a picker
    that cannot select the voice the coach is CURRENTLY speaking with — so the first
    change is unrevertable. It gets prepended instead."""
    cfg = reload_config(
        CEREBROZEN_VOICE_AVAILABLE_IDS="v1:Rachel",
        CEREBROZEN_VOICE_TTS_VOICE_ID="v9",
    )

    assert cfg.VOICE_AVAILABLE_IDS == [
        {"id": "v9", "name": "Current"},
        {"id": "v1", "name": "Rachel"},
    ]


def test_the_configured_voice_is_not_listed_twice(reload_config):
    """...but only when it isn't already there, or the picker shows the same voice
    under two names and the boss picks the wrong one."""
    cfg = reload_config(
        CEREBROZEN_VOICE_AVAILABLE_IDS="v9:Nicole,v1:Rachel",
        CEREBROZEN_VOICE_TTS_VOICE_ID="v9",
    )

    assert cfg.VOICE_AVAILABLE_IDS == [
        {"id": "v9", "name": "Nicole"},
        {"id": "v1", "name": "Rachel"},
    ]


def test_no_voice_configured_leaves_the_console_empty_rather_than_inventing_an_entry(
    reload_config,
):
    cfg = reload_config(CEREBROZEN_VOICE_AVAILABLE_IDS=None, CEREBROZEN_VOICE_TTS_VOICE_ID=None)

    assert cfg.VOICE_AVAILABLE_IDS == []


# ── comma-separated sets / stage lists ───────────────────────────────────────


@pytest.mark.parametrize(
    "env_var, attr",
    [
        ("CEREBROZEN_FORCE_HANDOFF", "FORCE_HANDOFF_STAGES"),
        ("CEREBROZEN_JSON_OUTPUT_STAGES", "JSON_OUTPUT_STAGES"),
    ],
)
@pytest.mark.parametrize("everything", ["all", "ALL", "true", "1"])
def test_the_all_keyword_is_a_sentinel_not_a_stage_named_all(
    reload_config, env_var, attr, everything
):
    """"all" must become the {"__all__"} sentinel. Parsed as a literal stage name it
    would match no stage at all, so a QA engineer who sets FORCE_HANDOFF=all would see
    nothing happen and conclude the flag is broken."""
    cfg = reload_config(**{env_var: everything})

    assert getattr(cfg, attr) == {"__all__"}


@pytest.mark.parametrize(
    "env_var, attr",
    [
        ("CEREBROZEN_FORCE_HANDOFF", "FORCE_HANDOFF_STAGES"),
        ("CEREBROZEN_JSON_OUTPUT_STAGES", "JSON_OUTPUT_STAGES"),
        ("CEREBROZEN_TEST_USERS", "CEREBROZEN_TEST_USERS"),
        ("CEREBROZEN_GRAPH_ALLOWLIST", "GRAPH_ALLOWLIST"),
        ("CEREBROZEN_GRAPH_BLOCKLIST", "GRAPH_BLOCKLIST"),
    ],
)
def test_a_comma_list_is_empty_when_unset_and_never_holds_a_blank_entry(
    reload_config, env_var, attr
):
    """Every one of these is checked with `x in SET`. A stray blank entry from a
    trailing comma means the empty string is a member — and CEREBROZEN_TEST_USERS in
    particular gates whether a user's LLM calls are STUBBED. A blank member there stubs
    the coach for any request whose user_id failed to resolve.
    """
    assert getattr(reload_config(**{env_var: None}), attr) == set()

    cfg = reload_config(**{env_var: " a , , b ,"})

    assert getattr(cfg, attr) == {"a", "b"}
    assert "" not in getattr(cfg, attr)


# ── booleans ─────────────────────────────────────────────────────────────────


# (attr, env var, value when UNSET, a spelling that means ON, a spelling that means OFF).
# Two idioms are in play and they are NOT interchangeable:
#   == "true"  → only the literal "true" is on; ANY other word is off.
#   != "false" → only the literal "false" is off; ANY other word is on.
# Which idiom a flag uses decides what a typo does to it, so both are pinned per-flag.
BOOLEAN_FLAGS = [
    # == "true", defaulting off
    ("LLM_PROMPT_CACHE_ENABLED", "CEREBROZEN_LLM_PROMPT_CACHE", False, "TRUE", "yes"),
    ("VOICE_BARGE_IN", "CEREBROZEN_VOICE_BARGE_IN", False, "true", "1"),
    ("RAG_REINDEX", "RAG_REINDEX", False, "True", "no"),
    # == "true", defaulting on
    ("ENABLE_MULTIPATH", "CEREBROZEN_ENABLE_MULTIPATH", True, "TRUE", "false"),
    ("TRACE_IO", "CEREBROZEN_TRACE", True, "true", "off"),
    ("CEREBROZEN_LLM_LOG_CONTENT", "CEREBROZEN_LLM_LOG_CONTENT", True, "True", "false"),
    ("VOICE_UI_EVENTS_ENABLED", "CEREBROZEN_VOICE_UI_EVENTS", True, "true", "false"),
    ("VOICE_INLINE_ACTIONS_ENABLED", "CEREBROZEN_VOICE_INLINE_ACTIONS", True, "true", "false"),
    ("VOICE_TTS_SPEAKER_BOOST", "CEREBROZEN_VOICE_SPEAKER_BOOST", True, "true", "false"),
    ("ENABLE_PREWARM", "CEREBROZEN_ENABLE_PREWARM", True, "true", "false"),
    # != "false", defaulting on — a typo leaves these ON
    ("ENABLE_BUILDERS", "CEREBROZEN_ENABLE_BUILDERS", True, "typo", "FALSE"),
    ("GRAPH_ENABLED", "CEREBROZEN_GRAPH_ENABLED", True, "true", "FALSE"),
    ("RAG_INGEST_ON_STARTUP", "RAG_INGEST_ON_STARTUP", True, "true", "false"),
    # != "false", defaulting off — a typo turns the CIM-only stub ON
    ("STUB_CHALLENGE", "CEREBROZEN_STUB_CHALLENGE", False, "true", "FALSE"),
]


@pytest.mark.parametrize(
    "attr, env_var, when_unset, on, off",
    BOOLEAN_FLAGS,
    ids=[f[0] for f in BOOLEAN_FLAGS],
)
def test_a_boolean_flag_is_case_and_whitespace_insensitive(
    reload_config, attr, env_var, when_unset, on, off
):
    """These are typed by hand into a console. `CEREBROZEN_GRAPH_ENABLED=" FALSE"` — with
    the trailing space a console adds, and the caps a human uses under pressure — is
    somebody pulling the kill-switch during an incident. If the strip-and-lower is
    wrong, the switch does nothing and the operator has no idea why.
    """
    assert getattr(reload_config(**{env_var: None}), attr) is when_unset, "wrong default"
    assert getattr(reload_config(**{env_var: f"  {on}  "}), attr) is True
    assert getattr(reload_config(**{env_var: f"  {off}  "}), attr) is False


@pytest.mark.parametrize("truthy", ["1", "true", "TRUE", "yes", " Yes "])
def test_the_strict_tenant_flag_accepts_more_than_one_way_of_saying_yes(
    reload_config, truthy
):
    """This is the flag a second tenant sets to refuse to boot on the incumbent's
    infrastructure. Someone writing "yes" or "1" and getting a silent no-op would ship
    against the first client's Mongo and S3 believing they were protected — the exact
    failure the flag exists to prevent."""
    cfg = reload_config(
        CEREBROZEN_STRICT_TENANT=truthy,
        # ...satisfied, so the guard evaluates the flag but does not raise.
        MONGO_DB_BACKEND_DB="acme_backend",
        RASA_DB="acme_rasa",
        MONGO_CHECKPOINT_DB="acme_langgraph",
        RAG_S3_BUCKET="acme-rag-data",
    )

    assert cfg.STRICT_TENANT is True


def test_strict_tenant_is_off_unless_asked_for(reload_config):
    """The incumbent must be entirely unaffected — an accidental opt-in would crash the
    live service on boot."""
    assert reload_config(CEREBROZEN_STRICT_TENANT=None).STRICT_TENANT is False
    assert reload_config(CEREBROZEN_STRICT_TENANT="no").STRICT_TENANT is False


# ── OTEL enablement matrix ───────────────────────────────────────────────────


@pytest.mark.parametrize(
    "endpoint, traces, metrics, want_traces, want_metrics",
    [
        # No endpoint → nothing can export, whatever the exporters say.
        ("", "otlp", "otlp", False, False),
        # Endpoint but exporters off (the SSM default) → still off.
        ("http://collector:4317", "none", "none", False, False),
        ("http://collector:4317", "", "", False, False),
        # Either signal can be enabled on its own.
        ("http://collector:4317", "otlp", "none", True, False),
        ("http://collector:4317", "none", "otlp", False, True),
        ("http://collector:4317", "otlp", "otlp", True, True),
    ],
)
def test_otel_only_turns_on_when_an_endpoint_and_an_exporter_are_both_set(
    reload_config, endpoint, traces, metrics, want_traces, want_metrics
):
    """Exporters configured with no collector endpoint is the classic half-migration
    state. If it read as "enabled", every span would be queued for an endpoint that does
    not exist and the exporter would retry-loop in the background of a live service.
    Both halves are required, or OTEL stays off.
    """
    cfg = reload_config(
        OTEL_EXPORTER_OTLP_ENDPOINT=endpoint,
        OTEL_TRACES_EXPORTER=traces,
        OTEL_METRICS_EXPORTER=metrics,
    )

    assert cfg.OTEL_TRACES_ENABLED is want_traces
    assert cfg.OTEL_METRICS_ENABLED is want_metrics
    assert cfg.OTEL_ENABLED is (want_traces or want_metrics)


def test_the_deployment_env_tag_falls_back_from_otel_env_to_env(reload_config):
    """Every span is tagged with this. If dev and prod both tag "dev", the two
    environments' traces are indistinguishable in X-Ray — which is discovered during the
    first prod incident, at the worst possible moment."""
    assert reload_config(OTEL_ENV="qa", ENV="production").OTEL_ENV == "qa"
    assert reload_config(OTEL_ENV=None, ENV="production").OTEL_ENV == "production"
    assert reload_config(OTEL_ENV=None, ENV=None).OTEL_ENV == "dev"


# ── the tenant guard's WARNING path (the strict/raise path is in test_whitelabel) ──


def test_a_deployed_env_warns_by_name_for_every_incumbent_default_it_falls_back_to(
    reload_config, caplog
):
    """The silent-inheritance failure. A second tenant who forgets RAG_S3_BUCKET points
    retrieval at the FIRST client's bucket, which their AWS account cannot read — so
    every extraction returns null and the coach quietly loses its concepts and learning
    aids. No error. Nothing red. Nobody notices.

    On any deployed ENV the only thing standing between that and a support ticket is
    this warning, so it must (a) fire, (b) name the PARAMETER, so the fix is "add it to
    SSM" rather than a week of debugging an empty-handed coach.
    """
    with caplog.at_level(logging.WARNING, logger="cerebrozen.config"):
        cfg = reload_config(
            ENV="production",
            MONGO_DB_BACKEND_DB=None,
            RASA_DB=None,
            MONGO_CHECKPOINT_DB=None,
            RAG_S3_BUCKET=None,
            S3_BUCKET_NAME=None,
        )

    assert cfg.ENV == "production"
    warned = {
        r.param for r in caplog.records if r.getMessage() == "config.tenant_value_at_default"
    }
    assert warned == {"MONGO_DB_BACKEND_DB", "RASA_DB", "MONGO_CHECKPOINT_DB", "RAG_S3_BUCKET"}

    rag = next(r for r in caplog.records if getattr(r, "param", "") == "RAG_S3_BUCKET")
    assert rag.value == "dev-cerebrozen-rag-agent-data"
    assert "/production/bot/RAG_S3_BUCKET" in rag.detail, "the warning must name the SSM path"


def test_setting_a_tenant_value_stops_the_warning_for_that_one_only(reload_config, caplog):
    """The warning has to be actionable one parameter at a time, or a tenant part-way
    through the handover checklist sees the same wall of warnings and stops reading it."""
    with caplog.at_level(logging.WARNING, logger="cerebrozen.config"):
        cfg = reload_config(
            ENV="qa",
            RAG_S3_BUCKET="acme-rag-data",
            MONGO_CHECKPOINT_DB=None,
            MONGO_DB_BACKEND_DB=None,
            RASA_DB=None,
            S3_BUCKET_NAME=None,
        )

    assert "RAG_S3_BUCKET" not in cfg.tenant_values_at_incumbent_default()
    warned = {
        r.param for r in caplog.records if r.getMessage() == "config.tenant_value_at_default"
    }
    assert "RAG_S3_BUCKET" not in warned
    assert "MONGO_CHECKPOINT_DB" in warned, "the ones still at default must still warn"


def test_local_dev_is_not_nagged(reload_config, caplog):
    """ENV=local is the incumbent's dev box, where the defaults are CORRECT. Warning
    there trains everyone to ignore the warning that matters in prod."""
    with caplog.at_level(logging.WARNING, logger="cerebrozen.config"):
        reload_config(ENV="local", RAG_S3_BUCKET=None, S3_BUCKET_NAME=None)

    assert not [
        r for r in caplog.records if r.getMessage() == "config.tenant_value_at_default"
    ]


def test_the_rag_bucket_accepts_the_legacy_env_var_name(reload_config):
    """S3_BUCKET_NAME is what the older services call it. A tenant who sets that one and
    not RAG_S3_BUCKET must still get their own bucket, not the incumbent's."""
    cfg = reload_config(RAG_S3_BUCKET=None, S3_BUCKET_NAME="acme-rag-data")

    assert cfg.RAG_S3_BUCKET == "acme-rag-data"
    assert cfg.RAG_LANCEDB_URI == "s3://acme-rag-data/lancedb", (
        "the vector-store URI is derived from the bucket — it must follow the tenant too"
    )


# ═════════════════════════════════════════════════════════════════════════════
# app/tracing_otel.py — telemetry must never take the service down
#
# The X-Ray id generator (`opentelemetry-sdk-extension-aws`) and the gRPC exporter are
# not installed and are not in requirements.txt (see the note in test_api_layer.py), so
# the provider-setup half of configure_tracing() cannot run in this build at all. The
# tests below install the ABSENT PACKAGE — and only that — as a stub, so the real
# opentelemetry SDK (TracerProvider, Resource, BatchSpanProcessor, the samplers) runs
# for real against a real in-memory exporter. Nothing in app/ is mocked.
# ═════════════════════════════════════════════════════════════════════════════


class _RecordingSpanExporter:
    """A real span exporter that keeps spans in memory instead of on the network.

    This is THE boundary: the only thing stubbed on the trace path. Spans that reach
    `.spans` really did travel through the provider, the sampler and the batch
    processor, so asserting on them asserts the pipeline, not a mock.
    """

    instances: list = []

    def __init__(self, *_a, **_k):
        self.spans: list = []
        _RecordingSpanExporter.instances.append(self)

    def export(self, spans):
        from opentelemetry.sdk.trace.export import SpanExportResult

        self.spans.extend(spans)
        return SpanExportResult.SUCCESS

    def shutdown(self):
        return None

    def force_flush(self, timeout_millis: int = 30_000):
        return True


@pytest.fixture
def otel_sandbox(monkeypatch):
    """Make the absent OTEL packages importable, and keep OTEL's process-wide globals
    out of every other test.

    opentelemetry's tracer/meter providers are set-once globals: leaving one behind
    would silently change what every later test's spans do. All of them are restored.
    """
    from opentelemetry import propagate, trace
    from opentelemetry.metrics import _internal as metrics_internal
    from opentelemetry.sdk.trace.id_generator import RandomIdGenerator
    from opentelemetry.util._once import Once

    # Process-global provider slots — reset so set_tracer_provider() actually takes,
    # and restored by monkeypatch when the test ends.
    monkeypatch.setattr(trace, "_TRACER_PROVIDER", None, raising=False)
    monkeypatch.setattr(trace, "_TRACER_PROVIDER_SET_ONCE", Once(), raising=False)
    monkeypatch.setattr(metrics_internal, "_METER_PROVIDER", None, raising=False)
    monkeypatch.setattr(metrics_internal, "_METER_PROVIDER_SET_ONCE", Once(), raising=False)
    monkeypatch.setattr(propagate, "_HTTP_TEXT_FORMAT", propagate._HTTP_TEXT_FORMAT)

    # tracing_otel's own once-only latch.
    monkeypatch.setattr("app.tracing_otel._CONFIGURED", False)
    monkeypatch.setattr("app.tracing_otel._TRACER", None)

    # The exporter: the network boundary, and the only stub on the trace path.
    from opentelemetry.exporter.otlp.proto.http import trace_exporter as http_trace_exporter

    _RecordingSpanExporter.instances = []
    monkeypatch.setattr(http_trace_exporter, "OTLPSpanExporter", _RecordingSpanExporter)

    providers: list = []

    def _install_aws_extension(with_propagator: bool = True):
        """Install `opentelemetry-sdk-extension-aws` as a stub. It is genuinely not
        installed here; the id generator is real enough for TracerProvider to mint valid
        ids with it, which is what the real one is for."""
        import types

        class AwsXRayIdGenerator(RandomIdGenerator):
            pass

        aws_trace = types.ModuleType("opentelemetry.sdk.extension.aws.trace")
        aws_trace.AwsXRayIdGenerator = AwsXRayIdGenerator
        monkeypatch.setitem(sys.modules, "opentelemetry.sdk.extension", types.ModuleType("x"))
        monkeypatch.setitem(sys.modules, "opentelemetry.sdk.extension.aws", types.ModuleType("y"))
        monkeypatch.setitem(sys.modules, "opentelemetry.sdk.extension.aws.trace", aws_trace)

        if with_propagator:
            from opentelemetry.propagators.textmap import TextMapPropagator

            class AwsXRayPropagator(TextMapPropagator):
                def extract(self, carrier, context=None, getter=None):
                    return context

                def inject(self, carrier, context=None, setter=None):
                    carrier["X-Amzn-Trace-Id"] = "Root=1-stub"

                @property
                def fields(self):
                    return {"X-Amzn-Trace-Id"}

            prop_mod = types.ModuleType(
                "opentelemetry.sdk.extension.aws.trace.propagation.aws_xray_propagator"
            )
            prop_mod.AwsXRayPropagator = AwsXRayPropagator
            monkeypatch.setitem(
                sys.modules,
                "opentelemetry.sdk.extension.aws.trace.propagation",
                types.ModuleType("z"),
            )
            monkeypatch.setitem(
                sys.modules,
                "opentelemetry.sdk.extension.aws.trace.propagation.aws_xray_propagator",
                prop_mod,
            )

    yield _install_aws_extension

    # Shut down anything configure_tracing() built, so no exporter thread outlives the
    # test.
    from opentelemetry import trace as _trace

    provider = _trace._TRACER_PROVIDER
    if provider is not None and hasattr(provider, "shutdown"):
        provider.shutdown()
    for p in providers:
        p.shutdown()


@pytest.fixture
def otel_on(monkeypatch):
    """Point config at a collector over HTTP (the only exporter installed here)."""
    monkeypatch.setattr(config, "OTEL_ENABLED", True)
    monkeypatch.setattr(config, "OTEL_TRACES_ENABLED", True)
    monkeypatch.setattr(config, "OTEL_METRICS_ENABLED", False)
    monkeypatch.setattr(config, "OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318")
    monkeypatch.setattr(config, "OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf")
    monkeypatch.setattr(config, "OTEL_SERVICE_NAME", "cerebrozen-langgraph")
    monkeypatch.setattr(config, "OTEL_ENV", "qa")
    monkeypatch.setattr(config, "OTEL_SAMPLE_RATIO", 1.0)


def test_a_configured_tracer_actually_delivers_spans_to_the_collector(
    otel_sandbox, otel_on, caplog
):
    """The whole point of the module: with a collector configured, a span opened by a
    node has to come out the other end. This asserts the SPAN — through the real
    TracerProvider, the real sampler and the real BatchSpanProcessor — not that a
    function was called. If the wiring silently produced a no-op tracer (which is
    exactly what every failure path here degrades to) X-Ray would show nothing and the
    only symptom would be an empty service map.
    """
    from opentelemetry import trace as ot_trace

    from app import tracing_otel

    otel_sandbox()

    with caplog.at_level(logging.INFO, logger="cerebrozen.otel"):
        assert tracing_otel.configure_tracing() is True

    tracer = tracing_otel.get_tracer()
    with tracer.start_as_current_span("turn") as span:
        span.set_attribute("stage", "core_coaching_agent")

    provider = ot_trace.get_tracer_provider()
    provider.force_flush()  # deterministic: no sleeping on the batch interval

    exporter = _RecordingSpanExporter.instances[0]
    assert [s.name for s in exporter.spans] == ["turn"]
    assert exporter.spans[0].attributes["stage"] == "core_coaching_agent"

    # Tagged so a prod trace is distinguishable from a dev one in X-Ray.
    resource = exporter.spans[0].resource.attributes
    assert resource["service.name"] == "cerebrozen-langgraph"
    assert resource["deployment.environment"] == "qa"

    assert "otel.enabled" in caplog.text


def test_the_sample_ratio_is_honoured_so_a_10_percent_sampler_does_not_bill_for_100(
    otel_sandbox, otel_on, monkeypatch
):
    """OTEL_TRACES_SAMPLER_ARG=0.1 is what dev's SSM actually sets. If the ratio were
    dropped on the floor the app would export every span of every turn — an X-Ray bill
    and a collector queue sized for 10x the intended traffic."""
    from opentelemetry import trace as ot_trace

    from app import tracing_otel

    monkeypatch.setattr(config, "OTEL_SAMPLE_RATIO", 0.0)  # sample nothing
    otel_sandbox()

    assert tracing_otel.configure_tracing() is True

    tracer = tracing_otel.get_tracer()
    for _ in range(20):
        with tracer.start_as_current_span("turn"):
            pass
    ot_trace.get_tracer_provider().force_flush()

    exporter = _RecordingSpanExporter.instances[0]
    assert exporter.spans == [], "ratio 0.0 must drop every span; the sampler is ignored"


def test_a_missing_xray_propagator_costs_the_header_not_the_traces(
    otel_sandbox, otel_on, caplog
):
    """The X-Ray propagator is the optional half — it links our spans to the sibling
    Spring services'. If it can't be loaded, the sensible degradation is losing the
    cross-service link, NOT losing our own traces. Anything else trades a nice-to-have
    for the whole signal."""
    from opentelemetry import trace as ot_trace

    from app import tracing_otel

    otel_sandbox(with_propagator=False)  # id generator present, propagator absent

    with caplog.at_level(logging.WARNING, logger="cerebrozen.otel"):
        assert tracing_otel.configure_tracing() is True

    assert "otel.propagator_skipped" in caplog.text

    tracer = tracing_otel.get_tracer()
    with tracer.start_as_current_span("turn"):
        pass
    ot_trace.get_tracer_provider().force_flush()

    assert [s.name for s in _RecordingSpanExporter.instances[0].spans] == ["turn"], (
        "traces must still export without the propagator"
    )


def test_an_exporter_that_explodes_on_construction_disables_otel_instead_of_boot(
    otel_sandbox, otel_on, caplog
):
    """A collector URL with a typo, a TLS failure, a version mismatch — the OTLP
    exporter can refuse to construct for a dozen reasons that are all somebody else's
    problem. None of them may stop this service from starting, and afterwards the tracer
    must be the SAFE no-op one, because every node opens a span on every turn.
    """
    from opentelemetry.exporter.otlp.proto.http import trace_exporter

    from app import tracing_otel

    otel_sandbox()

    class _ExplodingExporter:
        def __init__(self, *_a, **_k):
            raise RuntimeError("collector unreachable: getaddrinfo failed")

    trace_exporter.OTLPSpanExporter = _ExplodingExporter  # restored by otel_sandbox

    with caplog.at_level(logging.WARNING, logger="cerebrozen.otel"):
        assert tracing_otel.configure_tracing() is False

    assert "otel.setup_failed" in caplog.text
    assert tracing_otel._TRACER is None

    # And the tracer handed to the graph is still usable — a span must not raise.
    with tracing_otel.get_tracer().start_as_current_span("turn") as span:
        span.set_attribute("stage", "core")


def test_metrics_ride_the_same_configuration_without_a_second_endpoint(
    otel_sandbox, otel_on, monkeypatch, caplog
):
    """Traces and metrics share OTEL_EXPORTER_OTLP_ENDPOINT. Turning metrics on must not
    require a second endpoint (there isn't one) — and, crucially, must not break the
    trace half that was already working."""
    from opentelemetry import metrics as ot_metrics
    from opentelemetry.exporter.otlp.proto.http import metric_exporter

    from app import tracing_otel

    class _StubMetricExporter:
        _preferred_temporality: dict = {}
        _preferred_aggregation: dict = {}

        def __init__(self, *_a, **_k):
            pass

        def export(self, *_a, **_k):
            return None

        def shutdown(self, *_a, **_k):
            return None

        def force_flush(self, *_a, **_k):
            return True

    monkeypatch.setattr(metric_exporter, "OTLPMetricExporter", _StubMetricExporter)
    monkeypatch.setattr(config, "OTEL_METRICS_ENABLED", True)
    otel_sandbox()

    with caplog.at_level(logging.INFO, logger="cerebrozen.otel"):
        assert tracing_otel.configure_tracing() is True

    from opentelemetry.sdk.metrics import MeterProvider

    provider = ot_metrics.get_meter_provider()
    assert isinstance(provider, MeterProvider), "the meter provider must actually be installed"
    assert "otel.metrics_enabled" in caplog.text
    assert len(_RecordingSpanExporter.instances) == 1, "traces must still export too"
    provider.shutdown()


def test_metrics_only_does_not_start_a_span_exporter_for_traces_nobody_asked_for(
    otel_sandbox, otel_on, monkeypatch
):
    """OTEL_TRACES_EXPORTER=none + OTEL_METRICS_EXPORTER=otlp is a real, supported
    combination — you want the Prometheus dashboards without the X-Ray trace volume (and
    the bill). If the span processor were attached anyway, the service would open a
    background exporter connection and ship every span to a collector that was
    explicitly told not to want them.
    """
    from opentelemetry.exporter.otlp.proto.http import metric_exporter

    from app import tracing_otel

    class _StubMetricExporter:
        _preferred_temporality: dict = {}
        _preferred_aggregation: dict = {}

        def __init__(self, *_a, **_k):
            pass

        def export(self, *_a, **_k):
            return None

        def shutdown(self, *_a, **_k):
            return None

        def force_flush(self, *_a, **_k):
            return True

    monkeypatch.setattr(metric_exporter, "OTLPMetricExporter", _StubMetricExporter)
    monkeypatch.setattr(config, "OTEL_TRACES_ENABLED", False)
    monkeypatch.setattr(config, "OTEL_METRICS_ENABLED", True)
    otel_sandbox()

    assert tracing_otel.configure_tracing() is True

    assert _RecordingSpanExporter.instances == [], "no span exporter may be constructed"

    from opentelemetry import metrics as ot_metrics
    from opentelemetry.sdk.metrics import MeterProvider

    provider = ot_metrics.get_meter_provider()
    assert isinstance(provider, MeterProvider)
    provider.shutdown()


def test_configure_tracing_is_idempotent_and_does_not_stack_exporters(
    otel_sandbox, otel_on
):
    """It's called at startup, but startup can run twice (a reloader, a worker fork, a
    test importing main). A second provider would double every span and start a second
    exporter thread."""
    from app import tracing_otel

    otel_sandbox()

    assert tracing_otel.configure_tracing() is True
    assert tracing_otel.configure_tracing() is True
    assert len(_RecordingSpanExporter.instances) == 1, "the second call must be a no-op"


@pytest.mark.parametrize(
    "which, cls_fn",
    [("span", "_span_exporter_cls"), ("metric", "_metric_exporter_cls")],
)
def test_BUG_the_default_grpc_protocol_cannot_resolve_an_exporter_in_this_build(
    monkeypatch, which, cls_fn
):
    """PINNED. config.OTEL_EXPORTER_OTLP_PROTOCOL defaults to "grpc" (port 4317), but
    `opentelemetry-exporter-otlp-proto-grpc` is not installed and is not in
    requirements.txt — only the HTTP one is. So on the DEFAULT protocol the exporter
    import raises, configure_tracing() lands in `otel.deps_missing`, and OTEL is off no
    matter how the collector is configured.

    Not a crash (the module catches it, which is the important part), but it does mean
    the documented default is unusable. Install the grpc exporter, or default the
    protocol to http/protobuf; then this test should assert the class resolves.
    """
    from app import tracing_otel

    monkeypatch.setattr(config, "OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
    assert tracing_otel._is_http_protocol() is False

    with pytest.raises(ModuleNotFoundError, match="grpc"):
        getattr(tracing_otel, cls_fn)()


# ═════════════════════════════════════════════════════════════════════════════
# app/selector.py — the kill-switch
# ═════════════════════════════════════════════════════════════════════════════


@pytest.fixture
def selector_config(monkeypatch):
    """Selector reads config at CALL time, so no reload is needed — just set the flags."""

    def _set(enabled=True, percent=100, allow=(), block=()):
        monkeypatch.setattr(config, "GRAPH_ENABLED", enabled)
        monkeypatch.setattr(config, "GRAPH_PERCENT", percent)
        monkeypatch.setattr(config, "GRAPH_ALLOWLIST", set(allow))
        monkeypatch.setattr(config, "GRAPH_BLOCKLIST", set(block))

    return _set


def test_a_per_request_override_beats_every_other_rule(selector_config):
    """The override is the debugging escape hatch — it has to win even against the
    blocklist, or you cannot reproduce a blocked user's session to diagnose it."""
    from app.selector import route_to_graph

    selector_config(enabled=False, percent=0, block={"alice"})

    assert route_to_graph("alice", {"use_graph": True}) == (True, "request_override")

    selector_config(enabled=True, percent=100, allow={"alice"})

    assert route_to_graph("alice", {"use_graph": False}) == (False, "request_override")


def test_the_blocklist_beats_the_allowlist(selector_config):
    """A user on both lists is a mistake being made under pressure. Deny has to win:
    the blocklist is what you reach for when a specific user is melting something."""
    from app.selector import route_to_graph

    selector_config(enabled=True, percent=100, allow={"alice"}, block={"alice"})

    assert route_to_graph("alice") == (False, "blocklist")


def test_the_allowlist_beats_a_zero_percent_rollout(selector_config):
    """This is how you dogfood before the ramp: the flag is off for the world, on for
    you. If the percentage could override it, there would be no way to test in prod."""
    from app.selector import route_to_graph

    selector_config(enabled=True, percent=0, allow={"alice"})

    assert route_to_graph("alice") == (True, "allowlist")


def test_a_full_rollout_serves_everyone(selector_config):
    from app.selector import route_to_graph

    selector_config(enabled=True, percent=100)

    assert route_to_graph("anyone-at-all") == (True, "enabled")


def test_a_partial_rollout_splits_users_and_keeps_each_one_on_the_same_side(
    selector_config,
):
    """A user who lands on the graph for one turn and off it for the next would get half
    a conversation — a fresh session mid-thread. The bucket is an md5 of the user_id
    precisely so it is stable, and that stability is the property worth testing.

    (bucket('bob') == 32, bucket('carol') == 56 — either side of a 50% ramp.)
    """
    from app.selector import _bucket, route_to_graph

    selector_config(enabled=True, percent=50)

    assert route_to_graph("bob") == (True, "enabled")
    assert route_to_graph("carol") == (False, "percent_excluded")

    assert _bucket("bob") == _bucket("bob") == 32, "a user's bucket must never move"
    assert 0 <= _bucket("") <= 99, "an anonymous request must still bucket, not crash"


def test_a_globally_disabled_graph_can_still_ramp_a_cohort_back_in(selector_config):
    """Recovery after a kill: you do not go from 0% to everyone. With the global flag
    off, the percentage is the ramp back up — so it must still be honoured."""
    from app.selector import route_to_graph

    selector_config(enabled=False, percent=50)

    assert route_to_graph("bob") == (True, "percent_rampup")
    assert route_to_graph("carol") == (False, "disabled")


def test_the_kill_switch_at_zero_percent_turns_everyone_off(selector_config):
    """The actual kill: flag off, percent 0, nobody gets through — including the user
    whose bucket is 0, which is the off-by-one that would leave a cohort live during an
    incident."""
    from app.selector import route_to_graph

    selector_config(enabled=False, percent=0)

    for user in ("alice", "bob", "carol", ""):  # bucket('alice') == 0
        assert route_to_graph(user) == (False, "disabled")


# ═════════════════════════════════════════════════════════════════════════════
# app/trace.py — PII-bearing I/O logging, off the hot path
# ═════════════════════════════════════════════════════════════════════════════


def test_tracing_off_writes_nothing_at_all(monkeypatch, caplog):
    """CEREBROZEN_TRACE=false is how a client says "do not put our users' transcripts in
    your logs". If a single record still escapes, that is a privacy incident, not a
    verbosity one — so the switch must gate the WRITE, not just the formatting."""
    from app import trace

    monkeypatch.setattr(config, "TRACE_IO", False)

    with caplog.at_level(logging.INFO, logger="cerebrozen.trace"):
        trace.io("agent.input", system_prompt="you are a coach", user_message="my boss hates me")

    assert trace.enabled() is False
    assert caplog.records == []


def test_tracing_on_records_the_fields_it_was_given(monkeypatch, caplog):
    from app import trace

    monkeypatch.setattr(config, "TRACE_IO", True)
    monkeypatch.setattr(config, "TRACE_CHARS", 2000)

    with caplog.at_level(logging.INFO, logger="cerebrozen.trace"):
        trace.io("agent.output", stage="core", reply="ok")

    assert trace.enabled() is True
    record = caplog.records[0]
    assert record.getMessage() == "agent.output"
    assert (record.stage, record.reply) == ("core", "ok")


def test_a_27k_token_prompt_is_clipped_so_one_turn_cannot_flood_the_log(
    monkeypatch, caplog
):
    """The resolved system prompts are enormous. Logged whole, a single turn writes
    hundreds of KB to CloudWatch — the ingest bill and the unreadable log line are both
    real. The clip must also SAY how much it dropped, or the log silently lies about
    what was sent to the model."""
    from app import trace

    monkeypatch.setattr(config, "TRACE_IO", True)
    monkeypatch.setattr(config, "TRACE_CHARS", 50)

    with caplog.at_level(logging.INFO, logger="cerebrozen.trace"):
        trace.io("agent.input", prompt="x" * 500, history=[{"role": "user", "content": "y" * 500}])

    record = caplog.records[0]
    assert record.prompt.startswith("x" * 50)
    assert record.prompt.endswith("[+450 chars]")
    # Structured payloads are serialized first, then clipped — otherwise a 500-message
    # history sails straight past a limit that only ever looked at strings.
    assert isinstance(record.history, str)
    assert len(record.history) < 100 and "chars]" in record.history


def test_short_values_and_non_strings_are_passed_through_untouched(monkeypatch, caplog):
    """Clipping must not mangle the fields the log is actually queried on — a token
    count that arrived as the string "1234…[+2 chars]" is a broken CloudWatch dashboard."""
    from app import trace

    monkeypatch.setattr(config, "TRACE_IO", True)
    monkeypatch.setattr(config, "TRACE_CHARS", 2000)

    with caplog.at_level(logging.INFO, logger="cerebrozen.trace"):
        trace.io("openai.response", tokens=1234, cost=0.0042, reply="short", history=[{"a": 1}])

    record = caplog.records[0]
    assert record.tokens == 1234
    assert record.cost == 0.0042
    assert record.reply == "short"
    assert record.history == [{"a": 1}], "a small payload stays a payload, not a string"


def test_trace_chars_zero_means_log_the_whole_thing(monkeypatch, caplog):
    """The documented escape hatch for debugging a prompt end-to-end: 0 = no truncation.
    If 0 were treated as "clip to 0 chars" it would erase every field it was set to
    reveal."""
    from app import trace

    monkeypatch.setattr(config, "TRACE_IO", True)
    monkeypatch.setattr(config, "TRACE_CHARS", 0)

    with caplog.at_level(logging.INFO, logger="cerebrozen.trace"):
        trace.io("agent.input", prompt="x" * 5000)

    assert caplog.records[0].prompt == "x" * 5000


# ═════════════════════════════════════════════════════════════════════════════
# app/session.py — who the turn belongs to
# ═════════════════════════════════════════════════════════════════════════════


def test_a_fresh_session_id_is_opaque_and_unique(reload_config):
    """A session id that embedded the user_id would leak it wherever the id travels
    (URLs, logs, the FE). And a colliding id means two users sharing a checkpointer
    thread — one user reading another's conversation."""
    from app.session import mint_session_id

    ids = {mint_session_id() for _ in range(1000)}

    assert len(ids) == 1000
    assert all(len(i) == 32 and i.isalnum() for i in ids)


@pytest.mark.parametrize(
    "claims, expected",
    [
        ({"user": {"username": "6510abc"}}, "6510abc"),
        ({"user": {"userName": "6510abc"}}, "6510abc"),          # camelCase sibling service
        ({"username": "6510abc"}, "6510abc"),                    # flat spelling
        ({"userName": "6510abc"}, "6510abc"),
        ({"user": {"userName": "flat-wins"}, "username": "x"}, "flat-wins"),
        ({"user": {"email": "a@b.c"}}, ""),                      # user object, no username
        ({"user": "not-a-dict"}, ""),
        ({}, ""),
        (None, ""),
        ("a string, not claims", ""),
        ({"user": {"username": 6510}}, "6510"),                  # non-str id is stringified
    ],
)
def test_the_user_id_is_read_from_whichever_shape_the_token_arrives_in(claims, expected):
    """These claim shapes come from different services signing with the same secret.
    Reading the id wrong doesn't 401 — it resolves to "" or to the wrong string, and the
    turn is then checkpointed against the WRONG USER's thread. A malformed token must
    resolve to nobody, never to somebody else, and never raise.
    """
    from app.session import user_id_from_claims

    assert user_id_from_claims(claims) == expected


def test_an_explicit_payload_user_id_wins_over_the_token(reload_config):
    """Service-to-service callers authenticate as themselves and pass the end user's id
    in the payload. If the token's own username won, every such turn would land on the
    service account's thread and the users' sessions would be merged into one."""
    from app.session import resolve_user_id

    claims = {"user": {"username": "from-token"}}

    assert resolve_user_id("  from-payload  ", claims) == "from-payload"
    assert resolve_user_id("", claims) == "from-token"
    assert resolve_user_id("   ", claims) == "from-token", "whitespace is not a user id"
    assert resolve_user_id(None, claims) == "from-token"
    assert resolve_user_id(None, None) == ""


# ═════════════════════════════════════════════════════════════════════════════
# app/env_loader.py — a local file must never outrank the deployed environment
# ═════════════════════════════════════════════════════════════════════════════


def test_a_local_env_file_never_overrides_a_var_the_environment_already_set(tmp_path):
    """This runs at import of app.main INSIDE a process whose env is already set (an ECS
    task definition, CI secrets). If a checked-out `.env` could win, a stray file on a
    build box would silently change which database a deployed service connects to."""
    from app.env_loader import load_dotenv_file, load_env_file

    saved = dict(os.environ)
    try:
        os.environ["CEREBROZEN_ALREADY_SET"] = "from-the-real-environment"
        os.environ["CEREBROZEN_SET_BUT_EMPTY"] = ""
        os.environ.pop("CEREBROZEN_UNSET", None)

        dotenv = tmp_path / ".env"
        dotenv.write_text(
            "# a comment\n"
            "\n"
            "CEREBROZEN_ALREADY_SET=from-the-file\n"
            "CEREBROZEN_SET_BUT_EMPTY=from-the-file\n"
            "export CEREBROZEN_UNSET=from-the-file\n"
            "not a valid line\n",
            encoding="utf-8",
        )

        assert load_dotenv_file(dotenv) == 2
        assert os.environ["CEREBROZEN_ALREADY_SET"] == "from-the-real-environment"
        # An inherited-but-EMPTY var (uvicorn passes those down) is not a real value, so
        # the file may fill it — otherwise a blank OPENAI_API_KEY in the environment
        # would permanently mask the one in .env.
        assert os.environ["CEREBROZEN_SET_BUT_EMPTY"] == "from-the-file"
        assert os.environ["CEREBROZEN_UNSET"] == "from-the-file"

        ps1 = tmp_path / "env-dev.ps1"
        ps1.write_text('$env:CEREBROZEN_ALREADY_SET = "from-the-ps1"\n', encoding="utf-8")

        assert load_env_file(ps1) == 0, "the PowerShell path must respect the same rule"
        assert os.environ["CEREBROZEN_ALREADY_SET"] == "from-the-real-environment"

        assert load_dotenv_file(tmp_path / "nope.env") == 0
        assert load_env_file(tmp_path / "nope.ps1") == 0
    finally:
        os.environ.clear()
        os.environ.update(saved)


def test_load_local_env_reports_what_it_loaded_and_what_it_skipped(caplog):
    """The log line is the only way to answer "why does this box have a different
    OPENAI_API_KEY than I set?". `env.loaded` naming the file it came from, or
    `env.skipped` when nothing was taken, is the difference between a five-second answer
    and an afternoon.

    Uses the REAL repo paths, because load_local_env() derives them from its own
    __file__ — the paths are the thing under test.
    """
    from pathlib import Path

    from app import env_loader

    repo_root = Path(env_loader.__file__).resolve().parent.parent
    ps1 = repo_root / "env-dev.ps1"
    dotenv = repo_root / ".env"

    saved = dict(os.environ)
    created_ps1 = False
    try:
        # ── nothing to load: every name in the real .env is already in the environment,
        #    and there is no env-dev.ps1 → the module must say so, not stay silent.
        if dotenv.exists():
            for line in dotenv.read_text(encoding="utf-8-sig", errors="replace").splitlines():
                match = env_loader._DOTENV_LINE.match(line)
                if match and not line.strip().startswith("#"):
                    os.environ[match.group(1)] = "already-set-in-the-environment"

        if not ps1.exists():
            before = dict(os.environ)
            with caplog.at_level(logging.INFO, logger="cerebrozen.env"):
                env_loader.load_local_env()

            assert dict(os.environ) == before, "a local file overrode the running environment"
            assert "env.skipped" in caplog.text
            assert "env.loaded" not in caplog.text

            # ── something to load: an env-dev.ps1 with a var nobody has set.
            caplog.clear()
            os.environ.pop("CEREBROZEN_PS1_ONLY_VAR", None)
            ps1.write_text('$env:CEREBROZEN_PS1_ONLY_VAR = "from-the-ps1"\n', encoding="utf-8")
            created_ps1 = True

            with caplog.at_level(logging.INFO, logger="cerebrozen.env"):
                env_loader.load_local_env()

            assert os.environ["CEREBROZEN_PS1_ONLY_VAR"] == "from-the-ps1"
            loaded = next(r for r in caplog.records if r.getMessage() == "env.loaded")
            assert loaded.path == str(ps1), "the log must name the file it loaded from"
            assert loaded.vars_set >= 1
            assert "env.skipped" not in caplog.text
    finally:
        if created_ps1:
            ps1.unlink(missing_ok=True)
        os.environ.clear()
        os.environ.update(saved)
