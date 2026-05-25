#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QA Platform — One-shot production installer
#
# Brings a fresh (or just-cleaned) Ubuntu 22.04 server from zero to a running
# QA Platform behind Caddy + Let's Encrypt:
#
#   1. apt update + base packages
#   2. 4 GB swap (idempotent — skipped if already present)
#   3. UFW firewall (22/80/443 only)
#   4. Docker CE + compose plugin
#   5. .env generation with strong random secrets (saved once, persisted)
#   6. docker compose --profile prod build + up
#   7. Health-wait + final status report
#
# Idempotent: every step skips itself if it's already done. Safe to re-run.
#
# Usage:
#   bash deploy/install.sh                       # interactive prompts
#   DOMAIN=maieras.com ACME_EMAIL=me@x.com bash deploy/install.sh
#
# When run from inside an already-cloned repo (the normal flow), the repo
# you're running from IS the install target. When run outside a checkout,
# pass REPO_URL=... to have it clone for you.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Output helpers ─────────────────────────────────────────────────────────
B() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
G() { printf '\033[32m  ✓ %s\033[0m\n' "$*"; }
Y() { printf '\033[33m  ! %s\033[0m\n' "$*"; }
R() { printf '\033[31m  ✗ %s\033[0m\n' "$*"; }

# ── Config (env-overridable) ───────────────────────────────────────────────
DOMAIN="${DOMAIN:-maieras.com}"
ACME_EMAIL="${ACME_EMAIL:-admin@${DOMAIN}}"
REPO_URL="${REPO_URL:-}"
INSTALL_DIR="${INSTALL_DIR:-/opt/qa-platform}"
SWAP_SIZE="${SWAP_SIZE:-4G}"

# ── Pre-flight ─────────────────────────────────────────────────────────────
[[ $EUID -eq 0 ]] || { R "Run as root (sudo bash $0)"; exit 1; }

# If this script lives inside a git checkout, prefer THAT as the install
# target (the "you're already here" flow). Otherwise we'll clone REPO_URL
# into INSTALL_DIR below.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd 2>/dev/null || echo "")"
if [[ -f "$REPO_ROOT/docker-compose.yml" && -f "$REPO_ROOT/.env.example" ]]; then
    INSTALL_DIR="$REPO_ROOT"
    SOURCE="local-checkout"
else
    SOURCE="clone"
fi

cat <<EOF

╔══════════════════════════════════════════════════════════════════╗
║  QA Platform — Production Installer                               ║
║                                                                   ║
║  Domain        : $(printf '%-46s' "$DOMAIN") ║
║  ACME email    : $(printf '%-46s' "$ACME_EMAIL") ║
║  Install dir   : $(printf '%-46s' "$INSTALL_DIR") ║
║  Source        : $(printf '%-46s' "$SOURCE") ║
╚══════════════════════════════════════════════════════════════════╝

EOF

if [[ -t 0 ]]; then
    read -p "Proceed? [y/N] " ans
    [[ "$ans" =~ ^[yY] ]] || { Y "Aborted."; exit 0; }
fi

# ── Step 1: apt packages ───────────────────────────────────────────────────
B "Step 1/7: Installing base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl ca-certificates gnupg lsb-release git jq ufw vim htop openssl >/dev/null
G "base packages installed"

# ── Step 2: Swap ───────────────────────────────────────────────────────────
B "Step 2/7: Ensuring swap (${SWAP_SIZE})"
if swapon --show 2>/dev/null | grep -q '/swapfile'; then
    G "swapfile already active"
else
    fallocate -l "$SWAP_SIZE" /swapfile
    chmod 600 /swapfile
    mkswap -q /swapfile >/dev/null
    swapon /swapfile
    grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
    G "${SWAP_SIZE} swapfile created and enabled"
fi

# ── Step 3: UFW ────────────────────────────────────────────────────────────
B "Step 3/7: Configuring UFW firewall"
ufw --force reset >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp  comment 'SSH'      >/dev/null
ufw allow 80/tcp  comment 'HTTP'     >/dev/null
ufw allow 443/tcp comment 'HTTPS'    >/dev/null
ufw allow 443/udp comment 'HTTP/3'   >/dev/null
ufw --force enable >/dev/null
G "firewall: 22, 80, 443 allowed; everything else denied"

# ── Step 4: Docker ─────────────────────────────────────────────────────────
B "Step 4/7: Docker engine + compose plugin"
if command -v docker &>/dev/null && docker compose version &>/dev/null; then
    G "docker $(docker --version | awk '{print $3}' | tr -d ',') already installed"
else
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu jammy stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null
    systemctl enable --now docker
    G "docker installed and started"
fi

# ── Step 5: Source code ────────────────────────────────────────────────────
B "Step 5/7: Source code"
if [[ "$SOURCE" == "clone" ]]; then
    if [[ -d "$INSTALL_DIR" ]]; then
        G "$INSTALL_DIR already exists — git pull"
        cd "$INSTALL_DIR" && git pull --ff-only
    else
        [[ -n "$REPO_URL" ]] || { R "REPO_URL not set and no local checkout. Run with REPO_URL=... bash $0"; exit 1; }
        git clone "$REPO_URL" "$INSTALL_DIR"
        G "cloned to $INSTALL_DIR"
    fi
fi
cd "$INSTALL_DIR"

# ── Step 6: .env ───────────────────────────────────────────────────────────
B "Step 6/7: Environment configuration"
if [[ -f .env ]]; then
    G ".env already exists — keeping it (delete it manually if you want fresh secrets)"
