# Deployment — one-shot scripts

Three scripts run end-to-end deploys on a fresh (or just-cleaned) Ubuntu
22.04 server. They were written for the maieras.com host (10 GB RAM, single
node) but work on any equivalent box.

| Script        | When to run                                            |
|---------------|--------------------------------------------------------|
| `cleanup.sh`  | Server has leftover docker/projects from old attempts. |
| `install.sh`  | Bootstraps everything — packages, firewall, swap, docker, build, up. |
| `update.sh`   | After every `git push` to main — pulls + rebuilds in place. |

All three are **idempotent** (safe to re-run) and **non-destructive of
data** unless you explicitly ask for cleanup.

---

## First-time deployment

DNS first (otherwise Let's Encrypt won't issue a cert):

- Set an `A` record for `maieras.com` → server IP
- Set an `A` record for `www.maieras.com` → same IP

Then on the server:

```bash
ssh root@<server-ip>

# 1. Clone the repo. The project root is one level inside the repo
# (the repo has a top-level qa-platform/ subdir), so the install path
# you'll actually `cd` into is /opt/test-otomation/qa-platform.
git clone https://github.com/melikhan18/test-otomation.git /opt/test-otomation
cd /opt/test-otomation/qa-platform

# 2. Wipe any old state (skip if server is virgin)
bash deploy/cleanup.sh           # answer 'yes' to confirm

# 3. Install + boot
bash deploy/install.sh
```

The installer:

- Picks `DOMAIN=maieras.com` and `ACME_EMAIL=admin@maieras.com` by default.
  Override with env vars: `DOMAIN=foo.com ACME_EMAIL=me@x.com bash deploy/install.sh`
- Generates strong random secrets (`JWT_SECRET`, postgres password, MinIO root
  password, admin password) and writes them to `.env`.
- Saves a human-readable credential backup to `/root/qa-platform-credentials.txt`
  so you can find the admin password later.
- Builds every container and waits for healthchecks before declaring done.

First build is slow (~15–25 min, mostly the Playwright image).

When it finishes you'll see the admin password printed in a box. Visit
<https://maieras.com> and log in as `admin`.

---

## Pulling new commits

```bash
ssh root@<server-ip>
cd /opt/test-otomation/qa-platform
bash deploy/update.sh
```

Pulls `main`, runs `docker compose build` (uses cache where possible), then
`up -d` to recreate only the containers whose images changed. Postgres,
MinIO, and Caddy data volumes are preserved across this.

---

## Fresh-start nuke

If you want to wipe the entire deployment and reinstall from scratch:

```bash
cd /opt/test-otomation/qa-platform
docker compose --profile prod down -v   # NOTE: -v drops all data volumes
bash deploy/cleanup.sh --yes
bash deploy/install.sh
```

`down -v` deletes `qp_pgdata`, `qp_miniodata`, `qp_caddydata`,
`qp_caddyconfig`. You will lose every user, project, run, screenshot, video,
and TLS certificate. New certs will be issued on next boot.

---

## What the installer changes on your server

| File / system | Why |
|---------------|-----|
| `apt install …` | curl, git, jq, ufw, openssl, vim, htop |
| `/swapfile` (4 GB) | Headroom for build spikes on 10 GB hosts |
| UFW rules | 22 (SSH), 80, 443 TCP, 443 UDP (HTTP/3) — everything else denied |
| `/etc/apt/sources.list.d/docker.list` | Official Docker CE repo |
| `docker-ce` + `docker-compose-plugin` | If not already installed |
| `/opt/test-otomation/qa-platform/` | Project root inside the cloned repo |
| `/opt/test-otomation/qa-platform/.env` | Generated once on first run |
| `/root/qa-platform-credentials.txt` | Plain-text credential backup |

Everything is reversible by running `cleanup.sh` plus removing the swapfile
+ UFW rules manually if desired.
