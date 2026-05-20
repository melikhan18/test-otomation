# syntax=docker/dockerfile:1
# Multi-stage build shared by every Spring Boot service in this folder.
# Pick the module to build with `--build-arg SERVICE=auth-service` (or device-service, etc.).

FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY common/ common/
COPY auth-service/ auth-service/
COPY device-service/ device-service/
COPY session-service/ session-service/
COPY device-bridge-service/ device-bridge-service/
COPY api-gateway/ api-gateway/
COPY automation-service/ automation-service/
ARG SERVICE
RUN gradle :${SERVICE}:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
ARG SERVICE
ENV TZ=UTC
WORKDIR /opt/app

# Only the bridge needs ffmpeg (for H.264 → MP4 remux during run recordings).
# Conditional install keeps the other service images ~80MB lighter.
RUN if [ "$SERVICE" = "device-bridge-service" ]; then \
        apt-get update && \
        apt-get install -y --no-install-recommends ffmpeg && \
        rm -rf /var/lib/apt/lists/* ; \
    fi

COPY --from=build /workspace/${SERVICE}/build/libs/*.jar /opt/app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=70.0","-jar","/opt/app/app.jar"]
