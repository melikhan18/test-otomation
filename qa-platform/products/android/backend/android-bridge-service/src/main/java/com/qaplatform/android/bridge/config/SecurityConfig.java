package com.qaplatform.android.bridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * The bridge does NOT use Spring Security to authenticate WebSocket upgrades — the handlers
 * themselves parse the token (browsers can't set Authorization on a WebSocket).
 * This config keeps actuator + everything else open at the framework level.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurity(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(a -> a.anyExchange().permitAll())
                .build();
    }
}
