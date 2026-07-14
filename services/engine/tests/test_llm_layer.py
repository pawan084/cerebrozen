"""The LLM layer: resilience, cost, prompt sourcing, providers, and the two
utility generators (greeting + title).

Everything here is on the request path or the money path, and every failure mode
in it is SILENT by design — a retried call, a fallen-back model, a bundled
workbook served in place of S3, a static greeting instead of a generated one. The
app keeps answering either way, which is exactly why these need tests rather than
log lines: the only way to notice the guarantee has broken is to assert it.

Nothing here touches the network. The ONLY things faked are the true external
boundaries — the Ollama HTTP transport (a real httpx client over a MockTransport,
so httpx's own streaming/raise_for_status runs for real), boto3, and the LLM
provider handed to the generators. Everything else is our code, running.
"""
import hashlib
import json
import logging
import threading
import types

import httpx
import pytest

from app import config
from app.llm import pricing, prompt_store, resilience
from app.llm import greeting_generator as gg
from app.llm import title_generator as tg
from app.llm.providers import ollama
from app.llm.responses_client import LLMResponse


# ═══════════════════════════════════════════════════════════════════════════════
# resilience.py — retry classification
# ═══════════════════════════════════════════════════════════════════════════════


def _exc(name: str, **attrs) -> BaseException:
    """An exception whose CLASS NAME is `name` — the only thing is_retryable sees.

    Deliberately not the real openai exception tree: is_retryable matches on the
    class name precisely so it doesn't break when the SDK reshuffles its
    exceptions, and this test has to exercise that contract, not bypass it.
    """
    return type(name, (Exception,), attrs)()


@pytest.mark.parametrize(
    "name",
    [
        "APITimeoutError",
        "APIConnectionError",
        "APIConnectionTimeoutError",
        "InternalServerError",
        "RateLimitError",
        "Timeout",
        "TimeoutError",
        "ConnectionError",
        "ReadTimeout",
        "ServiceUnavailableError",
    ],
)
def test_transient_failures_are_retried(name):
    """These are the errors a retry can actually fix. If one drops out of the set
    the turn dies on a blip that a 0.5s backoff would have absorbed."""
    assert resilience.is_retryable(_exc(name)) is True


@pytest.mark.parametrize(
    "name",
    [
        "AuthenticationError",
        "PermissionDeniedError",
        "BadRequestError",
        "NotFoundError",
        "UnprocessableEntityError",
        "ConflictError",
    ],
)
def test_permanent_failures_are_never_retried(name):
    """A bad key or a malformed request will fail identically three times — retrying
    just triples the latency of an inevitable error (and, on 429-adjacent auth
    failures, burns quota). These must stay hard-no."""
    assert resilience.is_retryable(_exc(name)) is False


def test_non_retryable_class_wins_over_a_5xx_status_code():
    """An exception can carry both a known-permanent class name and a server-ish
    status. The classification must not fall through to the status check and start
    retrying auth failures against a rate-limited account."""
    assert resilience.is_retryable(_exc("AuthenticationError", status_code=503)) is False


def test_unknown_exceptions_are_classified_by_http_status():
    """Not every SDK error is in the name table. 429 and 5xx are retryable, other
    4xx are not, and something with no status at all is not — retrying an unknown
    error class blindly is how a bug becomes a 3x cost multiplier."""
    assert resilience.is_retryable(_exc("SomeNewSDKError", status_code=429)) is True
    assert resilience.is_retryable(_exc("SomeNewSDKError", status_code=500)) is True
    assert resilience.is_retryable(_exc("SomeNewSDKError", status_code=599)) is True
    assert resilience.is_retryable(_exc("SomeNewSDKError", status_code=400)) is False
    assert resilience.is_retryable(_exc("SomeNewSDKError", status_code=404)) is False
    # httpx-style attribute spelling, and no status at all.
    assert resilience.is_retryable(_exc("SomeNewSDKError", status=502)) is True
    assert resilience.is_retryable(_exc("SomeNewSDKError")) is False
    # A non-int status (some SDKs stringify it) must not be trusted as a number.
    assert resilience.is_retryable(_exc("SomeNewSDKError", status_code="500")) is False


# ═══════════════════════════════════════════════════════════════════════════════
# resilience.py — backoff
# ═══════════════════════════════════════════════════════════════════════════════


def test_backoff_is_exponential_and_capped(monkeypatch):
    """The cap is the whole point: without it, attempt 6 sleeps 32s and the user's
    turn blows past the UI timeout while we politely wait. Pin the ceiling schedule
    by removing the jitter (max jitter == the ceiling)."""
    monkeypatch.setattr(resilience, "random", types.SimpleNamespace(uniform=lambda lo, hi: hi))

    assert resilience.backoff_delays(5, base=1.0, cap=4.0) == [1.0, 2.0, 4.0, 4.0, 4.0]


def test_backoff_is_jittered_and_bounded_with_real_randomness():
    """Full jitter (uniform in [0, ceiling]) is what stops every in-flight turn from
    retrying on the same tick and re-DDoSing a recovering provider. A fixed backoff
    would pass a bounds check but fail this one."""
    samples = [resilience.backoff_delays(3, base=1.0, cap=8.0) for _ in range(200)]

    for delays in samples:
        assert len(delays) == 3
        for i, d in enumerate(delays):
            ceiling = min(8.0, 1.0 * (2 ** i))
            assert 0.0 <= d <= ceiling, "a delay must never exceed its capped ceiling"

    first_delays = {s[0] for s in samples}
    assert len(first_delays) > 1, "no jitter — every retry would fire on the same tick"


def test_backoff_delay_count_matches_the_retry_count():
    """One delay per retry: a mismatch either sleeps before the first attempt
    (needless latency on the happy path) or skips a sleep (hammering)."""
    assert resilience.backoff_delays(0) == []
    assert resilience.backoff_delays(-3) == [], "a negative retry count must not explode"
    assert len(resilience.backoff_delays(4)) == 4


def test_backoff_defaults_come_from_config(monkeypatch):
    """Ops tune the backoff through env → config. If the defaults stop being read,
    an incident-time change to CEREBROZEN_LLM_BACKOFF_MAX_S silently does nothing."""
    monkeypatch.setattr(config, "LLM_BACKOFF_BASE_S", 3.0)
    monkeypatch.setattr(config, "LLM_BACKOFF_MAX_S", 3.0)

    assert all(d <= 3.0 for d in resilience.backoff_delays(6))
    monkeypatch.setattr(resilience, "random", types.SimpleNamespace(uniform=lambda lo, hi: hi))
    assert resilience.backoff_delays(2) == [3.0, 3.0]


# ═══════════════════════════════════════════════════════════════════════════════
# resilience.py — circuit breaker
# ═══════════════════════════════════════════════════════════════════════════════


class _Clock:
    """A hand-cranked monotonic clock — the breaker's cooldown is time-based, and a
    test that sleeps for it is a test nobody runs."""

    def __init__(self) -> None:
        self.t = 1000.0

    def monotonic(self) -> float:
        return self.t

    def advance(self, seconds: float) -> None:
        self.t += seconds


@pytest.fixture
def clock(monkeypatch):
    c = _Clock()
    monkeypatch.setattr(resilience, "time", types.SimpleNamespace(monotonic=c.monotonic))
    return c


def test_breaker_opens_only_after_the_threshold(clock):
    """Opening too early takes a healthy provider offline for a blip; opening too
    late means we keep hammering a provider that is down (and keep paying the
    per-turn timeout in user-visible latency)."""
    cb = resilience.CircuitBreaker(fail_threshold=3, cooldown_s=30)
    assert cb.state == "closed" and cb.allow() is True

    cb.record_failure()
    cb.record_failure()
    assert cb.state == "closed", "2 of 3 failures must not open the breaker"
    assert cb.allow() is True

    cb.record_failure()
    assert cb.state == "open"
    assert cb.allow() is False, "an open breaker must short-circuit the call"


def test_breaker_counts_CONSECUTIVE_failures_only(clock):
    """A success resets the count. Without that, a provider that fails 1-in-10 all
    day would eventually trip the breaker and take coaching down while OpenAI is
    perfectly healthy."""
    cb = resilience.CircuitBreaker(fail_threshold=3, cooldown_s=30)
    cb.record_failure()
    cb.record_failure()
    cb.record_success()
    cb.record_failure()
    cb.record_failure()

    assert cb.state == "closed", "the success must have reset the consecutive count"
    assert cb.allow() is True


def test_breaker_stays_open_for_the_whole_cooldown_then_half_opens(clock):
    """The cooldown is what gives a dying provider room to recover. Probing early
    just re-fails and keeps the breaker open forever; never probing means we never
    come back."""
    cb = resilience.CircuitBreaker(fail_threshold=1, cooldown_s=30)
    cb.record_failure()

    clock.advance(29.9)
    assert cb.allow() is False and cb.state == "open"

    clock.advance(0.1)  # cooldown exactly elapsed
    assert cb.allow() is True, "the breaker must eventually probe, or it never recovers"
    assert cb.state == "half_open"


def test_a_successful_probe_closes_the_breaker(clock, caplog):
    """Recovery must be automatic. If a good probe didn't close the breaker, the
    app would keep serving the degraded safe-reply after OpenAI came back."""
    cb = resilience.CircuitBreaker(fail_threshold=1, cooldown_s=10)
    cb.record_failure()
    clock.advance(10)
    assert cb.allow() is True  # half-open probe

    with caplog.at_level(logging.INFO, logger="cerebrozen.resilience"):
        cb.record_success()

    assert cb.state == "closed"
    assert cb.allow() is True
    assert "breaker.closed" in caplog.text, "recovery must be visible in the logs"


