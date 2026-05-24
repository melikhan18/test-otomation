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

    // ── Playwright Java client ──────────────────────────────────────────
    // Version MUST match the base image tag in Dockerfile
    //   (mcr.microsoft.com/playwright:v1.49.0-noble).
    // A drift causes "browser binary X requires driver version Y" errors
    // at the first Playwright.create() call.
    implementation("com.microsoft.playwright:playwright:1.49.0")

    // ── AWS S3 SDK (MinIO) for artifact upload (trace/video/screenshots).
    // We reuse the same SDK pattern Android's automation-service uses.
    implementation(platform("software.amazon.awssdk:bom:2.28.16"))
    implementation("software.amazon.awssdk:s3")

    // ── Jackson is already on the path via Spring Web. ──────────────────

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
