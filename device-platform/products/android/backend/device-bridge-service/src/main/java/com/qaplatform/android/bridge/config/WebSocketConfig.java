package com.qaplatform.android.bridge.config;

import com.qaplatform.android.bridge.ws.AgentWebSocketHandler;
import com.qaplatform.android.bridge.ws.ControlWebSocketHandler;
import com.qaplatform.android.bridge.ws.VideoWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import reactor.netty.http.server.WebsocketServerSpec;

import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping wsHandlerMapping(
            AgentWebSocketHandler agent,
            VideoWebSocketHandler video,
            ControlWebSocketHandler control
    ) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(
                "/ws/agent",                       agent,
                "/ws/session/{sessionId}/video",   video,
                "/ws/session/{sessionId}/control", control
        ));
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }

    /**
     * Reactor Netty'nin default WebSocket frame limiti 65 536 byte (64 KB). H.264 keyframe
     * (SPS+PPS+IDR) genellikle bu sınırı aşar — gerçek bir 1080×2400 ekranın ilk keyframe'i
     * 200-400 KB olabilir. Limit aşılınca server CloseStatus 1009 ile bağlantıyı keser.
     *
     * Çözüm: {@link WebsocketServerSpec.Builder} üzerinden max payload uzunluğunu yükselt.
     * 10 MB'lık tavan her makul ekran çözünürlüğü/bitrate için fazlasıyla yeterli.
     */
    @Bean
    public WebSocketService webSocketService(
            @Value("${app.bridge.max-ws-frame-bytes:10485760}") int maxFrameBytes
    ) {
        ReactorNettyRequestUpgradeStrategy strategy =
                new ReactorNettyRequestUpgradeStrategy(
                        () -> WebsocketServerSpec.builder().maxFramePayloadLength(maxFrameBytes)
                );
        return new HandshakeWebSocketService(strategy);
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter(WebSocketService service) {
        return new WebSocketHandlerAdapter(service);
    }
}
