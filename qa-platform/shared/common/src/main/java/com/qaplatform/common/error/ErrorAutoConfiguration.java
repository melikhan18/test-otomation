package com.qaplatform.common.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link GlobalExceptionHandler} as a bean so every service that pulls in
 * the common module gets {@link ApiException} → JSON ProblemDetail mapping without
 * having to widen its component-scan to {@code com.qaplatform.common}.
 *
 * <p>Picked up via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 */
@AutoConfiguration
public class ErrorAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
