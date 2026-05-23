package com.qaplatform.android.automation.config;

import com.qaplatform.common.jwt.JwtAuthFilter;
import com.qaplatform.common.jwt.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtTokenService tokens) { return new JwtAuthFilter(tokens); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwt) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
