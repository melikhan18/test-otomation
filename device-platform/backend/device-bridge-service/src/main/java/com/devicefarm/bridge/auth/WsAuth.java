package com.devicefarm.bridge.auth;

import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Pulls a JWT from a WebSocket handshake. Browsers cannot set arbitrary headers on a
 * {@code new WebSocket(...)} call, so the canonical channel is the {@code token} query
 * parameter. The {@code Authorization} header and {@code Sec-WebSocket-Protocol: jwt.X}
 * are also accepted for non-browser clients.
 */
public final class WsAuth {

    private WsAuth() {}

    public static JwtPrincipal requireAuth(WebSocketSession session, JwtTokenService tokens) {
        String t = extractToken(session);
        if (t == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing token");
        try {
            return tokens.parse(t);
        } catch (JwtTokenService.InvalidJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private static String extractToken(WebSocketSession session) {
        Map<String, List<String>> q = session.getHandshakeInfo().getUri() == null
                ? Map.of()
                : parseQuery(session.getHandshakeInfo().getUri().getRawQuery());
        List<String> qToken = q.get("token");
        if (qToken != null && !qToken.isEmpty()) return qToken.get(0);

        List<String> headers = session.getHandshakeInfo().getHeaders().getOrEmpty("Authorization");
        for (String h : headers) {
            if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        }
        List<String> subprotocols = session.getHandshakeInfo().getHeaders().getOrEmpty("Sec-WebSocket-Protocol");
        for (String h : subprotocols) {
            if (h == null) continue;
            for (String token : h.split(",")) {
                String trimmed = token.trim();
                if (trimmed.startsWith("jwt.")) return trimmed.substring(4);
            }
        }
        return null;
    }

    private static Map<String, List<String>> parseQuery(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        java.util.Map<String, java.util.List<String>> out = new java.util.HashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            out.computeIfAbsent(k, __ -> new java.util.ArrayList<>()).add(v);
        }
        return out;
    }

    public static CloseStatus unauthorized(String reason) {
        return new CloseStatus(4401, reason);
    }
}
