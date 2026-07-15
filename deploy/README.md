# deploy — Production deployment

**Status: LIVE.** CerBroZen runs in production on a cloud VPS, reachable at
`https://cerebrozen.in` (+ `www`, `admin`, `api` subdomains). Caddy terminates
TLS (auto-HTTPS via Let's Encrypt) and reverse-proxies to the app/service
containers; every service except Caddy is internal to the compose network.

## Live environment

Concrete host, IP, and access credentials are **not published here** (this repo
is public) — they live in the private ops vault / password manager.

| Fact | Value |
|---|---|
| Host | Cloud VPS (Ubuntu 24.04) — address in the ops vault |
| Checkout | `$DEPLOY_PATH` (git clone of this repo, tracks `origin/main`) |
| Stack | `docker compose -f docker-compose.prod.yml --env-file .env.production` |
| Secrets | `$DEPLOY_PATH/.env.production` (git-ignored, `chmod 600`) |
| Project | compose project `cerebrozen` (7 services) |

`.env.production` on the host holds `DB_PASSWORD` + `JWT_SECRET` (generated on
the host, never reused) and `OPENAI_API_KEY`. The coaching engine boots but
stays LLM-degraded until a **real** OpenAI key is set there.

## Topology

| Domain | → | Service | Notes |
|---|---|---|---|
| `cerebrozen.in`, `www.` | → | `web:3000` | marketing site |
| `admin.cerebrozen.in` | → | `admin:3000` | HR portal + ops admin |
| `api.cerebrozen.in/engine/*` | → | `engine:8000` | coaching engine (prefix stripped) |
| `api.cerebrozen.in/*` | → | `platform:8100` | accounts, orgs, wellness, catalog |

`app.cerebrozen.in` is reserved for the future web employee app (Phase 5) and is
deliberately not proxied — no dangling subdomain until a service ships there.

The API split matches the Android release contract:
`API_BASE_URL=https://api.cerebrozen.in`, `ENGINE_BASE_URL=https://api.cerebrozen.in/engine`.

## Redeploy (ship a new `main`)

SSH to the host, then:

```
cd "$DEPLOY_PATH"
git fetch origin && git reset --hard origin/main
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
docker image prune -f
```

To minimise downtime, `... build` first (old containers keep serving), then
`... up -d` swaps them. Verify: `curl -fsS https://api.cerebrozen.in/health`.

## SSH access & the deploy key

Access is over SSH using a dedicated deploy key (`cerebrozen-deploy`, ed25519,
no passphrase); host, user, and password live in the ops vault. Install the
key's **public** half on the host once, then disable password login
(`PasswordAuthentication no`):

```
# on the host (paste the deploy public key):
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo 'ssh-ed25519 AAAA... cerebrozen-deploy' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Optional one-click deploys: store the deploy **private** key as the repo secret
`DEPLOY_SSH_KEY` (+ `DEPLOY_HOST`, `DEPLOY_USER`, var `DEPLOY_PATH`) and run the
`Deploy (prod)` GitHub Action (`.github/workflows/deploy.yml`).

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

## Still owned by a human

- **Rotate the root password** and move to key-only SSH (`PasswordAuthentication no`).
- Set a **real `OPENAI_API_KEY`** in the host's `.env.production`, then
  `docker compose ... up -d engine`.
- Rotate any OpenAI key / GitHub token ever pasted in chat or committed.
- Counsel sign-off on the terms/privacy pages and EU AI Act regulated-mode posture.
- Add real SMTP for the demo form if email delivery is wanted.
