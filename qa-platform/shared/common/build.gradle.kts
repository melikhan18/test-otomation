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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