else
    cp .env.example .env

    JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
    POSTGRES_PASSWORD=$(openssl rand -base64 24 | tr -d '\n=' | tr '/+' '_-')
    MINIO_ROOT_PASSWORD=$(openssl rand -base64 24 | tr -d '\n=' | tr '/+' '_-')
    ADMIN_PASSWORD=$(openssl rand -base64 16 | tr -d '\n=' | tr '/+' '_-')

    # Update values without breaking lines that contain $ or /
    _set() {
        local key="$1" val="$2"
        # Escape & and | for sed replacement
        local esc=$(printf '%s\n' "$val" | sed -e 's/[\/&|]/\\&/g')
        sed -i "s|^${key}=.*|${key}=${esc}|" .env
    }
    _set JWT_SECRET               "$JWT_SECRET"
    _set POSTGRES_PASSWORD        "$POSTGRES_PASSWORD"
    _set MINIO_ROOT_PASSWORD      "$MINIO_ROOT_PASSWORD"
    _set ADMIN_PASSWORD           "$ADMIN_PASSWORD"
    _set DOMAIN                   "$DOMAIN"
    _set ACME_EMAIL               "$ACME_EMAIL"
    _set CORS_ALLOWED_ORIGINS     "https://${DOMAIN},https://www.${DOMAIN}"
    _set MINIO_PUBLIC_URL         "https://${DOMAIN}/minio"
    _set APP_BRIDGE_PUBLIC_WS_URL "wss://${DOMAIN}/ws/agent"

    chmod 600 .env

    # Save a human-readable credential backup OUTSIDE the repo. Without
    # this, the admin password is buried in .env and harder to find when
    # someone needs to log in for the first time.
    BACKUP="/root/qa-platform-credentials.txt"
    cat > "$BACKUP" <<EOF_BACKUP
QA Platform — Production Credentials
Generated: $(date -Iseconds)
Server:    $(hostname)
URL:       https://${DOMAIN}

┌─ Admin login ────────────────────────────────────────────────
  username: admin
  password: ${ADMIN_PASSWORD}

┌─ Postgres (host: 127.0.0.1:5432) ─────────────────────────────
  user:     dp
  password: ${POSTGRES_PASSWORD}
  db:       qa_platform

┌─ MinIO root ──────────────────────────────────────────────────
  user:     change-me   (whatever MINIO_ROOT_USER is in .env)
  password: ${MINIO_ROOT_PASSWORD}

JWT_SECRET (do NOT share) lives in ${INSTALL_DIR}/.env

Rotate any of these by editing .env then:
  cd ${INSTALL_DIR} && docker compose --profile prod up -d
EOF_BACKUP
    chmod 600 "$BACKUP"
    G ".env generated with random secrets"
    G "credential backup saved to $BACKUP"
fi

# DNS sanity check (warn-only — never block install on it)
B "DNS sanity check"
SERVER_IP=$(curl -s -4 https://api.ipify.org 2>/dev/null || echo "")
DOMAIN_IP=$(dig +short +time=3 +tries=1 "$DOMAIN" @8.8.8.8 2>/dev/null | tail -1)
if [[ -z "$SERVER_IP" ]]; then
    Y "couldn't detect server public IP — skipping DNS check"
elif [[ -z "$DOMAIN_IP" ]]; then
    Y "$DOMAIN does not resolve yet. Set the A record before TLS will issue."
elif [[ "$SERVER_IP" != "$DOMAIN_IP" ]]; then
    Y "$DOMAIN resolves to $DOMAIN_IP, but this server is $SERVER_IP."
    Y "Caddy WILL fail to obtain a Let's Encrypt certificate until DNS matches."
else
    G "$DOMAIN → $SERVER_IP (matches this server)"
fi

# ── Step 7: Build + up ─────────────────────────────────────────────────────
B "Step 7/7: docker compose build (this can take 15–25 min on first run)"
docker compose --profile prod build

B "Bringing up the stack"
docker compose --profile prod up -d

# ── Health wait ────────────────────────────────────────────────────────────
B "Waiting for services to become healthy (up to 5 min)"
deadline=$(( $(date +%s) + 300 ))
while [[ $(date +%s) -lt $deadline ]]; do
    UNHEALTHY=$(docker compose ps --format json 2>/dev/null | \
        jq -r 'select(.Health != null and .Health != "" and .Health != "healthy") | .Name' 2>/dev/null | wc -l)
    TOTAL=$(docker compose ps --format json 2>/dev/null | jq -r 'select(.Health != null and .Health != "") | .Name' 2>/dev/null | wc -l)
    if [[ "$UNHEALTHY" == "0" && "$TOTAL" -gt 0 ]]; then
        G "all $TOTAL services with healthchecks report healthy"
        break
    fi
    printf '.'
    sleep 5
done
echo
docker compose ps

# ── Done ───────────────────────────────────────────────────────────────────
ADMIN_PWD_LINE=$(grep '^ADMIN_PASSWORD=' "$INSTALL_DIR/.env" | head -1 | cut -d= -f2-)

cat <<EOF

╔══════════════════════════════════════════════════════════════════╗
║  Installation complete                                            ║
║                                                                   ║
║  Console:  https://${DOMAIN}
║  API:      https://${DOMAIN}/api/actuator/health
║                                                                   ║
║  First login:                                                     ║
║    username: admin                                                ║
║    password: ${ADMIN_PWD_LINE}
║                                                                   ║
║  Full credential backup: /root/qa-platform-credentials.txt        ║
║                                                                   ║
║  Logs:     docker compose logs -f --tail=100                      ║
║  Status:   docker compose ps                                      ║
║  Update:   bash deploy/update.sh                                  ║
╚══════════════════════════════════════════════════════════════════╝

EOF
