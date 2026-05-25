#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QA Platform — Server cleanup
#
# Wipes *every* docker container/image/volume and removes /opt/qa-platform.
# Idempotent: re-running on an already-clean server is a no-op.
#
# Run BEFORE install.sh on a server with leftover state from previous
# experiments. Skip if the box is a fresh Ubuntu install.
#
# Usage (as root):
#   bash deploy/cleanup.sh
#   bash deploy/cleanup.sh --yes        # skip confirmation prompt
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Pretty output ──────────────────────────────────────────────────────────
B() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }   # blue header
G() { printf '\033[32m  ✓ %s\033[0m\n' "$*"; }     # green check
Y() { printf '\033[33m  ! %s\033[0m\n' "$*"; }     # yellow warn
R() { printf '\033[31m  ✗ %s\033[0m\n' "$*"; }     # red error

# ── Pre-flight ─────────────────────────────────────────────────────────────
[[ $EUID -eq 0 ]] || { R "Run as root (sudo bash $0)"; exit 1; }

AUTO_YES=0
[[ "${1:-}" == "--yes" || "${1:-}" == "-y" ]] && AUTO_YES=1

if [[ $AUTO_YES -ne 1 ]]; then
    cat <<EOF

╔══════════════════════════════════════════════════════════════════╗
║  WARNING                                                          ║
║                                                                   ║
║  This will permanently delete:                                    ║
║    • Every docker container (running and stopped)                 ║
║    • Every docker image, volume, network                          ║
║    • The directory /opt/qa-platform (if present)                  ║
║    • nginx, apache, caddy systemd services (if active)            ║
║                                                                   ║
║  All databases inside these volumes will be lost.                 ║
╚══════════════════════════════════════════════════════════════════╝

EOF
    read -p "Type 'yes' to proceed: " ans
    [[ "$ans" == "yes" ]] || { Y "Cancelled."; exit 0; }
fi

# ── Stop any web server holding ports 80/443 ───────────────────────────────
B "Stopping conflicting webservers (port 80/443)"
for svc in nginx apache2 caddy; do
    if systemctl is-active --quiet "$svc" 2>/dev/null; then
        systemctl stop "$svc" || true
        G "stopped $svc"
    fi
    if systemctl is-enabled --quiet "$svc" 2>/dev/null; then
        systemctl disable "$svc" || true
        G "disabled $svc"
    fi
done

# ── Docker resources ───────────────────────────────────────────────────────
if command -v docker &>/dev/null; then
    B "Stopping all docker containers"
    mapfile -t CIDS < <(docker ps -aq 2>/dev/null || true)
    if [[ ${#CIDS[@]} -gt 0 ]]; then
        docker stop "${CIDS[@]}" >/dev/null 2>&1 || true
        docker rm "${CIDS[@]}"   >/dev/null 2>&1 || true
        G "removed ${#CIDS[@]} container(s)"
    else
        G "no containers"
    fi

    B "Pruning docker images / volumes / networks / build cache"
    docker system prune -af --volumes >/dev/null 2>&1 || true
    docker network prune -f >/dev/null 2>&1 || true
    G "docker reset to empty state"
else
    Y "docker not installed — nothing to prune"
fi

# ── Old project dirs ───────────────────────────────────────────────────────
B "Removing old project directories"
REMOVED=0
for dir in /opt/qa-platform /root/qa-platform /home/*/qa-platform /srv/qa-platform; do
    if [[ -d "$dir" ]]; then
        rm -rf "$dir"
        G "removed $dir"
        REMOVED=$((REMOVED+1))
    fi
done
[[ $REMOVED -eq 0 ]] && G "no project dirs found"

# Find anything we might have missed
B "Sanity scan for leftover project paths"
LEFTOVER=$(find / -maxdepth 4 -type d \( -name "qa-platform" -o -name "test-otomation*" \) 2>/dev/null | head -10 || true)
if [[ -n "$LEFTOVER" ]]; then
    Y "Found these leftover paths — review and rm manually if not yours:"
    echo "$LEFTOVER" | sed 's/^/    /'
else
    G "no stray paths"
fi

# ── Port 80/443 final check ────────────────────────────────────────────────
B "Final port check (80/443 must be FREE before install.sh)"
PORT_HOG=$(ss -tlnp 2>/dev/null | grep -E ":80 |:443 " || true)
if [[ -n "$PORT_HOG" ]]; then
    R "Something is still listening:"
    echo "$PORT_HOG" | sed 's/^/    /'
    R "Resolve this before running install.sh."
    exit 1
else
    G "ports 80 and 443 are free"
fi

cat <<EOF

╔══════════════════════════════════════════════════════════════════╗
║  Cleanup complete.                                                ║
║  Next step:  bash deploy/install.sh                               ║
╚══════════════════════════════════════════════════════════════════╝

EOF