def test_a_failed_probe_re_opens_the_breaker_for_a_fresh_cooldown(clock, caplog):
    """The half-open probe is a single request's worth of risk. If its failure did
    not re-open the breaker (with the cooldown restarted), every subsequent call
    would sail through to a provider we already know is down."""
    cb = resilience.CircuitBreaker(fail_threshold=5, cooldown_s=10)
    with caplog.at_level(logging.WARNING, logger="cerebrozen.resilience"):
        for _ in range(5):
            cb.record_failure()
    assert cb.state == "open"
    assert "breaker.open" in caplog.text, "opening the breaker is an incident — log it"

    clock.advance(10)
    assert cb.allow() is True and cb.state == "half_open"

    cb.record_failure()  # the probe fails — note: below fail_threshold on its own
    assert cb.state == "open", "a failed probe must re-open immediately, not re-count to 5"
    assert cb.allow() is False

    clock.advance(9.99)
    assert cb.allow() is False, "the cooldown must restart from the re-open, not the first open"
    clock.advance(0.01)
    assert cb.allow() is True


def test_breaker_defaults_come_from_config(clock, monkeypatch):
    """Ops turn these knobs during an incident; they have to actually be read."""
    monkeypatch.setattr(config, "BREAKER_FAIL_THRESHOLD", 2)
    monkeypatch.setattr(config, "BREAKER_COOLDOWN_S", 7.0)
    cb = resilience.CircuitBreaker()

    assert (cb.fail_threshold, cb.cooldown_s) == (2, 7.0)
    cb.record_failure()
    cb.record_failure()
    assert cb.state == "open"


def test_the_process_breaker_is_a_single_shared_instance(monkeypatch):
    """A per-call breaker counts to N and never gets there — it would be a no-op.
    The whole mechanism depends on one instance seeing every failure."""
    monkeypatch.setattr(resilience, "_breaker", None)

    first = resilience.get_breaker()
    assert resilience.get_breaker() is first
    assert first.fail_threshold == config.BREAKER_FAIL_THRESHOLD


def test_breaker_is_thread_safe_under_concurrent_failures(clock):
    """One in-flight turn per session, but many sessions per process — the failure
    count is shared mutable state. A lost update would move the trip point."""
    cb = resilience.CircuitBreaker(fail_threshold=50, cooldown_s=30)
    threads = [threading.Thread(target=cb.record_failure) for _ in range(50)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert cb.state == "open", "50 concurrent failures must count as 50, not fewer"


# ═══════════════════════════════════════════════════════════════════════════════
# resilience.py — the model cascade
# ═══════════════════════════════════════════════════════════════════════════════


def test_the_requested_model_is_always_tried_first(monkeypatch):
    """The workbook's Catalog picks a model per agent — that choice is a product
    decision (a coaching turn on gpt-5-nano is a different, worse product). The
    cascade is a fallback, never a substitution: if the cascade could preempt the
    requested model, every turn would silently run on the cheap one.
    """
    monkeypatch.setattr(config, "MODEL_CASCADE", ["gpt-5-mini", "gpt-5-nano"])

    assert resilience.candidate_models("gpt-5")[0] == "gpt-5"
    # even when the requested model isn't in the cascade at all
    assert resilience.candidate_models("o4-custom") == ["o4-custom", "gpt-5-mini", "gpt-5-nano"]


def test_the_cascade_never_retries_the_same_model_twice(monkeypatch):
    """A duplicate entry means we burn a fallback attempt (and its full timeout) on
    the model that just failed — the user waits twice as long for the same error."""
    monkeypatch.setattr(config, "MODEL_CASCADE", ["gpt-5-mini", "gpt-5", "gpt-5-mini", ""])

    assert resilience.candidate_models("gpt-5-mini") == ["gpt-5-mini", "gpt-5"]


def test_an_empty_cascade_still_tries_the_requested_model(monkeypatch):
    """Misconfiguring CEREBROZEN_MODEL_CASCADE to empty must degrade to 'no fallback',
    not to 'no model'."""
    monkeypatch.setattr(config, "MODEL_CASCADE", [])

    assert resilience.candidate_models("gpt-5") == ["gpt-5"]


# ═══════════════════════════════════════════════════════════════════════════════
# pricing.py
# ═══════════════════════════════════════════════════════════════════════════════


@pytest.fixture(autouse=True)
def _forget_unknown_model_warnings():
    """pricing warns once per unknown model, process-wide. Reset it so the warning
    assertions below don't depend on test order."""
    pricing._warned_unknown.clear()
    yield
    pricing._warned_unknown.clear()


def test_cost_is_computed_per_million_tokens_at_the_model_rate():
    """This number is the product's unit economics — it feeds the per-turn cost
    metric and the ROI reporting. A factor-of-1000 slip here (per-1K vs per-1M) is
    invisible in the logs and wrong in every business review."""
    # gpt-5-mini: 0.25 in / 2.00 out per 1M
    assert pricing.estimate_cost("gpt-5-mini", prompt_tokens=1_000_000) == 0.25
    assert pricing.estimate_cost("gpt-5-mini", completion_tokens=1_000_000) == 2.0
    assert pricing.estimate_cost(
        "gpt-5-mini", prompt_tokens=20_000, completion_tokens=1_000
    ) == round((20_000 * 0.25 + 1_000 * 2.00) / 1e6, 6)
    # zero usage costs nothing
    assert pricing.estimate_cost("gpt-5") == 0.0


def test_cached_prompt_tokens_are_billed_at_the_cheaper_cached_rate():
    """Prompt caching is the reason a 20K-token system prompt is affordable at all
    (measured: 83% cached, ~48% cheaper per turn). If cached tokens were billed at
    the full input rate the saving would never show up in the cost metric — and the
    caching work would look worthless."""
    # 20K prompt of which 16K cached: 4K @ 0.25 + 16K @ 0.025 per 1M
    expected = round((4_000 * 0.25 + 16_000 * 0.025) / 1e6, 6)
    got = pricing.estimate_cost("gpt-5-mini", prompt_tokens=20_000, cached_tokens=16_000)

    assert got == expected
    full_price = pricing.estimate_cost("gpt-5-mini", prompt_tokens=20_000)
    assert got < full_price, "a cache hit must cost less than a cache miss"


def test_cached_tokens_are_a_subset_of_prompt_tokens_not_an_addition():
    """OpenAI reports cached_tokens as part of prompt_tokens. Adding them instead of
    subtracting would double-count the whole prefix and inflate reported cost."""
    both = pricing.estimate_cost("gpt-5-mini", prompt_tokens=1000, cached_tokens=1000)
    assert both == round(1000 * 0.025 / 1e6, 6)

    # A provider reporting more cached than prompt tokens must not produce a NEGATIVE cost.
    weird = pricing.estimate_cost("gpt-5-mini", prompt_tokens=100, cached_tokens=1000)
    assert weird >= 0.0


def test_an_unknown_model_costs_zero_and_never_raises(caplog):
    """The load-bearing one: cost estimation runs INSIDE a turn. The day OpenAI ships
    gpt-6 and someone puts it in the workbook Catalog, an exception here would take
    down every coaching reply on that agent. It must degrade to 0.0 and shout."""
    with caplog.at_level(logging.WARNING, logger="cerebrozen.pricing"):
        cost = pricing.estimate_cost("gpt-6-turbo", prompt_tokens=999, completion_tokens=999)

    assert cost == 0.0
    assert "pricing.unknown_model" in caplog.text, "an unpriced model must be visible in logs"


def test_the_unknown_model_warning_is_logged_once_not_once_per_turn():
    """A per-turn warning on every call for an unpriced model would flood CloudWatch
    (and cost more than the tokens)."""
    logger = logging.getLogger("cerebrozen.pricing")
    records = []
    handler = logging.Handler()
    handler.emit = records.append
    logger.addHandler(handler)
    try:
        for _ in range(5):
            pricing.estimate_cost("gpt-6-turbo", prompt_tokens=10)
    finally:
        logger.removeHandler(handler)

    assert len([r for r in records if r.msg == "pricing.unknown_model"]) == 1


def test_an_empty_model_id_is_silently_free():
    """Not every LLMResponse carries a model (the mock provider, a degraded turn).
    That is not an operational event and must not warn."""
    assert pricing.estimate_cost("", prompt_tokens=1000) == 0.0
    assert pricing._warned_unknown == set()


def test_point_releases_fall_back_to_the_model_family_price():
    """OpenAI ships gpt-5.4 / dated snapshots continuously. Without the prefix
    fallback every one of them would price at 0.0 — cost reporting would quietly go
    to zero exactly when the flagship model is in use."""
    assert pricing.estimate_cost("gpt-5.4", prompt_tokens=1_000_000) == 10.00  # gpt-5 family
    assert pricing.estimate_cost(
        "gpt-5-mini-2026-01-01", prompt_tokens=1_000_000
    ) == 0.25  # gpt-5-mini family
    # A model with nothing to strip must not loop forever — it must just be unknown.
    assert pricing.estimate_cost("llama3", prompt_tokens=1_000_000) == 0.0
    assert pricing.estimate_cost("mistral-large", prompt_tokens=1_000_000) == 0.0


def test_a_price_can_be_corrected_by_env_without_a_deploy(monkeypatch):
    """OpenAI changes prices; a redeploy to correct a number is a bad day. The env
    override is the escape hatch — if it stops being read, cost reporting is stuck
    on whatever was hard-coded at build time."""
    monkeypatch.setenv("CEREBROZEN_PRICE_GPT_5_MINI", "1.0,0.1,4.0")

    assert pricing.estimate_cost("gpt-5-mini", prompt_tokens=1_000_000) == 1.0
    assert pricing.estimate_cost("gpt-5-mini", cached_tokens=1_000_000) == 0.1
    assert pricing.estimate_cost("gpt-5-mini", completion_tokens=1_000_000) == 4.0
    # the key derivation: non-alphanumerics → underscores, upper-cased
    assert pricing._env_key("gpt-4o-mini") == "CEREBROZEN_PRICE_GPT_4O_MINI"


def test_a_malformed_env_override_falls_back_to_the_table_and_warns(monkeypatch, caplog):
    """A typo in an SSM param must not zero out (or crash) cost reporting — it must
    fall back to the shipped price and say so."""
    monkeypatch.setenv("CEREBROZEN_PRICE_GPT_5_MINI", "0.25,oops,2.00")

    with caplog.at_level(logging.WARNING, logger="cerebrozen.pricing"):
        cost = pricing.estimate_cost("gpt-5-mini", prompt_tokens=1_000_000)

    assert cost == 0.25, "must fall back to the bundled price, not to 0"
    assert "pricing.bad_env_override" in caplog.text


def test_an_env_override_with_the_wrong_number_of_prices_is_ignored(monkeypatch):
    """'0.25,2.00' (forgetting the cached rate) is the likely human error. Silently
    treating 2.00 as the CACHED price would misreport every cached turn."""
    monkeypatch.setenv("CEREBROZEN_PRICE_GPT_5_MINI", "0.25,2.00")

    assert pricing.estimate_cost("gpt-5-mini", prompt_tokens=1_000_000) == 0.25
    assert pricing.estimate_cost("gpt-5-mini", completion_tokens=1_000_000) == 2.00


# ═══════════════════════════════════════════════════════════════════════════════
# prompt_store.py — workbook source resolution + the S3 fallback
# ═══════════════════════════════════════════════════════════════════════════════


class _FakeS3:
    """An in-memory S3. Only the four calls prompt_store makes, and a real
    botocore ClientError so the module's own error-code branching runs for real."""

    def __init__(self, objects=None, fail_on=None):
        from botocore.exceptions import ClientError

        self.objects = dict(objects or {})          # (bucket, key) -> bytes
        self.etags = {}                             # (bucket, key) -> forced ETag
        self.fail_on = fail_on or {}                # method -> exception to raise
        self.requests = []                          # every call, for boundary assertions
        self.exceptions = types.SimpleNamespace(ClientError=ClientError)

    def _client_error(self, code, op):
        from botocore.exceptions import ClientError

        return ClientError({"Error": {"Code": code, "Message": code}}, op)

    def _maybe_fail(self, method):
        if method in self.fail_on:
            raise self.fail_on[method]

    def download_file(self, Bucket, Key, Filename, ExtraArgs=None):
        self.requests.append(("download_file", Bucket, Key, ExtraArgs))
        self._maybe_fail("download_file")
        if (Bucket, Key) not in self.objects:
            raise self._client_error("404", "GetObject")
        with open(Filename, "wb") as fh:
            fh.write(self.objects[(Bucket, Key)])

    def head_object(self, Bucket, Key):
        self.requests.append(("head_object", Bucket, Key, None))
        self._maybe_fail("head_object")
        if (Bucket, Key) not in self.objects:
            raise self._client_error("404", "HeadObject")
        etag = self.etags.get(
            (Bucket, Key), hashlib.md5(self.objects[(Bucket, Key)]).hexdigest()
        )
        return {"ETag": f'"{etag}"'}

    def copy_object(self, Bucket, Key, CopySource):
        self.requests.append(("copy_object", Bucket, Key, CopySource))
        self._maybe_fail("copy_object")
        self.objects[(Bucket, Key)] = self.objects[
            (CopySource["Bucket"], CopySource["Key"])
        ]

    def put_object(self, Bucket, Key, Body, ContentType=None):
        self.requests.append(("put_object", Bucket, Key, ContentType))
        self._maybe_fail("put_object")
        self.objects[(Bucket, Key)] = Body


@pytest.fixture
def s3(monkeypatch):
    """Swap boto3 itself (the true external boundary) — prompt_store's own
    _s3_client(), region pinning and error handling all still run."""
    import sys

    fake = _FakeS3()
    created = []

    def _client(service, region_name=None):
        created.append((service, region_name))
        return fake

    monkeypatch.setitem(
        sys.modules, "boto3", types.SimpleNamespace(client=_client)
    )
    fake.created = created
    return fake


@pytest.fixture
def cache_path(monkeypatch, tmp_path):
    """Redirect the server-side workbook cache into tmp — the real one is a shared
    path under /tmp that other processes (and the running app) read."""
    p = tmp_path / "agent_prompts.xlsx"
    monkeypatch.setattr(prompt_store, "WORKBOOK_CACHE_PATH", p)
    return p


def test_codebase_mode_uses_the_bundled_workbook_and_never_touches_s3(monkeypatch):
    """Local dev and CI run with no AWS credentials at all. If codebase mode reached
    for boto3 the whole test suite (and every dev box) would fail to load prompts."""
    import sys

    monkeypatch.setattr(config, "PROMPT_SOURCE", "codebase")
    monkeypatch.setitem(sys.modules, "boto3", None)  # any use of boto3 → TypeError

    resolved = prompt_store.resolve_workbook()

    assert resolved == {
        "path": config.PROMPT_WORKBOOK,
        "source": "codebase",
        "fallback": False,
        "error": None,
    }
    assert prompt_store.resolve_workbook_path() == config.PROMPT_WORKBOOK


def test_s3_mode_downloads_the_workbook_and_reports_it_as_live(monkeypatch, s3, cache_path):
    """The deployed default. The registry must load the S3 copy (what the prompt
    engineers actually edited), not the bundled one that shipped with the image."""
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "dev-system-configuration")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "agentic_prompts/agent_prompts.xlsx")
    monkeypatch.setattr(config, "PROMPT_S3_VERSION", "")
    s3.objects[("dev-system-configuration", "agentic_prompts/agent_prompts.xlsx")] = b"XLSX-FROM-S3"

    resolved = prompt_store.resolve_workbook()

    assert resolved["source"] == "s3" and resolved["fallback"] is False
    assert resolved["path"] == str(cache_path)
    assert cache_path.read_bytes() == b"XLSX-FROM-S3", "the S3 bytes must land in the cache"
    # The prompt bucket's region is pinned explicitly: the RAG store rewrites AWS_REGION
    # for ITS bucket, and an ambient region would send this request to the wrong one.
    assert s3.created == [("s3", config.AWS_REGION)]


