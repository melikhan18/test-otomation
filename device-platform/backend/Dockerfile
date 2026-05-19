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
COPY --from=build /workspace/${SERVICE}/build/libs/*.jar /opt/app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=70.0","-jar","/opt/app/app.jar"]
