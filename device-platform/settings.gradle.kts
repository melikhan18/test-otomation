// Monorepo layout — modules live in two pools:
//   shared/                         shared kernel reused by every platform stack
//   products/{platform}/backend/    platform-specific backend services
//
// Module short names reflect the deployment name (e.g. `:android-device-service`)
// so Gradle task references match Docker compose service names and Dockerfile
// build args one-for-one.

rootProject.name = "qa-platform"

// ─── Shared kernel ───────────────────────────────────────────────────────────
include(":common")
include(":auth-service")
include(":tenant-service")
include(":reports-aggregator-service")
include(":api-gateway")

project(":common").projectDir                    = file("shared/common")
project(":auth-service").projectDir              = file("shared/auth-service")
project(":tenant-service").projectDir            = file("shared/tenant-service")
project(":reports-aggregator-service").projectDir = file("shared/reports-aggregator-service")
project(":api-gateway").projectDir               = file("shared/api-gateway")

// ─── Android platform stack ──────────────────────────────────────────────────
include(":android-automation-service")
include(":android-device-service")
include(":android-session-service")
include(":android-bridge-service")

project(":android-automation-service").projectDir = file("products/android/backend/automation-service")
project(":android-device-service").projectDir     = file("products/android/backend/device-service")
project(":android-session-service").projectDir    = file("products/android/backend/session-service")
project(":android-bridge-service").projectDir     = file("products/android/backend/device-bridge-service")

// ─── (gelecek platformlar buraya eklenir — iOS, backend, web) ────────────────

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
}
