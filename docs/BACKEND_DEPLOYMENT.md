# Backend production deployment

This runbook deploys the single-VPS Docker Compose stack without exposing
PostgreSQL. Run it only after the read-only server audit confirms that ports 80
and 443 are available and the listed DNS names resolve to the VPS.

## Required configuration

Copy `backend/.env.production.example` to the ignored
`backend/.env.production` on the server. Keep mode `0600`. Required secret names
are `SECRET_KEY`, `POSTGRES_PASSWORD`, `DATABASE_URL`, and `ADMIN_PASSWORD`.
Provider variables may remain empty to disable their corresponding feature.

Never print, commit, or transfer the production environment file through CI
logs. Existing values must be retained unless their rotation is explicitly
approved.

## Backup and deploy

From `/opt/cerebrozen`:

```bash
set -euo pipefail
stamp=$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 700 backups
cp --preserve=mode,timestamps backend/.env.production "backups/env.production.${stamp}"
docker compose -f docker-compose.prod.yml --env-file backend/.env.production exec -T db \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "backups/postgres.${stamp}.dump"
git fetch origin main
git merge --ff-only origin/main
docker compose -f docker-compose.prod.yml --env-file backend/.env.production build
docker compose -f docker-compose.prod.yml --env-file backend/.env.production \
  --profile tools run --rm migrate
docker compose -f docker-compose.prod.yml --env-file backend/.env.production \
  up -d --no-build
```

The migration command is deliberately separate from API startup. If it fails,
do not start the new containers. Diagnose the migration and leave the running
release untouched.

Verify locally on the server and through the public TLS endpoint:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --since=10m api caddy
curl -fsS http://localhost/health -H 'Host: api.cerebrozen.in'
curl -fsS https://api.cerebrozen.in/ready
curl -fsS https://api.cerebrozen.in/api/v1/health
```

## Rollback

Record the previous Git commit and image IDs before deploying. A code-only
rollback uses the recorded commit and previous images, without deleting named
volumes.

Database rollback is not automatic. Alembic downgrades and database restores
can destroy newer data and require explicit approval. If a restore is approved:

1. Stop application writes while leaving unrelated services untouched.
2. Preserve another dump of the current database.
3. Validate the selected dump and matching application commit.
4. Restore into a new database first and run readiness/smoke checks against it.
5. Switch the application only after verification; retain the former database
   until the rollback is accepted.

Named volumes `pgdata`, `media`, `caddy_data`, and `caddy_config` must never be
removed during a routine deploy or rollback.
