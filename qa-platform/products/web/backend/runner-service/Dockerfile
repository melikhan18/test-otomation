# Dedicated Dockerfile for web-runner-service.
#
# Differs from the root Dockerfile (used by the JVM-only services) because
# Playwright Java needs:
#   - Node 20+ (the Java client launches a Node "driver" subprocess to talk
#     CDP / Playwright protocol to the browsers)
#   - Browser binaries (Chromium + Firefox + WebKit) with their OS deps
#     (fonts, libnss3, libatk, libxkbcommon, libgbm, libasound2, …)
#   - /dev/shm sized > 64MB (handled by compose `shm_size: 1gb`)
#
# Microsoft maintains an official base image bundling all of the above for a
# given Playwright version. We add JDK 21 on top and copy in the Spring Boot
# jar. Result is ~1.8GB — biggest image in the stack but acceptable.
#
# IMPORTANT: keep the Playwright version pinned here in sync with the
# `com.microsoft.playwright:playwright` dependency in build.gradle.kts.
# A mismatch between the Java client and the Node driver causes hard-to-
# debug protocol errors at first launch.

# ── stage 1: build the Spring Boot jar ─────────────────────────────────────
FROM gradle:8.10-jdk21 AS builder
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared/common ./shared/common
COPY products/web/backend/runner-service ./products/web/backend/runner-service
RUN gradle --no-daemon :web-runner-service:bootJar -x test

# ── stage 2: runtime — Playwright base image + JDK 21 ──────────────────────
FROM mcr.microsoft.com/playwright:v1.49.0-noble AS runtime

# JDK 21 (the MS base ships Node + browsers but no JVM).
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Point the Playwright Java client at the browsers already installed by the
# MS image so Playwright.create() doesn't try to re-download them at runtime.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_DOWNLOAD_HOST=https://playwright.azureedge.net \
    # Chromium /dev/shm flag — even with shm_size:1gb on the host, Chrome's
    # GPU process is happier when told to fall back to /tmp explicitly.
    PLAYWRIGHT_CHROMIUM_LAUNCH_ARGS="--disable-dev-shm-usage"

WORKDIR /opt/app
COPY --from=builder /workspace/products/web/backend/runner-service/build/libs/*.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Xms256m", "-Xmx1g", "-jar", "app.jar"]
