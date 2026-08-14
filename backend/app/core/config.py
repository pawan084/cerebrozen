import logging
from functools import lru_cache

import os

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger("cerebro.config")

# Values that must never survive into a production deployment.
_INSECURE_SECRETS = {"dev-secret", "change-me-to-a-long-random-string", "ci-secret", ""}
_DEFAULT_ADMIN_PASSWORD = "admin12345"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    env: str = "development"
    secret_key: str = "dev-secret"
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 30
    algorithm: str = "HS256"
    log_level: str = "INFO"
    # Gunicorn/uvicorn worker count (prod). Read by the container start command.
    web_concurrency: int = 1

    database_url: str = "postgresql+asyncpg://cerebro:cerebro@localhost:5432/cerebro"
    database_pool_size: int = Field(default=5, ge=1, le=50)
    database_max_overflow: int = Field(default=5, ge=0, le=50)
    database_pool_timeout: int = Field(default=30, ge=1, le=120)

    # Comma-separated string (env: CORS_ORIGINS); parsed via `cors_origins` below.
    cors_origins_raw: str = Field(
        default="http://localhost:3000,http://localhost:3001,http://localhost:3002,http://localhost:3003",
        validation_alias="CORS_ORIGINS",
    )
    trusted_hosts_raw: str = Field(
        default="localhost,127.0.0.1,testserver",
        validation_alias="TRUSTED_HOSTS",
    )

    #: How many reverse proxies sit in front of the API, and therefore how many
    #: trailing `X-Forwarded-For` entries were appended by infrastructure we run
    #: rather than typed by the caller. `core/ratelimit.client_ip` counts back
    #: this many hops from the END of the header; anything earlier is
    #: attacker-controlled and must never be keyed on. `deploy/Caddyfile`'s
    #: topology (Caddy → api) is 1; a CDN in front of Caddy makes it 2.
    #:
    #: **Defaults to 0 — trust the socket, ignore the header** — because the two
    #: ways to get this wrong fail very differently. Set too high, the limiter
    #: reads a hop the caller supplied and every request can mint its own bucket:
    #: silent, and the exact bug this setting exists to close. Set too low, it
    #: keys real users onto a shared proxy address and they collect 429s: loud,
    #: and someone reports it within the hour. So the default is the one that
    #: cannot be quietly wrong on a box nobody remembered to configure, and
    #: `_guard_production` below refuses to boot production until it is declared.
    trusted_proxy_hops: int = Field(default=0, ge=0, le=10)

    # AI / proactive. Provider is chosen at runtime: OpenAI when its key is set,
    # else Anthropic when its key is set, else deterministic local fallbacks.
    anthropic_api_key: str = ""
    ai_model: str = "claude-opus-4-8"
    openai_api_key: str = ""
    openai_model: str = "gpt-4o"

    # LangGraph "Oracle" agent (tool-calling + confirm-before-write + streaming).
    # Off by default; the deterministic /chat router is the always-on fallback.
    oracle_enabled: bool = False

    # Free-tier server-side quota: messages/day before a paywall (429). Premium
    # tiers are unlimited. Enforced in /chat and /oracle.
    free_daily_messages: int = 50

    # StoreKit 2 server-side receipt validation. Path to Apple's Root CA (G3) PEM.
    # When set, the transaction cert chain is pinned to it; empty = verify the
    # chain internally but skip pinning (dev). Bundle-id the transactions must match.
    appstore_root_cert_path: str = ""
    appstore_bundle_id: str = "com.cerebrozen.app"

    # Voice (Deepgram = speech-to-text, ElevenLabs = text-to-speech). Leave the
    # keys blank to disable the matching half of the voice loop.
    deepgram_api_key: str = ""
    deepgram_model: str = "nova-2"
    elevenlabs_api_key: str = ""
    elevenlabs_voice_id: str = "EXAVITQu4vr4xnSDxMaL"  # "Sarah" — calm, warm
    elevenlabs_model: str = "eleven_turbo_v2_5"

    # Generated media (narration MP3s) live here, served read-only at /media.
    # Relative to the working dir (/app in-container); prod mounts a named volume.
    media_root: str = "media"
    # How long a minted narration URL stays playable. Long enough to start a
    # sleep story and seek around inside it; short enough that a URL pasted
    # somewhere public stops working on its own.
    media_token_ttl_hours: int = 12

    # Sign in with Apple. `apple_client_id` is the token *audience* — your app's
    # bundle ID (or Services ID for web). Defaults to the APNs bundle id below.
    # Verification is always attempted; a bad/foreign token simply fails to 401.
    apple_client_id: str = ""
    # Web Sign in with Apple (apps/app) uses a separate Services ID audience.
    apple_services_client_id: str = ""

    # Sign in with Google. `google_client_id` is the OAuth client ID (the token
    # *audience*). Leave empty to skip audience checks (dev); set it in production.
    google_client_id: str = ""

    # In-process nudge delivery loop (minutes between dispatch passes; 0 = off,
    # e.g. when an external cron calls POST /admin/nudges/dispatch instead).
    nudge_dispatch_interval_minutes: int = 5

    # Web Push (VAPID) for browser nudges (apps/app). The keypair is
    # self-generated (`python -m py_vapid --gen` or `npx web-push
    # generate-vapid-keys`) — no third-party account. Empty keys = the web
    # client hides its notifications toggle and delivery logs instead of sends.
    vapid_public_key: str = ""
    vapid_private_key: str = ""
    vapid_subject: str = "mailto:support@cerebrozen.in"

    # APNs (token-based push). Leave key path empty to log instead of send.
    apns_key_path: str = ""  # path to the .p8 auth key
    apns_key_id: str = ""
    apns_team_id: str = ""
    apns_bundle_id: str = "com.cerebrozen.app"
    apns_use_sandbox: bool = True

    # FCM (HTTP v1) for Android push. Empty credentials path = log instead of
    # send, same contract as APNs — the Android client still registers its
    # token, so turning delivery on later needs no app release.
    fcm_credentials_path: str = ""  # path to the service-account .json
    fcm_project_id: str = ""

    # Transactional email (verification, password reset). Empty host = log only.
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_from: str = "CereBro <no-reply@cerebro.app>"
    smtp_tls: bool = True
    # Base URL for links embedded in emails (verification / reset).
    app_base_url: str = "https://cerebro.app"
    # Operational alert inbox for crisis safety events (empty = log only).
    ops_alert_email: str = ""

    # SMS (Twilio) for trusted-contact phone delivery. Empty SID = log only.
    twilio_account_sid: str = ""
    twilio_auth_token: str = ""
    twilio_from: str = ""

    # Stripe web billing (apps/app). Empty secret = /billing/checkout 503s and
    # the webhook rejects; the App Store flow is unaffected. Price ids map to
    # the same subscription_tier contract as services/appstore.py.
    stripe_secret_key: str = ""
    stripe_webhook_secret: str = ""
    stripe_price_premium_monthly: str = ""
    stripe_price_premium_annual: str = ""
    stripe_price_premium_human_monthly: str = ""
    stripe_price_premium_human_annual: str = ""
    stripe_return_url: str = "https://app.cerebrozen.in/account"

    # Seed
    seed_demo_data: bool = True
    admin_email: str = "admin@cerebro.app"
    admin_password: str = "admin12345"

    @property
    def is_production(self) -> bool:
        return self.env.lower() in {"production", "prod"}

    @property
    def cors_origins(self) -> list[str]:
        return [o.strip() for o in self.cors_origins_raw.split(",") if o.strip()]

    @property
    def trusted_hosts(self) -> list[str]:
        return [
            host.strip() for host in self.trusted_hosts_raw.split(",") if host.strip()
        ]

    @model_validator(mode="after")
    def _guard_production(self) -> "Settings":
        """Fail fast on insecure defaults when ENV=production, so a misconfigured
        deploy never boots with a known secret or the demo admin password."""
        if not self.is_production:
            return self
        problems = []
        if (
            self.secret_key in _INSECURE_SECRETS
            or len(self.secret_key) < 32
            or "CHANGE_ME" in self.secret_key.upper()
        ):
            problems.append("SECRET_KEY must be a strong (>=32 char) random value")
        if (
            self.admin_password == _DEFAULT_ADMIN_PASSWORD
            or "CHANGE_ME" in self.admin_password.upper()
        ):
            problems.append("ADMIN_PASSWORD must be set to a real value")
        if self.seed_demo_data:
            problems.append("SEED_DEMO_DATA must be false in production")
        if os.getenv("RATE_LIMIT_ENABLED", "1") in ("0", "false", "False"):
            problems.append("RATE_LIMIT_ENABLED must not be off in production")
        if self.trusted_proxy_hops < 1:
            # Production always serves through Caddy. At 0 the limiter ignores
            # X-Forwarded-For and keys every request on the proxy's own address,
            # so the whole internet shares one bucket and real users start
            # collecting 429s. A rate limiter that is on but wrong is worse than
            # one that is off, because nothing looks broken.
            problems.append("TRUSTED_PROXY_HOPS must be >= 1 in production (the API runs behind Caddy)")
        if "*" in self.cors_origins_raw:
            problems.append("CORS_ORIGINS must list explicit origins (no wildcard)")
        if not self.trusted_hosts or "*" in self.trusted_hosts:
            problems.append("TRUSTED_HOSTS must list explicit hosts (no wildcard)")
        if problems:
            raise ValueError("Insecure production config: " + "; ".join(problems))
        return self

    @property
    def ai_provider(self) -> str:
        if self.openai_api_key:
            return "openai"
        if self.anthropic_api_key:
            return "anthropic"
        return "none"

    @property
    def ai_enabled(self) -> bool:
        return self.ai_provider != "none"

    @property
    def oracle_available(self) -> bool:
        """The agent needs both the flag and a real LLM key."""
        return self.oracle_enabled and self.ai_enabled

    @property
    def stt_enabled(self) -> bool:
        return bool(self.deepgram_api_key)

    @property
    def tts_enabled(self) -> bool:
        return bool(self.elevenlabs_api_key)

    @property
    def apns_enabled(self) -> bool:
        return bool(self.apns_key_path and self.apns_key_id and self.apns_team_id)

    @property
    def fcm_enabled(self) -> bool:
        return bool(self.fcm_credentials_path and self.fcm_project_id)

    @property
    def webpush_enabled(self) -> bool:
        return bool(self.vapid_public_key and self.vapid_private_key)

    @property
    def stripe_enabled(self) -> bool:
        return bool(self.stripe_secret_key)

    @property
    def apple_audience(self) -> str:
        """Expected `aud` for Apple identity tokens (falls back to the bundle id)."""
        return self.apple_client_id or self.apns_bundle_id


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
