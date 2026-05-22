# ── Build stage ────────────────────────────────────────────────────────────────
# Compile the React SPA. Output ends up in /app/dist, copied into the runtime
# image below. We use pnpm because the lockfile is pnpm-lock.yaml — npm/yarn
# would silently resolve to different versions and break reproducibility.
FROM node:20-alpine AS build
WORKDIR /app
RUN corepack enable

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# ── Runtime stage ──────────────────────────────────────────────────────────────
# Caddy serves the static SPA AND reverse-proxies /api + /ws + /minio to the
# backend. Single container = no separate nginx + frontend split.
#
# Let's Encrypt certificates are issued automatically on first boot. They're
# persisted in /data (mounted as a docker volume by compose) so a restart
# doesn't re-issue and hit the rate limit (5 certs/week/domain).
FROM caddy:2-alpine
COPY --from=build /app/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile

EXPOSE 80 443