def test_a_pinned_workbook_version_is_actually_requested(monkeypatch, s3, cache_path):
    """PROMPT_S3_VERSION is the rollback lever: pin the last-good object and reload.
    If the VersionId never reached S3, the rollback would appear to work and serve
    the broken prompts anyway."""
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "k.xlsx")
    monkeypatch.setattr(config, "PROMPT_S3_VERSION", "v-last-good")
    s3.objects[("b", "k.xlsx")] = b"rolled-back"

    prompt_store.resolve_workbook()

    method, bucket, key, extra = s3.requests[0]
    assert (method, bucket, key) == ("download_file", "b", "k.xlsx")
    assert extra == {"VersionId": "v-last-good"}


def test_a_failed_s3_fetch_falls_back_to_the_bundled_workbook_and_logs_LOUDLY(
    monkeypatch, s3, cache_path, caplog
):
    """The single most important behaviour in this module: a bad bucket, an expired
    role, an S3 outage — none of them may take prompts down. The app must serve the
    bundled workbook and mark itself degraded.

    And it must log at ERROR, not WARNING: serving stale bundled prompts in an
    S3-configured environment is config drift, i.e. an incident. A warning gets lost.
    """
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "wrong-bucket")
    s3.fail_on["download_file"] = Exception("An error occurred (403) when calling GetObject")

    with caplog.at_level(logging.ERROR, logger="cerebrozen.prompt_store"):
        resolved = prompt_store.resolve_workbook()

    assert resolved["path"] == config.PROMPT_WORKBOOK, "prompts must still load"
    assert resolved["source"] == "s3-fallback"
    assert resolved["fallback"] is True, "the degradation must be visible to /v1/prompts"
    assert "403" in resolved["error"]

    errors = [r for r in caplog.records if r.levelno >= logging.ERROR]
    assert [r.msg for r in errors] == ["prompt_store.s3_fallback"]


def test_s3_mode_with_no_bucket_configured_falls_back_instead_of_crashing(
    monkeypatch, s3, cache_path
):
    """PROMPT_S3_BUCKET has no safe default (it is per-environment), so an env that
    forgets it must still boot on bundled prompts — and the error must name the
    missing param, not surface as some opaque boto3 exception."""
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "")

    resolved = prompt_store.resolve_workbook()

    assert resolved["fallback"] is True and resolved["source"] == "s3-fallback"
    assert "PROMPT_S3_BUCKET" in resolved["error"]
    assert resolved["path"] == config.PROMPT_WORKBOOK


