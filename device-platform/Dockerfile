# syntax=docker/dockerfile:1
# Multi-stage build shared by every Spring Boot service in the monorepo.
#
# Two args drive it:
#   SERVICE       — Gradle module short name (e.g. auth-service). Used to invoke
#                   `gradle :${SERVICE}:bootJar` and to enable per-service apt-get
#                   conditionals (ffmpeg for the bridge).
#   SERVICE_PATH  — Filesystem path of the module's projectDir (e.g.
#                   shared/auth-service, products/android/backend/automation-service).
#                   Needed at runtime to copy the built jar from the correct
#                   build/libs/ directory.
#
# Compose calls this Dockerfile once per service, passing both args.

FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace

# Gradle config — settings + root build + properties.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

# Source trees. Two pools are copied separately so build-cache invalidation is
# scoped: a change in products/android doesn't bust shared/ layer caches.
COPY shared/ shared/
COPY products/ products/

ARG SERVICE
RUN gradle :${SERVICE}:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
ARG SERVICE
ARG SERVICE_PATH
ENV TZ=UTC
WORKDIR /opt/app

# curl is needed by every container's healthcheck (probes /actuator/health). The
# bridge additionally needs ffmpeg for H.264 → MP4 remux.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    if [ "$SERVICE" = "device-bridge-service" ]; then \
        apt-get install -y --no-install-recommends ffmpeg; \
    fi && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/${SERVICE_PATH}/build/libs/*.jar /opt/app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=70.0","-jar","/opt/app/app.jar"]
