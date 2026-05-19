rootProject.name = "device-platform-backend"

include(
    "common",
    "auth-service",
    "device-service",
    "session-service",
    "device-bridge-service",
    "api-gateway",
    "automation-service",
)

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