def test_checksum_detects_a_server_cache_that_drifted_from_s3(monkeypatch, s3, cache_path):
    """The reload endpoint's proof-of-freshness. If it reported `match` on different
    bytes, an operator would believe their prompt edit was live when the process is
    still serving the old workbook."""
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "k.xlsx")
    cache_path.write_bytes(b"the workbook this process loaded")

    s3.objects[("b", "k.xlsx")] = b"the workbook this process loaded"
    same = prompt_store.workbook_checksum()
    assert same["match"] is True
    assert same["local_md5"] == same["s3_md5"] == hashlib.md5(b"the workbook this process loaded").hexdigest()
    assert same["note"] is None

    s3.objects[("b", "k.xlsx")] = b"someone uploaded a newer workbook"
    drifted = prompt_store.workbook_checksum()
    assert drifted["match"] is False, "different bytes must never report as a match"


def test_a_multipart_etag_is_reported_as_uncomparable_not_as_a_mismatch(
    monkeypatch, s3, cache_path
):
    """A multipart-uploaded object's ETag is `<md5>-<parts>`, not an MD5. Comparing
    it verbatim would report a permanent false mismatch and send an operator chasing
    a drift that isn't there — so it must say `match: false` WITH the reason."""
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "k.xlsx")
    cache_path.write_bytes(b"content")
    s3.objects[("b", "k.xlsx")] = b"content"
    s3.etags[("b", "k.xlsx")] = f"{hashlib.md5(b'content').hexdigest()}-2"

    result = prompt_store.workbook_checksum()

    assert result["s3_md5"] is None and result["match"] is False
    assert "multipart" in result["note"]
    assert prompt_store._s3_etag_to_md5('"abc123"') == "abc123"


def test_checksum_degrades_to_an_error_field_rather_than_raising(monkeypatch, s3, cache_path):
    """It backs an HTTP endpoint — an unreachable S3 must be a 200 with an `error`,
    not a 500."""
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")

    # 1. codebase mode: there is nothing to compare against
    monkeypatch.setattr(config, "PROMPT_SOURCE", "codebase")
    assert "codebase" in prompt_store.workbook_checksum()["error"]

    # 2. s3 mode, but the server has never downloaded the workbook
    monkeypatch.setattr(config, "PROMPT_SOURCE", "s3")
    assert "reload" in prompt_store.workbook_checksum()["error"]

    # 3. s3 mode, cache present, S3 unreachable
    cache_path.write_bytes(b"cached")
    s3.fail_on["head_object"] = Exception("connection timed out")
    result = prompt_store.workbook_checksum()
    assert result["local_md5"] == hashlib.md5(b"cached").hexdigest()
    assert "connection timed out" in result["error"]


def test_the_local_md5_is_streamed_correctly_for_a_large_workbook(cache_path):
    """The real workbook is ~1 MB, read in 64 KB chunks. A chunking bug would hash
    only the first block and report a match on a workbook that changed."""
    blob = b"".join(bytes([i % 251]) * 1024 for i in range(200))  # ~200 KB, >3 chunks
    cache_path.write_bytes(blob)

    assert prompt_store._file_md5(cache_path) == hashlib.md5(blob).hexdigest()


def test_an_upload_backs_up_the_previous_workbook_before_replacing_it(monkeypatch, s3):
    """A prompt engineer uploading a broken workbook is a routine event, and the
    backup is the ONLY way back — S3 versioning is not assumed to be on. If the
    backup silently didn't happen, the previous prompts would be gone for good."""
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "agentic_prompts/agent_prompts.xlsx")
    s3.objects[("b", "agentic_prompts/agent_prompts.xlsx")] = b"the good workbook"

    result = prompt_store.upload_workbook_to_s3(b"the new workbook")

    assert s3.objects[("b", "agentic_prompts/agent_prompts.xlsx")] == b"the new workbook"
    backup = result["backup_key"]
    assert backup and backup != config.PROMPT_S3_KEY
    assert s3.objects[("b", backup)] == b"the good workbook", "the old bytes must be recoverable"
    assert backup.startswith("agentic_prompts/agent_prompts_") and backup.endswith(".xlsx")
    assert result["md5"] == hashlib.md5(b"the new workbook").hexdigest()
    assert result["bucket"] == "b"


def test_the_first_ever_upload_needs_no_backup(monkeypatch, s3):
    """A brand-new environment has no object to copy. A 404 on the backup step must
    not block the very first upload."""
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "k.xlsx")

    result = prompt_store.upload_workbook_to_s3(b"first")

    assert result["backup_key"] is None
    assert s3.objects[("b", "k.xlsx")] == b"first"


def test_an_upload_aborts_when_the_backup_fails_for_a_REAL_reason(monkeypatch, s3):
    """AccessDenied on head_object is not 'no object there' — it means we cannot
    read what we are about to overwrite. Treating it like a 404 would destroy the
    live workbook with no backup. It must raise."""
    from botocore.exceptions import ClientError

    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "b")
    monkeypatch.setattr(config, "PROMPT_S3_KEY", "k.xlsx")
    s3.objects[("b", "k.xlsx")] = b"the live workbook"
    s3.fail_on["head_object"] = ClientError(
        {"Error": {"Code": "AccessDenied", "Message": "denied"}}, "HeadObject"
    )

    with pytest.raises(ClientError):
        prompt_store.upload_workbook_to_s3(b"replacement")

    assert s3.objects[("b", "k.xlsx")] == b"the live workbook", "the live object must survive"


def test_an_upload_with_no_bucket_configured_fails_loudly(monkeypatch, s3):
    """Unlike a READ (which falls back to the bundled workbook), a WRITE with no
    bucket has nowhere to go — reporting success would lose the user's edit."""
    monkeypatch.setattr(config, "PROMPT_S3_BUCKET", "")

    with pytest.raises(RuntimeError, match="PROMPT_S3_BUCKET"):
        prompt_store.upload_workbook_to_s3(b"data")


def test_the_backup_key_is_timestamped_and_keeps_the_extension(monkeypatch):
    """Backups must sort chronologically and stay .xlsx — an operator restoring one
    downloads it by name."""
    key = prompt_store._backup_key("agentic_prompts/agent_prompts.xlsx")

    prefix, _, name = key.rpartition("/")
    assert prefix == "agentic_prompts"
    assert name.startswith("agent_prompts_") and name.endswith("Z.xlsx")


# ═══════════════════════════════════════════════════════════════════════════════
# providers/__init__.py — the provider factory
# ═══════════════════════════════════════════════════════════════════════════════


@pytest.fixture
def provider_factory(monkeypatch):
    """Reset the process-wide provider singleton around each factory test — the rest
    of the suite runs on the cached mock provider and must get it back."""
    from app.llm import providers

    providers.reset_provider()
    yield providers
    providers.reset_provider()


def test_a_missing_api_key_silently_selects_the_mock_provider(provider_factory, monkeypatch, caplog):
    """This is why the test suite and every keyless dev box can run the full graph.
    It is also a trap in production: a lost OPENAI_API_KEY would serve canned
    '[mock]' replies to real users instead of erroring — so the fallback MUST log."""
    from app.llm.providers.mock import MockLLMClient

    monkeypatch.delenv("CEREBROZEN_LLM_PROVIDER", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "")
    monkeypatch.delenv("OPENAI_ADMIN_KEY", raising=False)

    with caplog.at_level(logging.WARNING, logger="cerebrozen.llm.providers"):
        provider = provider_factory.get_provider()

    assert isinstance(provider, MockLLMClient)
    assert "llm.provider_mock" in caplog.text
    assert [r.reason for r in caplog.records if r.msg == "llm.provider_mock"] == ["no_api_key"]


def test_the_provider_is_a_singleton_and_resettable(provider_factory, monkeypatch):
    """Each provider owns an HTTP client (and the Ollama one, a KV-cache-warm model).
    Rebuilding it per call would throw away connection pooling on every turn."""
    monkeypatch.setenv("OPENAI_API_KEY", "")

    first = provider_factory.get_provider()
    assert provider_factory.get_provider() is first

    provider_factory.reset_provider()
    assert provider_factory.get_provider() is not first


def test_an_explicit_mock_provider_is_honoured_even_with_a_key(provider_factory, monkeypatch, caplog):
    """CI/eval runs set CEREBROZEN_LLM_PROVIDER=mock to guarantee no spend even where a
    key happens to be exported."""
    from app.llm.providers.mock import MockLLMClient

    monkeypatch.setenv("CEREBROZEN_LLM_PROVIDER", "mock")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-real-key-would-cost-money")

    with caplog.at_level(logging.WARNING, logger="cerebrozen.llm.providers"):
        provider = provider_factory.get_provider()

    assert isinstance(provider, MockLLMClient)
    assert [r.reason for r in caplog.records if r.msg == "llm.provider_mock"] == ["explicit"]


@pytest.mark.parametrize("name", ["openai", "responses", "openai_responses"])
def test_the_openai_provider_is_selected_when_a_key_is_present(provider_factory, monkeypatch, name):
    """The deployed path. All three spellings must resolve, and a key must NOT be
    quietly ignored in favour of the mock."""
    from app.llm.responses_client import OpenAIResponsesClient

    monkeypatch.setenv("CEREBROZEN_LLM_PROVIDER", name)
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test-not-used-offline")

    assert isinstance(provider_factory.get_provider(), OpenAIResponsesClient)


