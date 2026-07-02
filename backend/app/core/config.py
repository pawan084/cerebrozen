import logging
from functools import lru_cache

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

    # Comma-separated string (env: CORS_ORIGINS); parsed via `cors_origins` below.
    cors_origins_raw: str = Field(
        default="http://localhost:3000,http://localhost:3001",
        validation_alias="CORS_ORIGINS",
    )

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

    # Sign in with Apple. `apple_client_id` is the token *audience* — your app's
    # bundle ID (or Services ID for web). Defaults to the APNs bundle id below.
    # Verification is always attempted; a bad/foreign token simply fails to 401.
    apple_client_id: str = ""

    # Sign in with Google. `google_client_id` is the OAuth client ID (the token
    # *audience*). Leave empty to skip audience checks (dev); set it in production.
    google_client_id: str = ""

    # In-process nudge delivery loop (minutes between dispatch passes; 0 = off,
    # e.g. when an external cron calls POST /admin/nudges/dispatch instead).
    nudge_dispatch_interval_minutes: int = 5

    # APNs (token-based push). Leave key path empty to log instead of send.
    apns_key_path: str = ""        # path to the .p8 auth key
    apns_key_id: str = ""
    apns_team_id: str = ""
    apns_bundle_id: str = "com.cerebrozen.app"
    apns_use_sandbox: bool = True

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
        if self.admin_password == _DEFAULT_ADMIN_PASSWORD or "CHANGE_ME" in self.admin_password.upper():
            problems.append("ADMIN_PASSWORD must be set to a real value")
        if self.seed_demo_data:
            problems.append("SEED_DEMO_DATA must be false in production")
        if "*" in self.cors_origins_raw:
            problems.append("CORS_ORIGINS must list explicit origins (no wildcard)")
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
    def apple_audience(self) -> str:
        """Expected `aud` for Apple identity tokens (falls back to the bundle id)."""
        return self.apple_client_id or self.apns_bundle_id


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
