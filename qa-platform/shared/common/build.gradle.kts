plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-web")
    api("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    api("org.slf4j:slf4j-api")

    // JSON-formatted Logback output. Every service that depends on :common
    // automatically picks up the shared logback-spring.xml in this module's
    // resources, which produces structured JSON ready for Loki/ELK/Grafana
    // ingestion. Pinned to the latest stable that matches Logback 1.5.x
    // (the version Spring Boot 3.3 ships).
    api("net.logstash.logback:logstash-logback-encoder:8.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
