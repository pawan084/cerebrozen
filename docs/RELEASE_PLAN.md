# CereBrozen — Release Plan

Production launch of the full stack: FastAPI backend + Postgres, the Next.js
landing site and admin, and the iOS app — on a Contabo VPS behind `cerebrozen.in`,
with the iOS app shipped via TestFlight → the App Store.

## Architecture (domain → service)

```
                      ┌────────────────────────── VPS 194.163.182.1 (Ubuntu 24.04) ──┐
cerebrozen.in ───┐    │  Caddy (:80/:443, auto-TLS)                                    │
www ─────────────┼──▶ │    ├─ cerebrozen.in / www  → web   (Next.js :3000)             │
admin ───────────┼──▶ │    ├─ admin.cerebrozen.in  → admin (Next.js :3001)             │
api ─────────────┼──▶ │    └─ api.cerebrozen.in    → api   (FastAPI :8000) ── db (pg)  │
mcp (reserved) ──┘    └───────────────────────────────────────────────────────────────┘
iOS app ─────────────────────────────────────────▶ https://api.cerebrozen.in
```

DNS is already set (A records `@`, `www`?, `admin`, `api`, `mcp` → 194.163.182.1).
**Add a `www` A record** if missing, and optionally AAAA records for the IPv6
(`2a02:c207:2341:217::1`).

---

## ⚠️ Phase 0 — Security first (do before anything else)

- [ ] **Rotate the Contabo password and VPS root password** — both were shared in
      chat; treat them as compromised.
- [ ] Replace weak root password; better, disable root SSH entirely (below).
- [ ] Generate a fresh **strong `SECRET_KEY`**, DB password, and admin password
      for `.env.production` (never reuse a shared value).

## Phase 1 — Server foundation

```bash
# as root, first login
adduser deploy && usermod -aG sudo deploy
rsync --archive ~/.ssh/authorized_keys /home/deploy/.ssh/    # or ssh-copy-id deploy@HOST
# harden SSH: /etc/ssh/sshd_config → PermitRootLogin no, PasswordAuthentication no
systemctl restart ssh

# firewall
ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw enable
# auto security updates + fail2ban
apt update && apt install -y unattended-upgrades fail2ban
dpkg-reconfigure -plow unattended-upgrades

# Docker + compose plugin
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy
```

## Phase 2 — DNS (verify)

- [ ] `dig +short cerebrozen.in api.cerebrozen.in admin.cerebrozen.in` → all `194.163.182.1`.
- [ ] Add `www` if absent. TTL 600 is fine for launch (lower to 300 while iterating).

## Phase 3 — Backend + web/admin deploy

```bash
# as deploy
git clone git@github.com:pawan084/cerebrozen.git && cd cerebrozen
cp backend/.env.production.example backend/.env.production
# fill in: strong SECRET_KEY (python -c "import secrets;print(secrets.token_urlsafe(48))"),
# POSTGRES_PASSWORD (match DATABASE_URL), ADMIN_PASSWORD, provider keys
# (OPENAI/DEEPGRAM/ELEVENLABS), GOOGLE_CLIENT_ID, SMTP_*, TWILIO_* as available.

docker compose -f docker-compose.prod.yml --env-file backend/.env.production up -d --build
```

What this does (already wired in `docker-compose.prod.yml` + `deploy/Caddyfile`):
- **Caddy** provisions Let's Encrypt certs for all four domains automatically
  (needs 80/443 open + DNS live — both done).
- **api** runs gunicorn (WEB_CONCURRENCY workers) + `prestart` applies Alembic
  migrations; Postgres is internal-only (no host port).
- **web/admin** are built with `NEXT_PUBLIC_API_URL=https://api.cerebrozen.in`
  baked in.

Verify:
- [ ] `curl https://api.cerebrozen.in/health` → `{"status":"ok"}`
- [ ] `https://cerebrozen.in` loads (waitlist posts to the API — CORS is set).
- [ ] `https://admin.cerebrozen.in` loads; log in with the admin creds.
- [ ] The production guard passed (it refuses to boot on weak secret / demo seed /
      wildcard CORS — if it booted, config is sound).

## Phase 4 — iOS production build

1. **Apple Developer** (developer.apple.com):
   - [ ] App ID `com.cerebrozen.app` with **Sign in with Apple** + **Push** enabled.
   - [ ] In Xcode → target → Signing & Capabilities → add **Sign in with Apple**.
2. **API URL** — already handled: Release builds default to `https://api.cerebrozen.in`
   (`APIClient.defaultBaseURL`, `#if DEBUG` local / else prod).
3. **App Store Connect**:
   - [ ] Create the app record (bundle id `com.cerebrozen.app`).
   - [ ] Create the two **auto-renewable subscriptions**:
     `com.cerebrozen.premium.monthly`, `com.cerebrozen.premiumhuman.monthly`.
   - [ ] Point **App Store Server Notifications V2** at
     `https://api.cerebrozen.in/webhooks/appstore`.
   - [ ] (Optional) put Apple's Root CA G3 PEM on the server and set
     `APPSTORE_ROOT_CERT_PATH` to pin the receipt chain.
4. **Ship it**:
   ```bash
   cd apps/ios
   export ASC_KEY_ID=… ASC_ISSUER_ID=… ASC_KEY_CONTENT="$(cat AuthKey_XXXX.p8)"
   bundle install && bundle exec fastlane ios beta      # → TestFlight
   ```
   Or add the `ASC_*` GitHub secrets and run the **TestFlight** workflow.

## Phase 5 — App Store submission

- [ ] Metadata: `apps/ios/fastlane/metadata/en-US` is drafted → `fastlane metadata`.
- [ ] Privacy nutrition labels per `docs/PRIVACY_LABELS.md`.
- [ ] Screenshots (6.9"/6.5") — generate from the app or the showcase frames.
- [ ] Age rating 17+; the in-app AI disclosure + crisis resources satisfy review
      scrutiny for a wellness app.
- [ ] Submit for review from TestFlight once a build is validated by real testers.

## Phase 6 — Post-launch hardening

- [ ] **Backups**: nightly `pg_dump` to off-box storage (cron + rotate), or
      Contabo snapshots. Test a restore.
- [ ] **Monitoring/uptime**: a healthcheck ping on `/health` (UptimeRobot/BetterStack).
- [ ] **Logs**: `docker compose logs -f`; consider shipping to a log service.
- [ ] **CI/CD deploy**: a GitHub Action on push to `main` that SSHes to the VPS and
      `docker compose … up -d --build` (add `DEPLOY_SSH_KEY` secret). Optional.
- [ ] **Providers** (as budget allows): SMTP (email verify/reset), Twilio (SMS),
      Google OAuth client id. Each degrades gracefully until set.
- [ ] Lower DNS TTLs back up (3600) once stable.

## Rollback

`docker compose -f docker-compose.prod.yml down` keeps the `pgdata` volume; redeploy
a previous commit with the same command. DB migrations are additive — for a bad
migration, restore the latest `pg_dump`.

## Release checklist (tl;dr)

1. Rotate shared credentials · harden VPS · install Docker.
2. Verify DNS (add `www`).
3. Fill `.env.production` · `docker compose -f docker-compose.prod.yml up -d --build`.
4. Verify `api./`, `/`, `admin.` over HTTPS.
5. Apple Developer + App Store Connect (App ID, SIWA, subscriptions, notifications URL).
6. `fastlane ios beta` → TestFlight → submit.
7. Backups + monitoring.
