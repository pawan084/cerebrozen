# deploy — Production deployment

Caddy terminates TLS (auto-HTTPS via Let's Encrypt) and reverse-proxies the
`cerebrozen.in` subdomains to the app/service containers; every service except
Caddy is internal to the compose network (no published ports).

## Topology

| Domain | → | Service | Notes |
|---|---|---|---|
| `cerebrozen.in`, `www.` | → | `web:3000` | marketing site |
| `admin.cerebrozen.in` | → | `admin:3000` | HR portal + ops admin |
| `api.cerebrozen.in/engine/*` | → | `engine:8000` | coaching engine (prefix stripped) |
| `api.cerebrozen.in/*` | → | `platform:8100` | accounts, orgs, wellness, catalog |

`app.cerebrozen.in` is reserved for the future web employee app (Phase 5) and is
deliberately not proxied — no dangling subdomain until a service ships there.

The two-backends-behind-one-domain split matches the Android release contract:
`API_BASE_URL=https://api.cerebrozen.in`, `ENGINE_BASE_URL=https://api.cerebrozen.in/engine`.

## Deploy

1. **DNS** — point A-records for `cerebrozen.in`, `www`, `admin`, `api` at the host.
   Open ports **80** and **443** (Caddy needs 80 for the ACME HTTP challenge).
2. **Secrets** — `cp .env.production.example .env.production` and fill in real,
   freshly-generated values (see the comments in that file). Never reuse a dev or
   reference-project secret. `.env.production` is git-ignored.
3. **Up:**
   ```
   docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
   ```
4. Caddy provisions certs on first request to each domain (needs DNS + 80/443 live).

## What `ENV=production` turns on (fail-fast guards)

- **Engine** refuses to boot with wildcard CORS — `CEREBROZEN_CORS_ORIGINS` must
  list the browser app origins (set in the compose file to the admin/app domains).
- **Platform** `guard_production()` refuses the dev admin seed and demands a real
  `JWT_SECRET`; `CEREBROZEN_SEED_DEV_ADMIN=false` is set.
- **Auth is enforced** on every session/engine route.
- A missing required secret (`DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`) fails
  the boot with a named error rather than starting on a silent default.

## Persistence & ops

- `pg-data` (Postgres) and `caddy-data` (TLS certs + ACME account) are named
  volumes — **back these up**; losing `caddy-data` re-triggers ACME rate limits.
- First boot creates the `cerebrozen_platform` database via `deploy/initdb/`.
- Regulated-workplace mode stays ON by default; turning it off is a contract-level
  decision with counsel, not an env flag flipped in a hurry.

## Still owned by a human before go-live

Rotate any OpenAI key ever pasted in chat or committed; get counsel to sign off
the terms/privacy pages and the EU AI Act regulated-mode posture; add real SMTP
for the demo form if email delivery is wanted.
