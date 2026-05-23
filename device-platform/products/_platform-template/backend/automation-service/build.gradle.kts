// COPY-TARGET — this build file is not on the Gradle path until you register
// the module in settings.gradle.kts (see ../../README.md, step 3).
//
// Mirrors shared/auth-service/build.gradle.kts shape: Spring Boot + JPA +
// Flyway + Security + the shared :common library. Add platform-specific
// dependencies (your driver SDK, an HTTP client for an external service,
// etc.) below the marked block.

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ── Platform-specific dependencies go here ──────────────────────────
    // e.g. for iOS:  implementation("com.your-org:wda-client:1.0")
    // e.g. for Web:  implementation("com.microsoft.playwright:playwright:1.41")
    // ────────────────────────────────────────────────────────────────────

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
