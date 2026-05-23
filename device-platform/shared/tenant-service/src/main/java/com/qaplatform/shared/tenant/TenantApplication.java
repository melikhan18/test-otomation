package com.qaplatform.shared.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tenancy service — owns the cross-cutting tenancy state that platform stacks
 * (android-*, ios-*, backend-*, web-*) need to coordinate around.
 *
 * <h2>Scope (F5 skeleton)</h2>
 * Today this service owns a single concern: {@code project_platforms} — which
 * testing platforms a given project has activated. Future fazlar will migrate
 * the company / project / membership tables out of auth-service into here so
 * tenant data lives behind a single API surface; for now those still reside
 * in {@code auth.companies} / {@code auth.projects} and are owned by
 * auth-service.
 */
@SpringBootApplication
public class TenantApplication {
    public static void main(String[] args) {
        SpringApplication.run(TenantApplication.class, args);
    }
}
