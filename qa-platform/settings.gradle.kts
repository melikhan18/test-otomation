// Monorepo layout — modules live in two pools:
//   shared/                         shared kernel reused by every platform stack
//   products/{platform}/backend/    platform-specific backend services
//
// Module short names reflect the deployment name (e.g. `:android-device-service`)
// so Gradle task references match Docker compose service names and Dockerfile
// build args one-for-one.

rootProject.name = "qa-platform"

/**
 * Conditional include — only register a module if its source dir exists in
 * the current build context. The root `docker compose build` always has
 * everything, but per-service Dockerfiles (e.g. `web-runner-service`) copy
 * only a slim subset of the tree into their build stage to keep image
 * size + cache hit rate sane. Without this guard, gradle would try to
 * configure every project on every build and fail with a missing-dir
 * error the first time some platform's sources weren't staged.
 *
 * Gradle's lazy task graph lets us get away with declaring all modules
 * here as long as the missing ones are never *evaluated* — but that
 * "as long as" is fragile, especially with plugins that scan all
 * projects eagerly. Filtering at the include() boundary is simpler
 * and survives plugin upgrades.
 */
fun includeIfExists(name: String, dir: String) {
    if (file(dir).isDirectory) {
        include(name)
        project(name).projectDir = file(dir)
    }
}

// ─── Shared kernel ───────────────────────────────────────────────────────────
includeIfExists(":common",                     "shared/common")
includeIfExists(":auth-service",               "shared/auth-service")
includeIfExists(":tenant-service",             "shared/tenant-service")
includeIfExists(":reports-aggregator-service", "shared/reports-aggregator-service")
includeIfExists(":api-gateway",                "shared/api-gateway")

// ─── Android platform stack ──────────────────────────────────────────────────
includeIfExists(":android-automation-service", "products/android/backend/automation-service")
includeIfExists(":android-device-service",     "products/android/backend/device-service")
includeIfExists(":android-session-service",    "products/android/backend/session-service")
includeIfExists(":android-bridge-service",     "products/android/backend/android-bridge-service")

// ─── Web platform stack ──────────────────────────────────────────────────────
includeIfExists(":web-runner-service", "products/web/backend/runner-service")

// ─── (gelecek platformlar buraya eklenir — iOS, backend) ─────────────────────

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
