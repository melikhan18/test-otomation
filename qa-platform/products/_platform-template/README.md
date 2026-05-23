# Platform template

A reference skeleton for adding a new platform stack (iOS, Backend, Web, …)
to the QA Platform. **This directory is intentionally not on the Gradle
build path** — `settings.gradle.kts` does not include it, so nothing in
here compiles. It's a copy source.

The template covers the minimum every platform needs: a single
`automation-service` that owns the platform's scenarios, runs, and step
DSL, talks to `tenant-service` for project-platform activation, and
pushes a summary to `reports-aggregator-service` when each run finishes.
Device-bound platforms (iOS, future Android successors) will add device /
session / bridge services on top — see [§ When you need more services](#when-you-need-more-services).

---

## Step-by-step: add a new platform stack

> Time-box: 1–2 weeks for a non-device platform (Backend, Web), 3–4 weeks
> for a device-bound platform (iOS) including agent work.

Replace `{platform}` below with your platform's lower-case slug
(`ios`, `backend`, `web`, …) and `{PLATFORM}` with the upper-case form
(`IOS`, `BACKEND`, `WEB`).

### 1. Copy the template

```bash
cp -r products/_platform-template products/{platform}
```

### 2. Rename packages, paths, ports

Inside `products/{platform}/` do a recursive search-and-replace:

| Find                                  | Replace with                           |
|---------------------------------------|----------------------------------------|
| `_template`                           | `{platform}`                           |
| `_TEMPLATE`                           | `{PLATFORM}`                           |
| `TemplateAutomationApplication`       | `{Platform}AutomationApplication`      |
| `port: 8099`                          | a free port (see [docs/architecture.md § Servis catalog](../../docs/architecture.md)) |

Then rename the Java directory tree to match the new package:

```bash
git mv products/{platform}/backend/automation-service/src/main/java/com/qaplatform/_template \
       products/{platform}/backend/automation-service/src/main/java/com/qaplatform/{platform}
```

### 3. Register the Gradle module

Edit [`settings.gradle.kts`](../../settings.gradle.kts):

```kotlin
include(":{platform}-automation-service")
project(":{platform}-automation-service").projectDir =
    file("products/{platform}/backend/automation-service")
```

`./gradlew :{platform}-automation-service:compileJava` should now build.

### 4. Add the docker-compose service

In [`docker-compose.yml`](../../docker-compose.yml), copy an existing
`android-automation-service` block, rename, point to your new port and
schema. The `SERVICE` + `SERVICE_PATH` build args feed the shared
[`Dockerfile`](../../Dockerfile).

### 5. Wire the gateway

In [`shared/api-gateway/src/main/resources/application.yml`](../../shared/api-gateway/src/main/resources/application.yml)
add a header-predicated route. Reuse the same path surface
(`/api/scenarios`, `/api/runs`, …) so the frontend doesn't change —
the `X-Platform: {PLATFORM}` header alone picks which stack handles it:

```yaml
- id: {platform}-automation-resources
  uri: ${SVC_{PLATFORM}_AUTOMATION:http://localhost:8099}
  predicates:
    - Path=/api/scenarios/**,/api/suites/**,/api/runs/**,/api/suite-runs/**,/api/elements/**,/api/test-data/**
    - Header=X-Platform, {PLATFORM}
  filters:
    - RewritePath=/api/(?<seg>scenarios|suites|runs|suite-runs|elements|test-data)(?<rest>/?.*), /api/automation/${seg}${rest}
```

Add the matching `SVC_{PLATFORM}_AUTOMATION` env var to the api-gateway
service in `docker-compose.yml`.

### 6. Enable the platform option in the frontend

In [`frontend/web-console/src/store/auth.ts`](../../frontend/web-console/src/store/auth.ts)
the `Platform` union already lists `IOS | BACKEND | WEB`. In
[`WorkspaceSwitcher.tsx`](../../frontend/web-console/src/components/WorkspaceSwitcher.tsx)
flip your platform from disabled (`soon` badge) to enabled.

If your platform needs new step actions (it usually does), extend the
step palette under
[`frontend/web-console/src/components/automation/`](../../frontend/web-console/src/components/automation/).

### 7. Implement the StepExecutor

The [`F6 SPI`](../../shared/common/src/main/java/com/qaplatform/common/runengine/spi/)
is the single integration contract. In the new service implement
[`StepExecutor`](../../shared/common/src/main/java/com/qaplatform/common/runengine/spi/StepExecutor.java) —
the template's `TemplateStepExecutor.java` is the starting point. Your
executor parses `step.payload()`, runs the action against your
platform's driver, and returns a `StepOutcome`. The orchestrator (today
still in `android-automation-service`; will be extracted in a later
faz) takes care of state transitions, retries, cancellation, and
artifact upload.

### 8. Push run summaries to the reports aggregator

Copy `android-automation-service`'s
[`ReportsPublisher`](../../products/android/backend/automation-service/src/main/java/com/qaplatform/android/automation/service/run/ReportsPublisher.java)
pattern. Set `PLATFORM = "{PLATFORM}"` so the cross-platform dashboard
groups your runs correctly. The aggregator's
[`PushRunSummary`](../../shared/reports-aggregator-service/src/main/java/com/qaplatform/shared/reports/api/dto/ReportsDtos.java)
validator already accepts `{PLATFORM}`.

### 9. Allow projects to enable your platform

Once your service is live, project owners enable your platform via:

```bash
POST /api/tenancy/projects/{projectId}/platforms
Body: {"platform": "{PLATFORM}"}
```

The validator in
[`ProjectPlatformDtos.java`](../../shared/tenant-service/src/main/java/com/qaplatform/shared/tenant/api/dto/ProjectPlatformDtos.java)
already accepts your value.

### 10. Smoke test

```bash
docker compose up -d --build {platform}-automation-service api-gateway
# health
curl http://localhost:8099/actuator/health
# gateway routes a request based on the X-Platform header
curl -H "Authorization: Bearer $TOK" \
     -H "X-Platform: {PLATFORM}" \
     http://localhost:8080/api/scenarios
```

---

## When you need more services

This template assumes a **stateless runner** stack (Backend, Web).
Device-bound platforms (iOS, future device farms) also need:

| Concern               | Mirror in the Android stack                                                                                   |
|-----------------------|----------------------------------------------------------------------------------------------------------------|
| Device registry       | [`android-device-service`](../../products/android/backend/device-service/)                                     |
| Reservation + locking | [`android-session-service`](../../products/android/backend/session-service/)                                   |
| WS hub (video + control + inspect) | [`android-bridge-service`](../../products/android/backend/device-bridge-service/)                 |
| On-device agent       | [`android-agent`](../../products/android/agent/android-agent/)                                                 |

Copy each of those as a sibling under `products/{platform}/backend/`
and rename in the same pattern. The `BridgeClient` / `SessionClient`
HTTP surface in the new automation-service stays the same — only the
device-side implementation changes.

---

## What this template intentionally does **not** include

- A pre-baked step DSL — every platform's step set is different.
- A frontend page — the existing console works against the platform
  via header routing; only the step palette needs platform-aware UI.
- Authentication — JWT comes from the shared kernel (`auth-service`),
  the template just trusts the gateway-validated token via
  `SecurityConfig`.
- Tenancy ownership — `tenant-service` owns
  `project_platforms`; the new service just respects whatever's
  enabled there.
