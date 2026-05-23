// Monorepo layout — modules live in two pools:
//   shared/                         shared kernel reused by every platform stack
//   products/{platform}/backend/    platform-specific backend services
//
// Module short names (`:auth-service`, `:automation-service`) are kept so existing
// Gradle task references (`:auth-service:bootJar`) and Dockerfile build args still
// resolve. Only `projectDir` points to the new physical path.

rootProject.name = "qa-platform"

// ─── Shared kernel ───────────────────────────────────────────────────────────
include(":common")
include(":auth-service")
include(":api-gateway")

project(":common").projectDir       = file("shared/common")
project(":auth-service").projectDir = file("shared/auth-service")
project(":api-gateway").projectDir  = file("shared/api-gateway")

// ─── Android platform stack ──────────────────────────────────────────────────
include(":automation-service")
include(":device-service")
include(":session-service")
include(":device-bridge-service")

project(":automation-service").projectDir    = file("products/android/backend/automation-service")
project(":device-service").projectDir        = file("products/android/backend/device-service")
project(":session-service").projectDir       = file("products/android/backend/session-service")
project(":device-bridge-service").projectDir = file("products/android/backend/device-bridge-service")

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
