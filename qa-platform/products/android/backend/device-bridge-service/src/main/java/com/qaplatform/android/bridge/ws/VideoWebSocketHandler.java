package com.qaplatform.android.bridge.ws;

import com.qaplatform.android.bridge.auth.WsAuth;
import com.qaplatform.android.bridge.hub.DeviceChannel;
import com.qaplatform.android.bridge.hub.DeviceChannelRegistry;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.jwt.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Handler for web video subscribers. URL: {@code /ws/session/{sessionId}/video?token={sessionJwt}}.
 * Read-only: streams H.264 keyframes/deltas + stream metadata.
 */
@Component
public class VideoWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoWebSocketHandler.class);

    private final DeviceChannelRegistry registry;
    private final JwtTokenService tokens;
    private final boolean forceKeyframeOnSubscribe;

    public VideoWebSocketHandler(DeviceChannelRegistry registry, JwtTokenService tokens,
                                 @Value("${app.bridge.force-keyframe-on-subscribe:true}") boolean forceKf) {
        this.registry = registry;
        this.tokens = tokens;
        this.forceKeyframeOnSubscribe = forceKf;
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

        log.info("video subscriber attached session={} device={}", principal.sessionId(), principal.deviceId());

        if (forceKeyframeOnSubscribe) channel.requestKeyframe();

        Mono<Void> sink = session.send(channel.subscribeWeb(true).map(f -> f.encode(session)));
        // Drain any inbound to keep the socket healthy (web side may send heartbeat pings).
        Mono<Void> drain = session.receive().then();
        return Mono.when(sink, drain)
                .doFinally(sig -> log.info("video subscriber detached session={} reason={}", principal.sessionId(), sig));
    }
}
