# CereBro API

FastAPI + Postgres backend for the CereBro mental-wellness app. Provides auth and
user-data storage (mood, journal, chat, plans, consent) plus four **proactive**
capabilities:

| Capability            | Where                          | AI? | Fallback |
|-----------------------|--------------------------------|-----|----------|
| Agentic daily plan    | `services/agentic.py`          | ✅  | curated templates by goal |
| Push nudges           | `services/nudges.py` + `notifications.py` | – | token-based APNs (HTTP/2); logs when creds unset |
| Weekly insights       | `services/insights.py`         | –   | computed from activity |
| Crisis/safety detect  | `services/safety.py`           | ✅  | keyword heuristic |
| Voice loop            | `services/voice.py`            | ✅  | Deepgram STT + ElevenLabs TTS (disabled if keys unset) |

> Every AI/voice call degrades gracefully. The LLM provider is chosen at runtime
> (`OPENAI_API_KEY` → OpenAI, else `ANTHROPIC_API_KEY` → Anthropic, else local
> fallbacks), so the whole API still runs with no keys at all.

## Run it

```bash
cd <repo root>            # the folder containing docker-compose.yml
cp backend/.env.example backend/.env   # already present in dev
docker compose up --build
```

- API → http://localhost:8000
- Interactive docs → http://localhost:8000/docs
- Health → http://localhost:8000/health

On boot the API waits for Postgres, creates tables (`init_db`), and seeds an
admin + demo user + content catalogue.

**Seeded logins**
- Admin: `admin@cerebro.app` / `admin12345`
- Demo:  `pawan@cerebro.app` / `demo12345`

## Tests

```bash
docker compose up -d db
docker compose run --rm api sh -c "pip install -r requirements-dev.txt && pytest -q"
```

## Layout

```
app/
  core/      config, async DB, JWT security, deps
  models/    SQLAlchemy models (users, moods, journal, chat, plans, content,
             nudges, insights, safety, consent)
  schemas/   Pydantic request/response models
  services/  ai, agentic, safety, voice, insights, nudges, notifications
  api/routes/ auth, users, moods, journal, chat, plans, content, insights, voice, admin
  main.py    app factory + CORS + /health
  prestart.py  wait-for-db → create tables → seed
  seed.py
```

## Key endpoints

| Method | Path | Notes |
|--------|------|-------|
| POST | `/auth/signup` `/auth/login` `/auth/refresh` | JWT access+refresh |
| GET/PATCH | `/users/me`, `/users/me/consent` | profile + privacy |
| PUT | `/users/me/push-token` | register device for nudges |
| GET/POST | `/moods` | logging a rough mood may queue a supportive nudge |
| GET/POST/DELETE | `/journal` | POST runs the safety scan |
| GET/POST | `/chat`, `/chat/messages` | companion reply (Calm Guide / Scientific) |
| GET | `/plans/active` · POST `/plans/generate` · PATCH `/plans/steps/{id}` | agentic plan |
| GET | `/content` | public catalogue (filter by `kind`, `q`) |
| GET | `/insights/weekly`, `/nudges` | proactive surfaces |
| GET/POST | `/voice/status` · `/voice/stt` · `/voice/tts` | speech-to-text + text-to-speech |
| * | `/admin/*` | stats, users, content CRUD, safety queue (admin only) |

## Migrations (Alembic)

The schema is managed by Alembic; `prestart` runs `alembic upgrade head` on boot
(falling back to `create_all` only if migrations can't run).

```bash
# after changing models:
docker compose run --rm api alembic revision --autogenerate -m "describe change"
docker compose run --rm api alembic upgrade head
```

## Push (APNs)

`notifications.send_push` sends token-based APNs over HTTP/2 when these are set
(see `.env.example`): `APNS_KEY_PATH` (.p8), `APNS_KEY_ID`, `APNS_TEAM_ID`,
`APNS_BUNDLE_ID`, `APNS_USE_SANDBOX`. Without them it logs the payload. Run the
due-nudge sweep from a cron/worker via `POST /admin/nudges/dispatch`.

## Production notes
- Set a strong `SECRET_KEY` and an LLM key (`OPENAI_API_KEY` or `ANTHROPIC_API_KEY`).
- Set `DEEPGRAM_API_KEY` + `ELEVENLABS_API_KEY` to enable the voice loop.
- Mount the APNs `.p8` key and set the APNS_* vars to enable real push.
- Keep all secrets in `backend/.env` (git-ignored) or your secret manager — never commit them.
