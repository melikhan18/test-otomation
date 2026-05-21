package com.devicefarm.gateway;

import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Validates Bearer token (and ws subprotocol) at the edge and injects identity headers
 * for downstream services. Anonymous endpoints listed below are bypassed.
 */
@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/signup",
            // SSE notification stream — EventSource can't set Authorization headers,
            // so it carries the token in ?access_token=... and is verified inside the controller.
            "/api/notifications/stream",
            "/actuator",
            // WebSocket endpoints are validated by the bridge service itself (the token rides
            // in the query string because browsers can't set Authorization on a new WebSocket).
            "/ws/"
    );

    private static final List<String> PUBLIC_AGENT_PATHS = List.of(
            "/api/agent/enroll"
    );

    private final JwtTokenService tokens;

    public JwtGatewayFilter(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getPath().value();

        if (isPublic(path)) return chain.filter(exchange);

        String auth = req.getHeaders().getFirst("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;

        // For WebSocket: token may come via subprotocol header `Sec-WebSocket-Protocol: jwt.{token}`
        if (token == null) {
            String sub = req.getHeaders().getFirst("Sec-WebSocket-Protocol");
            if (sub != null && sub.startsWith("jwt.")) token = sub.substring(4);
        }

        if (token == null) return deny(exchange, "missing token");

        JwtPrincipal principal;
        try {
            principal = tokens.parse(token);
        } catch (JwtTokenService.InvalidJwtException e) {
            return deny(exchange, "invalid token");
        }

        ServerHttpRequest mutated = req.mutate()
                .header("X-User-Id", str(principal.userId()))
                .header("X-Device-Id", str(principal.deviceId()))
                .header("X-Session-Id", str(principal.sessionId()))
                .header("X-Product-Id", str(principal.productId()))
                .header("X-Role", principal.role() == null ? "" : principal.role())
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static String str(Long v) { return v == null ? "" : String.valueOf(v); }

    private static boolean isPublic(String path) {
        for (String p : PUBLIC_PATH_PREFIXES) if (path.startsWith(p)) return true;
        for (String p : PUBLIC_AGENT_PATHS)   if (path.equals(p))   return true;
        return false;
    }

    private static Mono<Void> deny(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("WWW-Authenticate", "Bearer error=\"" + reason + "\"");
        return exchange.getResponse().setComplete();
    }

    @Override public int getOrder() { return -100; }
}
