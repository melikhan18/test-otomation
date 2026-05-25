#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QA Platform — One-shot update
#
# Pulls the latest main, rebuilds containers, recreates them in place. Existing
# data in qp_pgdata + qp_miniodata + qp_caddydata is preserved (we never use
# `down -v`). Caddy keeps its TLS certs across this.
#
# Usage (as root, from anywhere):
#   bash deploy/update.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-/opt/qa-platform}"

B() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
G() { printf '\033[32m  ✓ %s\033[0m\n' "$*"; }
R() { printf '\033[31m  ✗ %s\033[0m\n' "$*"; }

[[ $EUID -eq 0 ]] || { R "Run as root (sudo bash $0)"; exit 1; }
[[ -d "$INSTALL_DIR" ]] || { R "$INSTALL_DIR does not exist. Run install.sh first."; exit 1; }
cd "$INSTALL_DIR"

B "Pulling latest changes"
git fetch origin
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse '@{u}' 2>/dev/null || git rev-parse origin/main)
if [[ "$LOCAL" == "$REMOTE" ]]; then
    G "already at latest commit ($LOCAL)"
else
    git pull --ff-only
    G "updated $(git log --oneline -1)"
fi

B "Rebuilding + restarting (data volumes preserved)"
docker compose --profile prod up -d --build

B "Waiting for services to settle"
sleep 15
docker compose ps

cat <<EOF

╔══════════════════════════════════════════════════════════════════╗
║  Update complete.                                                 ║
║  Tail logs:  docker compose logs -f --tail=100                    ║
╚══════════════════════════════════════════════════════════════════╝

EOF
