package com.devicefarm.bridge.ws;

import com.devicefarm.bridge.auth.WsAuth;
import com.devicefarm.bridge.hub.DeviceChannel;
import com.devicefarm.bridge.hub.DeviceChannelRegistry;
import com.devicefarm.bridge.protocol.Frame;
import com.devicefarm.bridge.protocol.FrameType;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Handler for web control + inspect channel. URL: {@code /ws/session/{sessionId}/control?token={sessionJwt}}.
 * Web sends control commands and inspect requests, receives inspect responses.
 */
@Component
public class ControlWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ControlWebSocketHandler.class);

    private final DeviceChannelRegistry registry;
    private final JwtTokenService tokens;

    public ControlWebSocketHandler(DeviceChannelRegistry registry, JwtTokenService tokens) {
        this.registry = registry;
        this.tokens = tokens;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        JwtPrincipal principal;
        try { principal = WsAuth.requireAuth(session, tokens); }
        catch (ResponseStatusException e) { return session.close(WsAuth.unauthorized(e.getReason())); }

        if (principal.deviceId() == null || principal.sessionId() == null) {
            return session.close(WsAuth.unauthorized("session token required"));
        }
        DeviceChannel channel = registry.get(principal.deviceId()).orElse(null);
        if (channel == null) return session.close(new org.springframework.web.reactive.socket.CloseStatus(4404, "agent offline"));
        if (channel.productId() != principal.productId()) {
            return session.close(WsAuth.unauthorized("product mismatch"));
        }

        log.info("control attached session={} device={}", principal.sessionId(), principal.deviceId());

        Mono<Void> inbound = session.receive()
                .filter(m -> m.getType() == WebSocketMessage.Type.BINARY || m.getType() == WebSocketMessage.Type.TEXT)
                .doOnNext(msg -> {
                    try {
                        Frame f;
                        if (msg.getType() == WebSocketMessage.Type.TEXT) {
                            // Convenience: web sends plain JSON for control; we wrap as CONTROL_COMMAND.
                            f = Frame.ofJson(FrameType.CONTROL_COMMAND, msg.getPayloadAsText());
                        } else {
                            f = Frame.decode(msg);
                        }
                        switch (f.type()) {
                            case FrameType.CONTROL_COMMAND, FrameType.INSPECT_REQUEST, FrameType.FORCE_KEYFRAME ->
                                channel.sendToAgent(f);
                            default -> log.debug("ignoring inbound frame from web type={}", FrameType.name(f.type()));
                        }
                    } catch (Exception ex) {
                        log.warn("bad inbound web frame: {}", ex.toString());
                    }
                })
                .then();

        Mono<Void> outbound = session.send(
                channel.subscribeWeb(false)
                        .filter(f -> FrameType.isInspectResponse(f.type()))
                        .map(f -> f.encode(session))
        );

        return Mono.when(inbound, outbound)
                .doFinally(sig -> log.info("control detached session={} reason={}", principal.sessionId(), sig));
    }
}
