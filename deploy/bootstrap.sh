#!/usr/bin/env bash
#
# CereBrozen — first-time production bootstrap for the Contabo VPS (Ubuntu 24.04).
# Idempotent: safe to re-run. Run as root from inside a checkout of the repo:
#
#   # 1) give the server read access to the private repo (one time):
#   ssh-keygen -t ed25519 -f ~/.ssh/gh_cerebrozen -N ""
#   cat ~/.ssh/gh_cerebrozen.pub            # → add as a *Deploy key* on the GitHub repo
#   printf 'Host github.com\n  IdentityFile ~/.ssh/gh_cerebrozen\n' >> ~/.ssh/config
#   # 2) clone + run:
#   git clone git@github.com:pawan084/cerebrozen.git /opt/cerebrozen
#   bash /opt/cerebrozen/deploy/bootstrap.sh
#
# Optional: bootstrap.sh --harden-ssh   also disables root + password SSH
#           (only after confirming the deploy user has an authorized key).
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-deploy}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$REPO_DIR/backend/.env.production"
HARDEN_SSH=false
[[ "${1:-}" == "--harden-ssh" ]] && HARDEN_SSH=true

log()  { printf "\033[1;35m▶ %s\033[0m\n" "$*"; }
warn() { printf "\033[1;33m⚠ %s\033[0m\n" "$*"; }

[[ $EUID -eq 0 ]] || { echo "Run as root (sudo)."; exit 1; }

# ── 1. System packages ───────────────────────────────────────────────────
log "Updating system + installing base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq ca-certificates curl git ufw fail2ban unattended-upgrades openssl
dpkg-reconfigure -f noninteractive -plow unattended-upgrades || true

# ── 2. Non-root deploy user ──────────────────────────────────────────────
if ! id "$DEPLOY_USER" &>/dev/null; then
  log "Creating user '$DEPLOY_USER'"
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
  usermod -aG sudo "$DEPLOY_USER"
  # Carry over root's authorized_keys so key-based login works immediately.
  if [[ -f /root/.ssh/authorized_keys ]]; then
    install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
    install -m 600 -o "$DEPLOY_USER" -g "$DEPLOY_USER" \
      /root/.ssh/authorized_keys "/home/$DEPLOY_USER/.ssh/authorized_keys"
  fi
else
  log "User '$DEPLOY_USER' already exists"
fi

# ── 3. Firewall ──────────────────────────────────────────────────────────
log "Configuring ufw (OpenSSH, 80, 443)"
ufw allow OpenSSH >/dev/null
ufw allow 80/tcp   >/dev/null
ufw allow 443/tcp  >/dev/null
ufw --force enable  >/dev/null
systemctl enable --now fail2ban >/dev/null 2>&1 || true

# ── 4. Docker + compose plugin ───────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  log "Installing Docker"
  curl -fsSL https://get.docker.com | sh
else
  log "Docker already installed"
fi
usermod -aG docker "$DEPLOY_USER"
systemctl enable --now docker >/dev/null 2>&1 || true

# ── 5. Repo ownership (so the deploy user + CD can pull) ─────────────────
log "Handing the checkout to '$DEPLOY_USER' ($REPO_DIR)"
chown -R "$DEPLOY_USER":"$DEPLOY_USER" "$REPO_DIR"

# ── 6. .env.production (auto-generate the security-critical secrets) ──────
if [[ ! -f "$ENV_FILE" ]]; then
  log "Creating $ENV_FILE from the template with generated secrets"
  cp "$REPO_DIR/backend/.env.production.example" "$ENV_FILE"
  SECRET_KEY="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 60)"
  DB_PW="$(openssl rand -hex 24)"
  ADMIN_PW="$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 20)"
  sed -i "s|^SECRET_KEY=.*|SECRET_KEY=$SECRET_KEY|" "$ENV_FILE"
  sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$DB_PW|" "$ENV_FILE"
  sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$ADMIN_PW|" "$ENV_FILE"
  sed -i "s|postgresql+asyncpg://cerebro:[^@]*@db|postgresql+asyncpg://cerebro:$DB_PW@db|" "$ENV_FILE"
  chown "$DEPLOY_USER":"$DEPLOY_USER" "$ENV_FILE"; chmod 600 "$ENV_FILE"
  warn "Generated ADMIN_PASSWORD for admin@cerebrozen.in: $ADMIN_PW  (save it now)"
  warn "Now add your provider keys (OPENAI/DEEPGRAM/ELEVENLABS, GOOGLE_CLIENT_ID,"
  warn "SMTP_*, TWILIO_*) to $ENV_FILE — blank ones just disable that feature."
  echo
  read -r -p "Press Enter to deploy now, or Ctrl-C to edit the env first… " _
else
  log "$ENV_FILE already present — leaving it as-is"
fi

# ── 7. Deploy the stack ──────────────────────────────────────────────────
log "Building + starting the production stack (this pulls images + builds)"
cd "$REPO_DIR"
sudo -u "$DEPLOY_USER" docker compose -f docker-compose.prod.yml \
  --env-file backend/.env.production up -d --build

# ── 8. Optional SSH hardening (opt-in, lockout-safe) ─────────────────────
if $HARDEN_SSH; then
  if [[ -s "/home/$DEPLOY_USER/.ssh/authorized_keys" ]]; then
    log "Hardening SSH: disabling root login + password auth"
    sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
    sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
    systemctl restart ssh
    warn "Password + root SSH are now OFF. Keep your key for '$DEPLOY_USER' safe."
  else
    warn "Skipped SSH hardening: no authorized_keys for '$DEPLOY_USER'."
    warn "Add your key first:  ssh-copy-id $DEPLOY_USER@<host>  then re-run with --harden-ssh"
  fi
fi

# ── Done ─────────────────────────────────────────────────────────────────
log "Bootstrap complete."
echo "  • Caddy is provisioning TLS for cerebrozen.in / www / admin. / api."
echo "  • Verify:  curl https://api.cerebrozen.in/health"
echo "  • For the GitHub Deploy workflow, set:"
echo "        DEPLOY_HOST=<server ip/host>   DEPLOY_USER=$DEPLOY_USER"
echo "        DEPLOY_SSH_KEY=<private key>   (var) DEPLOY_PATH=$REPO_DIR"
echo "  • Logs:  docker compose -f docker-compose.prod.yml logs -f"