def test_an_admin_key_alone_is_enough_to_stay_on_openai(provider_factory, monkeypatch):
    """Some environments only carry OPENAI_ADMIN_KEY. Falling back to the mock there
    would serve canned replies to real users."""
    from app.llm.responses_client import OpenAIResponsesClient

    from app.llm.providers.mock import MockLLMClient

    monkeypatch.setenv("CEREBROZEN_LLM_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "")
    monkeypatch.setenv("OPENAI_ADMIN_KEY", "sk-admin")
    # The OpenAI SDK constructor reads OPENAI_API_KEY itself and would refuse; stub the
    # SDK (the external boundary) so the FACTORY's key check is what's under test.
    monkeypatch.setattr("openai.OpenAI", lambda **kw: types.SimpleNamespace(**kw))

    provider = provider_factory.get_provider()

    assert not isinstance(provider, MockLLMClient), (
        "a key IS present (admin) — falling back to canned '[mock]' replies would be silent"
    )
    assert isinstance(provider, OpenAIResponsesClient)


def test_the_ollama_provider_is_selected_by_config_alone(provider_factory, monkeypatch):
    """The offline backend is sold as a config change, not a fork — no key needed,
    and it must not be hijacked by the keyless mock fallback."""
    from app.llm.providers.ollama import OllamaClient

    monkeypatch.setenv("CEREBROZEN_LLM_PROVIDER", "ollama")
    monkeypatch.setenv("OPENAI_API_KEY", "")

    assert isinstance(provider_factory.get_provider(), OllamaClient)


@pytest.mark.parametrize("name", ["gemini", "anthropic"])
def test_unapproved_providers_raise_instead_of_pretending(provider_factory, monkeypatch, name):
    """Half-wiring a provider that doesn't exist would boot the app and fail on the
    first turn. These fail at selection, with a reason."""
    monkeypatch.setenv("CEREBROZEN_LLM_PROVIDER", name)

    with pytest.raises(NotImplementedError, match="Phase 2"):
        provider_factory.get_provider()


def test_a_typo_in_the_provider_name_fails_loudly(provider_factory, monkeypatch):
    """CEREBROZEN_LLM_PROVIDER=openal must not silently become the mock (canned replies
    in production) — it must refuse to start."""
    monkeypatch.setenv("CEREBROZEN_LLM_PROVIDER", "openal")

    with pytest.raises(ValueError, match="openal"):
        provider_factory.get_provider()


# ═══════════════════════════════════════════════════════════════════════════════
# providers/ollama.py — the grammar (the routing contract)
# ═══════════════════════════════════════════════════════════════════════════════


def _schema_violations(schema, payload):
    """Validate `payload` against the subset of JSON Schema this grammar uses
    (required / type / enum) — i.e. the constraints Ollama's grammar enforces during
    decoding. Returns the list of violations; empty means the model COULD emit it."""
    problems = []
    for field in schema["required"]:
        if field not in payload:
            problems.append(f"missing required field: {field}")
    for field, value in payload.items():
        spec = schema["properties"].get(field)
        if spec is None:
            continue
        if "enum" in spec and value not in spec["enum"]:
            problems.append(f"{field}={value!r} not in enum {spec['enum']}")
        expected = {"string": str, "boolean": bool, "object": dict}[spec["type"]]
        if not isinstance(value, expected):
            problems.append(f"{field} is not a {spec['type']}")
    return problems


def test_coaching_path_is_IMPOSSIBLE_to_omit_on_the_routing_stage():
    """The correctness guarantee of the whole offline backend.

    challenge_context_agent's ONLY job is to choose the coaching path. Measured
    against a live 8B with coaching_path merely OPTIONAL, the model omitted it on
    3/3 CH-shaped goals → the router saw no path → silent CIM fallback → the CH
    path became UNREACHABLE, with no error anywhere. The graph cannot detect this.

    `required` is what turns "the model might mention a path" into "the grammar
    cannot terminate the object without one". A reply that omits it must be
    unemittable, and a reply that invents a path outside the router's enum must be
    unemittable too — an unknown path routes nowhere.
    """
    schema = ollama.control_schema("challenge_context_agent")

    omitted = {"response_to_user": "Tell me more.", "handoff_ready": False}
    assert _schema_violations(schema, omitted) == ["missing required field: coaching_path"]

    invented = {**omitted, "coaching_path": "GROW"}
    assert _schema_violations(schema, invented), "a path the router doesn't know must be rejected"

    for path in ("CIM", "CBT", "CH"):
        assert _schema_violations(schema, {**omitted, "coaching_path": path}) == []

    assert schema["properties"]["coaching_path"]["enum"] == ["CIM", "CBT", "CH"]


def test_every_stage_is_forced_to_emit_user_text_and_a_handoff_decision():
    """Without response_to_user the turn streams nothing (the user sees an empty
    bubble); without handoff_ready the graph can never advance a stage and the
    session is stuck forever. Both are structural, for every agent."""
    for stage in ("core_coaching_agent", "CH_coaching_agent", "role_play_agent", ""):
        schema = ollama.control_schema(stage)
        assert _schema_violations(schema, {}) == [
            "missing required field: response_to_user",
            "missing required field: handoff_ready",
        ]
        assert _schema_violations(schema, {"response_to_user": "hi", "handoff_ready": True}) == []


def test_a_non_routing_stage_may_still_offer_a_path_but_is_not_forced_to():
    """coaching_path stays available (and enum-constrained) everywhere — but forcing
    every agent to invent a path would have the mid-session coaching agent silently
    re-routing the user."""
    schema = ollama.control_schema("core_coaching_agent")

    assert "coaching_path" not in schema["required"]
    assert schema["properties"]["coaching_path"]["enum"] == ["CIM", "CBT", "CH"]
    assert _schema_violations(schema, {"response_to_user": "x", "handoff_ready": False,
                                       "coaching_path": "NOPE"})


def test_every_forced_routing_field_is_actually_constrained_by_a_property():
    """A field named in _ROUTING_REQUIRED but missing from `properties` would be
    required-but-unconstrained: the model must emit the key, and can put ANY string
    in it. That is the CIM-fallback bug wearing a disguise."""
    for stage, fields in ollama._ROUTING_REQUIRED.items():
        schema = ollama.control_schema(stage)
        for field in fields:
            assert field in schema["properties"], (
                f"{stage}: {field} is forced but has no property spec — the grammar "
                "cannot constrain its VALUE, only its presence."
            )


# ═══════════════════════════════════════════════════════════════════════════════
# providers/ollama.py — the HTTP call
# ═══════════════════════════════════════════════════════════════════════════════


def _ollama_http(monkeypatch, handler):
    """Fake ONLY the transport. `ollama.httpx` still resolves to a REAL httpx client
    (real streaming, real raise_for_status, real json) — just one that never leaves
    the process. Returns the list of requests our code actually made."""
    seen = []

    def _handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return handler(request)

    monkeypatch.setattr(
        ollama,
        "httpx",
        types.SimpleNamespace(
            Client=lambda **kw: httpx.Client(transport=httpx.MockTransport(_handler), **kw)
        ),
    )
    return seen


def _body(request: httpx.Request) -> dict:
    return json.loads(request.content)


def _ok(payload: dict) -> httpx.Response:
    return httpx.Response(200, json=payload)


def test_a_non_streaming_call_returns_the_reply_and_real_token_counts(monkeypatch):
    """The LLMResponse feeds the turn's telemetry (tokens, latency, cost). Dropping
    the counts here doesn't break the reply — it silently zeroes the whole metrics
    story for every offline deployment."""
    seen = _ollama_http(
        monkeypatch,
        lambda r: _ok({
            "message": {"content": '{"response_to_user": "hello"}'},
            "prompt_eval_count": 20116,
            "eval_count": 137,
            "prompt_eval_duration": 80_000_000,  # ns → 80ms
        }),
    )

    resp = ollama.OllamaClient().generate(
        "SYSTEM", "USER", "gemma4:8b", stage="core_coaching_agent"
    )

    assert resp.text == '{"response_to_user": "hello"}'
    assert (resp.prompt_tokens, resp.completion_tokens) == (20116, 137)
    assert resp.total_tokens == 20253
    assert resp.model == "gemma4:8b"
    assert resp.cost_usd == 0.0, "a self-hosted model has no per-token cost"
    assert resp.model_latency_ms > 0
    assert str(seen[0].url) == "http://localhost:11434/api/chat"
    assert _body(seen[0])["stream"] is False


def test_thinking_is_forced_OFF_on_every_call(monkeypatch):
    """Measured: gemma4 left to reason spends its ENTIRE output budget on hidden
    thinking tokens and returns an empty string (done_reason: length). think=False
    is not a tuning preference — without it the offline backend returns nothing."""
    seen = _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": "hi"}}))
    client = ollama.OllamaClient()

    client.generate("S", "U", "m")
    client.generate_stream("S", "U", "m", on_token=lambda t: None)
    client.prewarm("S")

    assert [_body(r)["think"] for r in seen] == [False, False, False]


def test_the_context_window_is_set_explicitly_and_is_bigger_than_the_prompt(monkeypatch):
    """Ollama defaults to num_ctx=4096 and SILENTLY truncates above it — which would
    lop the head off a 20K-token system prompt and leave the model reading the middle
    of its own instructions. There is no error; the coach just gets worse."""
    seen = _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": "hi"}}))

    ollama.OllamaClient().generate("S", "U", "m")

    num_ctx = _body(seen[0])["options"]["num_ctx"]
    assert num_ctx == 32768
    assert num_ctx > 26_700, "must exceed the worst-case prompt (CH_coaching + environment)"

    monkeypatch.setenv("CEREBROZEN_OLLAMA_NUM_CTX", "8192")
    assert ollama._num_ctx() == 8192


def test_the_routing_grammar_is_actually_attached_to_the_request(monkeypatch):
    """The schema being correct is worthless if it never reaches Ollama — `format` is
    what constrains decoding. This is the end-to-end version of the contract test
    above: the wire payload for the routing stage must force coaching_path."""
    seen = _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": "{}"}}))
    client = ollama.OllamaClient()

    client.generate("S", "U", "m", stage="challenge_context_agent")
    client.generate_stream("S", "U", "m", on_token=lambda t: None, stage="core_coaching_agent")

    assert "coaching_path" in _body(seen[0])["format"]["required"]
    assert _body(seen[0])["format"] == ollama.control_schema("challenge_context_agent")
    assert "coaching_path" not in _body(seen[1])["format"]["required"]


def test_the_system_prompt_is_first_and_history_is_replayed_in_order(monkeypatch):
    """The system prompt is the CACHEABLE PREFIX (measured: 2.97s cold → 0.08s warm on
    a 20K-token prompt). Anything volatile placed ahead of it destroys the KV-cache
    hit on every turn and the offline backend blows its latency budget."""
    seen = _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": "hi"}}))

    ollama.OllamaClient().generate(
        "THE 20K SYSTEM PROMPT",
        "and now the user's turn",
        "m",
        history=[
            {"role": "user", "content": "earlier"},
            {"role": "bot", "content": "a reply"},        # legacy spelling → assistant
            {"role": "assistant", "content": ""},          # empty → dropped entirely
            {"role": "system", "content": "injected!"},    # anything else → user
        ],
    )

    assert _body(seen[0])["messages"] == [
        {"role": "system", "content": "THE 20K SYSTEM PROMPT"},
        {"role": "user", "content": "earlier"},
        {"role": "assistant", "content": "a reply"},
        {"role": "user", "content": "injected!"},
        {"role": "user", "content": "and now the user's turn"},
    ]


def test_streaming_forwards_tokens_as_they_arrive_and_survives_junk_lines(monkeypatch):
    """Ollama's NDJSON stream can carry keep-alive blanks and (on a bad build) a
    non-JSON line. One bad line must not abort a turn that is already streaming to
    the user — and the final `done` chunk carries the only token counts we get."""
    lines = [
        json.dumps({"message": {"content": '{"response_to_user": "'}}),
        "",                                   # keep-alive blank
        "<not json at all>",                  # junk → skipped, not fatal
        json.dumps({"message": {"content": 'hi"}'}}),
        json.dumps({
            "done": True, "message": {"content": ""},
            "prompt_eval_count": 20116, "eval_count": 9,
            "prompt_eval_duration": 3_000_000_000,
        }),
    ]
    _ollama_http(
        monkeypatch,
        lambda r: httpx.Response(200, content=("\n".join(lines) + "\n").encode()),
    )
    tokens = []

    resp = ollama.OllamaClient().generate_stream(
        "S", "U", "m", on_token=tokens.append, stage="core_coaching_agent"
    )

    assert tokens == ['{"response_to_user": "', 'hi"}']
    assert resp.text == '{"response_to_user": "hi"}'
    assert (resp.prompt_tokens, resp.completion_tokens) == (20116, 9)


def test_a_cold_prefix_cache_is_visible_in_the_logs(monkeypatch, caplog):
    """prompt_eval_duration is the ONLY tell for whether the KV prefix cache hit
    (~80ms warm vs ~3,000ms cold on the same prompt). If a prompt-composition change
    makes the prefix volatile again, every turn silently gets ~3s slower — this log
    line is how that gets noticed."""
    _ollama_http(
        monkeypatch,
        lambda r: _ok({
            "message": {"content": "hi"},
            "prompt_eval_count": 20116,
            "prompt_eval_duration": 2_970_000_000,   # 2.97s → a cache MISS
            "eval_count": 5,
        }),
    )

    with caplog.at_level(logging.INFO, logger="cerebrozen.llm.ollama"):
        ollama.OllamaClient().generate("S", "U", "m", stage="core_coaching_agent")

    record = next(r for r in caplog.records if r.msg == "ollama.call")
    assert record.prefix_cache == "miss"
    assert record.prompt_eval_ms == 2970.0
    assert record.empty_reply is False


def test_an_empty_reply_is_reported_not_hidden(monkeypatch, caplog):
    """The reasoning-model failure mode returns HTTP 200 with an empty string. The
    turn then streams nothing at all — so the emptiness has to be in the telemetry,
    or this looks like a healthy call."""
    _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": "   "}}))

    with caplog.at_level(logging.INFO, logger="cerebrozen.llm.ollama"):
        resp = ollama.OllamaClient().generate("S", "U", "m")

    assert resp.text == "   "
    assert next(r for r in caplog.records if r.msg == "ollama.call").empty_reply is True


def test_a_5xx_from_ollama_raises_so_the_resilience_layer_can_see_it(monkeypatch):
    """Swallowing an HTTP error here and returning empty text would look, to the
    graph, exactly like the model choosing to say nothing — no retry, no fallback,
    no breaker. It must surface as an exception."""
    _ollama_http(monkeypatch, lambda r: httpx.Response(503, text="model is loading"))
    client = ollama.OllamaClient()

    with pytest.raises(httpx.HTTPStatusError):
        client.generate("S", "U", "m")
    with pytest.raises(httpx.HTTPStatusError):
        client.generate_stream("S", "U", "m", on_token=lambda t: None)

    # ...and it is retryable by class name, so the resilience layer will actually retry.
    assert resilience.is_retryable(_exc("APIConnectionError")) is True


def test_the_model_and_endpoint_come_from_env_with_working_defaults(monkeypatch):
    """A client running Ollama on another host/model changes env vars, not code. A
    stale default would send every request to a localhost that isn't there."""
    monkeypatch.setenv("OLLAMA_HOST", "http://gpu-box.internal:11434/")   # trailing slash
    monkeypatch.setenv("CEREBROZEN_OLLAMA_MODEL", "gemma4:8b")
    monkeypatch.setenv("CEREBROZEN_OLLAMA_TIMEOUT", "42")
    seen = _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": "hi"}}))

    client = ollama.OllamaClient()
    assert (client.model_default, client.timeout) == ("gemma4:8b", 42.0)

    resp = client.generate("S", "U", "")            # no model → the configured default
    assert str(seen[0].url) == "http://gpu-box.internal:11434/api/chat"
    assert _body(seen[0])["model"] == "gemma4:8b"
    assert resp.model == "gemma4:8b"
    assert ollama._base_url() == "http://gpu-box.internal:11434"


def test_prewarm_loads_the_prefix_without_generating_a_reply(monkeypatch):
    """Prewarm exists to move a 10s cold prompt-eval off the user's turn (measured:
    10.6s → 1.8s on a stage transition). It must ask for essentially no output — if
    it generated a full reply it would cost as much as the turn it is optimising."""
    seen = _ollama_http(monkeypatch, lambda r: _ok({"message": {"content": ""}}))

    ollama.OllamaClient().prewarm("THE NEXT AGENT'S 27K PROMPT", model="gemma4:8b")

    body = _body(seen[0])
    assert body["options"]["num_predict"] == 1, "prewarm must not generate a real reply"
    assert body["stream"] is False
    assert body["model"] == "gemma4:8b"
    assert body["messages"][0] == {"role": "system", "content": "THE NEXT AGENT'S 27K PROMPT"}
    assert body["options"]["num_ctx"] == 32768, "a truncated prewarm caches the WRONG prefix"


def _raise(exc):
    def _handler(request):
        raise exc
    return _handler


@pytest.mark.parametrize(
    "failure",
    [
        httpx.ConnectError("connection refused"),          # Ollama isn't running
        None,                                              # Ollama answers, with an error
    ],
)
def test_a_failed_prewarm_is_logged_as_a_failure_and_never_raises(monkeypatch, caplog, failure):
    """Two things, both load-bearing.

    (1) Prewarm is fire-and-forget, off the request path. If it raised, an unreachable
    Ollama during a background prewarm would kill a turn that was otherwise fine — the
    worst case must be 'the next turn is cold', which is where we started.

    (2) An HTTP error (404: the model was never pulled; 500: OOM) means the prefix was
    NOT cached. Logging that as `ollama.prewarmed` would tell an operator the
    optimisation is working while every stage transition silently stays cold (10.6s vs
    1.8s). A prewarm that didn't warm anything must not report success.
    """
    handler = _raise(failure) if failure else (lambda r: httpx.Response(404, text="model not found"))
    _ollama_http(monkeypatch, handler)

    with caplog.at_level(logging.INFO, logger="cerebrozen.llm.ollama"):
        ollama.OllamaClient().prewarm("S")   # must not raise

    assert "ollama.prewarm_failed" in caplog.text
    assert "ollama.prewarmed" not in caplog.text, "a failed prewarm must never log success"


# ═══════════════════════════════════════════════════════════════════════════════
# providers/mock.py — the keyless fallback
# ═══════════════════════════════════════════════════════════════════════════════


@pytest.fixture
def instant_mock_stream(monkeypatch):
    """The mock provider paces its fake stream with time.sleep. Real behaviour, but
    not something a test should sit through."""
    from app.llm.providers import mock as mock_mod

    monkeypatch.setattr(mock_mod, "time", types.SimpleNamespace(sleep=lambda s: None))


def test_the_mock_provider_emits_a_routable_control_envelope():
    """The mock is what runs on every keyless dev box and in CI. It is only useful if
    the GRAPH still routes on its output — a plain-prose reply would leave the nodes
    with no handoff_ready / coaching_path, and the reference graph could never be run
    end-to-end without spending money."""
    from app.llm.providers.mock import MockLLMClient

    resp = MockLLMClient().generate("SYS", "USER", "gpt-5", stage="core_coaching_agent")
    envelope = json.loads(resp.text)

    assert envelope["handoff_ready"] is False, "the mock must not loop the graph forward"
    assert envelope["coaching_path"] == "CIM", "routing must be deterministic offline"
    # Nodes read whichever user-facing key their own schema uses — all must be present.
    for key in ("response_to_user", "next_question", "clarifying_question", "message", "response"):
        assert envelope[key].strip()
    assert resp.cost_usd == 0.0 and resp.model == "mock", "an offline turn must cost nothing"
    assert resp.total_tokens == resp.prompt_tokens + resp.completion_tokens


def test_the_mock_provider_streams_the_same_envelope_it_would_have_returned(instant_mock_stream):
    """The streamed path must be byte-identical to the non-streamed one: the node's
    UserTextStreamer unwraps the JSON as it arrives, so a mock that streamed prose
    (or nothing) would exercise a code path that does not exist in production."""
    from app.llm.providers.mock import MockLLMClient

    tokens = []
    resp = MockLLMClient().generate_stream(
        "SYS", "USER", "gpt-5", on_token=tokens.append, stage="core_coaching_agent"
    )

    assert len(tokens) > 1, "the client must see a real token stream, not one blob"
    assert "".join(tokens) == resp.text
    assert json.loads("".join(tokens))["response_to_user"].startswith("[mock]"), (
        "a mock reply must be identifiable as one if it ever reaches a user"
    )


# ═══════════════════════════════════════════════════════════════════════════════
# greeting_generator.py + title_generator.py — degrade or die
# ═══════════════════════════════════════════════════════════════════════════════


class FakeLLM:
    """A scripted LLM. Each entry is either the text to return or an exception to
    raise, so a test can drive the exact reply sequence (junk → good) that the retry
    logic exists for. Records the user prompt of every call: the feedback the retry
    sends back to the model IS the behaviour under test."""

    def __init__(self, *replies):
        self.replies = list(replies)
        self.user_prompts = []
        self.system_prompts = []
        self.models = []

    def _next(self, system_prompt, user_prompt, model):
        self.user_prompts.append(user_prompt)
        self.system_prompts.append(system_prompt)
        self.models.append(model)
        reply = self.replies.pop(0) if self.replies else ""
        if isinstance(reply, BaseException):
            raise reply
        return reply

    def generate(self, system_prompt, user_prompt, model, **kw):
        return LLMResponse(text=self._next(system_prompt, user_prompt, model), model=model)

    def generate_stream(self, system_prompt, user_prompt, model, on_token=None, **kw):
        text = self._next(system_prompt, user_prompt, model)
        for i in range(0, len(text), 4):          # chunk it like a real stream
            on_token(text[i : i + 4])
        return LLMResponse(text=text, model=model)


@pytest.fixture
def llm(monkeypatch):
    """Install a scripted LLM at the generators' provider seam (the true external
    boundary), and pin the profile/Mongo reads so the greeting is deterministic."""

    def _install(*replies, profile=None, has_prior=False):
        fake = FakeLLM(*replies)
        for module in (gg, tg):
            monkeypatch.setattr(module, "get_client", lambda: fake, raising=True)
        monkeypatch.setattr(
            gg, "get_greeting_profile", lambda user_id: dict(profile or {})
        )
        monkeypatch.setattr(
            gg.conversation, "has_prior_sessions", lambda user_id: has_prior
        )
        return fake

    return _install


# ── greeting: the profile → prompt resolution ────────────────────────────────


def test_the_greeting_prompt_carries_the_users_real_name_and_local_time(llm):
    """Everything the greeting personalises on is resolved SERVER-side from the JWT's
    user_id. If the name or the time-of-day never reached the prompt, every user
    would get the same generic line and nobody would see an error."""
    fake = llm("Good morning, Alex, what's first", profile={"name": "Alex", "timezone": "UTC"})

    greeting = gg.generate_greeting("u1", time_of_day_override="morning")

    assert greeting == "Good morning, Alex, what's first"
    system = fake.system_prompts[0]
    assert "{userName}" not in system and "Alex" in system
    assert "{Time}" not in system and "morning" in system
    assert "{isFirstSession}" not in system
    assert fake.models[0] == config.GREETING_GENERATION_MODEL


def test_a_first_time_user_is_flagged_so_the_greeting_never_implies_history(llm):
    """'Welcome back!' to someone who has never opened the app is the kind of small
    lie that breaks trust in a coaching product. isFirstSession comes from the
    transcript store, and it has to reach the prompt."""
    fresh = llm("Alex, what's on your mind", profile={"name": "Alex"}, has_prior=False)
    gg.generate_greeting("u1")
    first_prompt = fresh.system_prompts[0]
    assert "IF true IS TRUE (brand-new user" in first_prompt

    returning = llm("Alex, what's on your mind", profile={"name": "Alex"}, has_prior=True)
    gg.generate_greeting("u1")
    returning_prompt = returning.system_prompts[0]

    assert "IF false IS FALSE (returning user)" in returning_prompt
    assert returning_prompt != first_prompt, (
        "a returning user must get a different prompt than a first-timer"
    )


def test_a_user_with_no_name_on_file_gets_no_placeholder_and_no_filler(llm):
    """Observed: substituting a filler ('there') reads to the model as the NAME, and
    it then 'varies its position' — producing 'Good morning there' or a literal
    '[Name]'. The no-name branch must instead tell the model to drop the name."""
    fake = llm("What shall we think through", profile={})

    greeting = gg.generate_greeting("u1", time_of_day_override="evening")

    assert greeting == "What shall we think through"
    assert gg._NO_NAME_OVERRIDE in fake.system_prompts[0]
    assert "{userName}" in fake.system_prompts[0], (
        "with no name, the token stays literal and the override tells the model to ignore it"
    )


@pytest.mark.parametrize(
    "profile,expected",
    [
        ({"timezone": "Asia/Kolkata"}, "Asia/Kolkata"),           # explicit IANA wins
        ({"timezone": "Mars/Olympus", "country": "IN"}, "Asia/Kolkata"),  # junk tz → country
        ({"country": "in"}, "Asia/Kolkata"),                      # lower-case ISO code
        ({"country": "ZZ"}, "UTC"),                               # unknown country → UTC
        ({}, "UTC"),                                              # nothing at all → UTC
    ],
)
def test_the_timezone_resolves_through_every_fallback(profile, expected):
    """A greeting that says 'Good morning' at 11pm is the most visible bug in the
    product. The `users` collection is inconsistent (some rows have localTimeZone,
    some only country, some neither) — every path has to land somewhere sane."""
    assert gg._resolve_timezone_name(profile) == expected


@pytest.mark.parametrize(
    "hour,expected",
    [(0, "late night"), (4, "late night"), (5, "morning"), (11, "morning"),
     (12, "afternoon"), (16, "afternoon"), (17, "evening"), (20, "evening"),
     (21, "late night"), (23, "late night")],
)
def test_the_time_of_day_buckets_are_computed_in_the_users_own_timezone(hour, expected):
    """Boundary hours are exactly where an off-by-one lands ('Good evening' at 4pm)."""
    from datetime import datetime, timezone as _tz

    utc_noon_ist = datetime(2026, 7, 14, hour, 0, tzinfo=_tz.utc)
    assert gg._time_of_day("UTC", now=utc_noon_ist) == expected


def test_an_unparseable_timezone_still_produces_a_time_of_day():
    """A corrupt tz string must degrade to UTC, not raise — the greeting is the FIRST
    thing the app renders, and an exception here is a blank home screen."""
    from datetime import datetime, timezone as _tz

    at_8am_utc = datetime(2026, 7, 14, 8, 0, tzinfo=_tz.utc)
    assert gg._time_of_day("Not/AZone", now=at_8am_utc) == "morning"


# ── greeting: cleaning the model's reply ─────────────────────────────────────


@pytest.mark.parametrize(
    "raw,expected",
    [
        ('"Good morning, Alex"', "Good morning, Alex"),      # wrapping quotes
        ("`Morning, Alex`", "Morning, Alex"),                # backticks
        ("  Morning, Alex \n", "Morning, Alex"),             # whitespace
        ("what's on your mind, Alex", "What's on your mind, Alex"),  # sentence case
        ("Alex — what's first today", "Alex, what's first today"),   # em dash → comma
        ("Alex – ready when you are", "Alex, ready when you are"),   # en dash → comma
        ("Alex, — what's first", "Alex, what's first"),      # no doubled comma
        ("Alex,—what's first", "Alex,what's first"),         # no ",," either
        ("", ""),
    ],
)
def test_the_greeting_is_cleaned_of_the_junk_models_add(raw, expected):
    """This line is rendered as a large headline. A stray quote, a leading lowercase,
    or an em dash (which the model kept emitting even after being told not to) all
    ship straight to the user's home screen."""
    assert gg._clean_greeting(raw) == expected


def test_an_over_long_greeting_is_DISCARDED_not_truncated(caplog):
    """Slicing at 55 chars would cut mid-word ('What shall we think thro') on the
    biggest text on the screen. A clean static fallback reads better than a
    mutilated clever one, so an over-cap reply is thrown away — and the caller's
    fallback/retry takes over."""
    long_line = "Good morning Alex, what feels like the most important thing today"
    assert len(long_line) > gg._MAX_GREETING_CHARS

    with caplog.at_level(logging.WARNING, logger="cerebrozen.greeting_generator"):
        assert gg._clean_greeting(long_line) == ""

    assert "greeting_generator.reply_too_long" in caplog.text

    # ...and the dash swap runs BEFORE the length check, so a reply is judged on its
    # post-cleanup length. Otherwise a reply that only overshoots because of
    # punctuation we are about to remove anyway gets thrown away for nothing.
    borderline = "Alex , — " + "a" * 47          # 56 raw chars → over the cap
    assert len(borderline) > gg._MAX_GREETING_CHARS
    assert gg._clean_greeting(borderline) == "Alex, " + "a" * 47   # 53 → kept


# ── greeting: the retry loop and the fallback ────────────────────────────────


def test_a_failing_llm_still_produces_a_usable_greeting(llm, caplog):
    """The home screen renders this line unconditionally. An exception here (OpenAI
    down, breaker open, bad key) must never show the user an empty header — it falls
    back to a plain 'Good {time}, {name}'."""
    llm(RuntimeError("openai is down"), profile={"name": "Alex"})

    with caplog.at_level(logging.WARNING, logger="cerebrozen.greeting_generator"):
        greeting = gg.generate_greeting("u1", time_of_day_override="afternoon")

    assert greeting == "Good afternoon, Alex"
    assert "greeting_generator.failed" in caplog.text


def test_the_fallback_has_no_dangling_comma_when_the_user_has_no_name(llm):
    """'Good afternoon,' with a trailing comma is the kind of detail that makes a
    product look broken."""
    llm(RuntimeError("down"), profile={})

    assert gg.generate_greeting("u1", time_of_day_override="afternoon") == "Good afternoon"


def test_an_over_cap_reply_is_retried_WITH_the_failure_fed_back(llm):
    """At reasoning_effort=minimal, gpt-5-nano is near-deterministic: retrying the
    IDENTICAL prompt was observed returning the byte-identical over-cap reply twice,
    burning both attempts. The retry must tell the model what went wrong, or it is
    not a retry at all."""
    too_long = "Good morning Alex, what feels like the most important thing today"
    fake = llm(too_long, "Morning, Alex, what's first", profile={"name": "Alex"})

    greeting = gg.generate_greeting("u1", time_of_day_override="morning")

    assert greeting == "Morning, Alex, what's first", "the second attempt must be used"
    assert fake.user_prompts[0] == gg._BASE_USER_PROMPT
    retry = fake.user_prompts[1]
    assert retry != fake.user_prompts[0], "a same-prompt retry reproduces the same failure"
    assert too_long in retry and str(gg._MAX_GREETING_CHARS) in retry


def test_a_hallucinated_name_is_rejected_and_retried(llm, caplog):
    """Observed live: the prompt injected the real username 'Lvk26' verbatim and the
    model returned a greeting for 'Pablo' — a more natural-sounding name belonging to
    nobody. Greeting a user by the WRONG name is worse than not greeting them."""
    fake = llm(
        "Pablo, what shall we think through",
        "Lvk26, what's on your mind",
        profile={"name": "Lvk26"},
    )

    with caplog.at_level(logging.WARNING, logger="cerebrozen.greeting_generator"):
        greeting = gg.generate_greeting("u1", time_of_day_override="morning")

    assert greeting == "Lvk26, what's on your mind"
    assert "greeting_generator.wrong_name" in caplog.text
    assert "Lvk26" in fake.user_prompts[1] and "Pablo" in fake.user_prompts[1]


def test_two_bad_attempts_fall_back_rather_than_retrying_forever(llm):
    """The retry budget is exactly one. An unbounded retry loop on a model stuck in a
    fixed point would hang the home screen instead of rendering a plain greeting."""
    fake = llm(
        "Pablo, what shall we think through",
        "Ringo, what's on your mind",
        "George, still here",           # never requested — proves we stopped at 2
        profile={"name": "Lvk26"},
    )

    greeting = gg.generate_greeting("u1", time_of_day_override="evening")

    assert greeting == "Good evening, Lvk26"
    assert len(fake.user_prompts) == gg._MAX_ATTEMPTS == 2


def test_the_greeting_stream_never_leaks_a_reply_it_is_about_to_discard(llm):
    """The load-bearing one for the streamed path: if a rejected attempt's tokens were
    forwarded as they arrived, the user would SEE the wrong-name (or over-long)
    greeting render on their home screen, and then watch it get replaced. Tokens are
    buffered and only flushed once an attempt has passed both checks."""
    fake = llm(
        "Pablo, what shall we think through",   # wrong name → discarded
        "Lvk26, what's on your mind",           # accepted
        profile={"name": "Lvk26"},
    )
    tokens = []

    greeting = gg.generate_greeting_stream("u1", tokens.append, time_of_day_override="morning")

    assert greeting == "Lvk26, what's on your mind"
    assert "".join(tokens) == "Lvk26, what's on your mind"
    assert "Pablo" not in "".join(tokens), "the discarded attempt must never reach the user"
    assert len(tokens) > 1, "the accepted reply must still arrive as a token stream"
    assert len(fake.user_prompts) == 2


def test_the_greeting_stream_emits_the_fallback_as_a_token_when_the_llm_dies(llm, caplog):
    """The caller is streaming — if the failure path returned the fallback but never
    emitted it, the client would render nothing at all and the return value would go
    to a caller that has already finished writing its response."""
    llm(RuntimeError("connection reset"), profile={"name": "Alex"})
    tokens = []

    with caplog.at_level(logging.WARNING, logger="cerebrozen.greeting_generator"):
        greeting = gg.generate_greeting_stream("u1", tokens.append, time_of_day_override="morning")

    assert greeting == "Good morning, Alex"
    assert tokens == ["Good morning, Alex"]
    assert "greeting_generator.stream_failed" in caplog.text


def test_the_greeting_stream_falls_back_when_every_attempt_is_junk(llm):
    """Both attempts over-cap → nothing to stream. The user still needs a header."""
    too_long = "Good morning Alex, what feels like the most important thing today"
    llm(too_long, too_long, profile={"name": "Alex"})
    tokens = []

    greeting = gg.generate_greeting_stream("u1", tokens.append, time_of_day_override="morning")

    assert greeting == "Good morning, Alex"
    assert tokens == ["Good morning, Alex"], "no partial junk, exactly one fallback token"


# ── titles ───────────────────────────────────────────────────────────────────


def test_a_chat_title_is_generated_from_the_users_first_message(llm):
    """The title is what the user scans their history by. It must be the model's
    short paraphrase, not the raw message."""
    fake = llm('"Preparing for a hard 1:1"')

    title = tg.generate_chat_title(
        "I have to give my report difficult feedback tomorrow and I'm dreading it",
        session_id="s1",
        user_id="u1",
    )

    assert title == "Preparing for a hard 1:1", "wrapping quotes are the model's, not the title's"
    assert fake.models[0] == config.TITLE_GENERATION_MODEL
    assert fake.user_prompts[0].startswith("I have to give my report")


def test_a_failing_llm_titles_the_session_with_the_users_own_message(llm, caplog):
    """Never a generic 'New conversation' placeholder: the user's own words are a
    better title than a label, and the sidebar must not fill with identical rows."""
    llm(RuntimeError("rate limited"))

    with caplog.at_level(logging.WARNING, logger="cerebrozen.title_generator"):
        title = tg.generate_chat_title("  I keep procrastinating  ", session_id="s1")

    assert title == "I keep procrastinating"
    assert "title_generator.failed" in caplog.text


def test_an_empty_or_junk_llm_reply_falls_back_to_the_message(llm):
    """A model that returns "" (or just quotes/whitespace) would otherwise persist an
    EMPTY title — an unclickable blank row in the history sidebar."""
    llm("   ")
    assert tg.generate_chat_title("I keep procrastinating") == "I keep procrastinating"

    llm('""')
    assert tg.generate_chat_title("I keep procrastinating") == "I keep procrastinating"


def test_a_blank_message_is_not_titled_at_all(llm):
    """No message, nothing to title — and no LLM call to pay for."""
    fake = llm("should never be asked for")

    assert tg.generate_chat_title("   ") == ""
    assert fake.user_prompts == [], "a blank message must not reach the LLM"


def test_a_runaway_title_is_capped(llm):
    """A 'title' of 500 chars breaks the sidebar layout. Both the model's reply and
    the user's-message fallback are capped."""
    llm("x" * 500)
    assert len(tg.generate_chat_title("something")) == tg._MAX_TITLE_CHARS

    llm(RuntimeError("down"))
    assert len(tg.generate_chat_title("y" * 500)) == tg._MAX_TITLE_CHARS


def test_title_generation_is_dispatched_off_the_request_path_and_persisted(llm, monkeypatch):
    """Titles are generated on a background thread, in parallel with the coaching
    agent's own LLM call, so they never add latency to the user's first reply. If the
    dispatch ever became synchronous, every new session would pay for two LLM calls
    before the first token streamed."""
    llm("Preparing for a hard 1:1")
    saved = {}
    done = threading.Event()

    def _capture(session_id, user_id, title):
        saved.update(session_id=session_id, user_id=user_id, title=title)
        done.set()
        return True

    monkeypatch.setattr(tg.conversation, "set_session_title", _capture)

    tg.dispatch_title_generation("u1", "s1", "  I have to give hard feedback  ")

    assert done.wait(10), "the background title job never persisted a title"
    assert saved == {"session_id": "s1", "user_id": "u1", "title": "Preparing for a hard 1:1"}


def test_dispatching_a_blank_message_does_no_work_at_all(llm, monkeypatch):
    """start_session fires this on EVERY new session, including empty/voice-opened
    ones. A blank message must not burn a thread, an LLM call, or write a blank title."""
    fake = llm("should never be asked for")
    calls = []
    monkeypatch.setattr(
        tg.conversation, "set_session_title", lambda *a, **k: calls.append(a)
    )

    tg.dispatch_title_generation("u1", "s1", "   ")

    assert calls == [] and fake.user_prompts == []
