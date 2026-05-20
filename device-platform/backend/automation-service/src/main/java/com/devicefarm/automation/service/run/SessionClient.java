package com.devicefarm.automation.service.run;

import com.devicefarm.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin client that calls session-service to acquire / release a device reservation on
 * behalf of the user who triggered the run.
 *
 * The user JWT is forwarded so session-service's existing ownership checks apply
 * (cross-product access is rejected, etc.).
 */
@Component
public class SessionClient {

    private final RestClient http;

    public SessionClient(@Value("${app.services.session.url:http://localhost:8083}") String baseUrl) {
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Reservation reserve(long deviceId, String userJwt) {
        try {
            JsonNode body = http.post()
                    .uri("/api/sessions")
                    .header("Authorization", "Bearer " + userJwt)
                    .body(Map.of("deviceId", deviceId))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.has("id") || !body.has("sessionToken")) {
                throw ApiException.badRequest("session-service returned no token");
            }
            return new Reservation(body.get("id").asLong(), body.get("sessionToken").asText());
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            throw new ApiException(e.getStatusCode().is4xxClientError()
                    ? org.springframework.http.HttpStatus.valueOf(e.getStatusCode().value())
                    : org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "reservation failed: " + body);
        }
    }

    public void release(long sessionId, String userJwt) {
        try {
            http.delete()
                    .uri("/api/sessions/{id}", sessionId)
                    .header("Authorization", "Bearer " + userJwt)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Best-effort release — the OrphanedSessionReaper will mop up.
        }
    }

    public record Reservation(long sessionId, String sessionToken) {}
}
