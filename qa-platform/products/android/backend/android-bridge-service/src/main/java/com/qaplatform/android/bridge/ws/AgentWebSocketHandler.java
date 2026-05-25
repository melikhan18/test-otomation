package com.qaplatform.android.bridge.ws;

import com.qaplatform.android.bridge.auth.WsAuth;
import com.qaplatform.android.bridge.hub.DeviceChannel;
import com.qaplatform.android.bridge.hub.DeviceChannelRegistry;
import com.qaplatform.android.bridge.protocol.Frame;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.jwt.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Handler for the agent-side WebSocket. URL: {@code /ws/agent?token={agentJwt}}.
 * Agent sends video / inspect-response / metadata / heartbeat;
 * receives control / inspect-request / force-keyframe frames.
 */
@Component
public class AgentWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final DeviceChannelRegistry registry;
    private final JwtTokenService tokens;
    private final Duration heartbeatTimeout;

    public AgentWebSocketHandler(DeviceChannelRegistry registry, JwtTokenService tokens,
                                 @Value("${app.bridge.heartbeat-timeout-seconds:25}") long timeoutSeconds) {
        this.registry = registry;
        this.tokens = tokens;
        this.heartbeatTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        JwtPrincipal principal;
        try {
            principal = WsAuth.requireAuth(session, tokens);
        } catch (ResponseStatusException e) {
            return session.close(WsAuth.unauthorized(e.getReason()));
        }
        if (!principal.isAgent() || principal.deviceId() == null) {
            return session.close(WsAuth.unauthorized("agent token required"));
        }

        long deviceId = principal.deviceId();
        DeviceChannel channel = registry.attach(deviceId);
        log.info("agent connected device={}", deviceId);

        Mono<Void> inbound = session.receive()
                .timeout(heartbeatTimeout)
                .filter(m -> m.getType() == WebSocketMessage.Type.BINARY)
                .doOnNext(msg -> {
                    try { channel.publishFromAgent(Frame.decode(msg)); }
                    catch (Exception ex) { log.warn("bad frame from device {}: {}", deviceId, ex.toString()); }
                })
                .doOnError(err -> log.info("agent inbound error device={}: {}", deviceId, err.toString()))
                .then();

        Mono<Void> outbound = session.send(
                channel.agentOutbound().map(f -> f.encode(session))
        );

        // Mono.when (not Mono.zip): zip on two Mono<Void> completes empty immediately because
        // neither emits a value — that was closing the WS with no status code on first handshake.
        // Mono.when waits for both signals (complete or error) without needing values.
        return Mono.when(inbound, outbound)
                .doFinally(sig -> {
                    log.info("agent disconnected device={} reason={}", deviceId, sig);
                    registry.detach(deviceId, channel);
                })
                .onErrorResume(e -> {
                    log.error("websocket error device={}", deviceId, e);
                    return session.close(CloseStatus.SERVER_ERROR).then();
                });
    }
}
