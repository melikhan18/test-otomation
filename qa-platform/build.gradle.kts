plugins {
    java
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.qaplatform"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Spring Boot's plugin produces both an executable fat jar (the `bootJar`
    // task) AND a thin classpath jar (the `jar` task) with `-plain` suffix.
    // We only ship the fat jar — the plain jar exists purely so other gradle
    // modules can depend on the classes, which we don't need here. Disabling
    // it shrinks build output and, more importantly, removes the failure
    // mode where the root Dockerfile's `COPY .../build/libs/*.jar` glob
    // matches both files and silently picks the wrong one (whichever Docker
    // happens to COPY last wins — `app.jar` ends up overwritten with the
    // classpath stub, container fails with "no main manifest attribute").
    tasks.matching { it.name == "jar" }.configureEach {
        enabled = false
    }
}
