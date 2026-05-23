package com.qaplatform.shared.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory hub of per-user SSE connections. Used by {@link NotificationService}
 * to push freshly-created notifications to any browser tab the user has open.
 *
 * One user → N emitters (multiple tabs, mobile + desktop). We keep the set
 * synchronized via {@link CopyOnWriteArrayList} so dispatching never blocks
 * registrations.
 *
 * Limitations
 * ───────────
 * – Single-instance only. Horizontally-scaled deploys need Redis Pub/Sub or
 *   similar to fan out across replicas.
 * – Emitter cleanup is best-effort: completion/timeout callbacks unregister,
 *   but a hard kill of the connection only surfaces on the next failed send.
 */
@Component
public class NotificationBroker {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 30 minutes — long enough to survive normal proxy idle timeouts but bounded. */
    public static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Register a tab. Returns the emitter to attach to the controller response. */
    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<SseEmitter> bucket = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        bucket.add(emitter);

        Runnable remove = () -> {
            bucket.remove(emitter);
            if (bucket.isEmpty()) emitters.remove(userId, bucket);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError((t) -> remove.run());

        // Initial "ready" packet so the browser knows the channel is live; lets us
        // distinguish a working connection from a stuck preflight.
        sendQuietly(emitter, "ready", Map.of("ts", System.currentTimeMillis()));
        return emitter;
    }

    /**
     * Push an event to every tab the given user has open. {@code payload} is any
     * JSON-serialisable value; the wire event name becomes the SSE {@code event:}
     * field, letting the client subscribe with {@code addEventListener('notification', …)}.
     */
    public void publish(long userId, String event, Object payload) {
        List<SseEmitter> bucket = emitters.get(userId);
        if (bucket == null || bucket.isEmpty()) return;
        for (SseEmitter em : bucket) sendQuietly(em, event, payload);
    }

    private static void sendQuietly(SseEmitter em, String event, Object payload) {
        try {
            em.send(SseEmitter.event().name(event).data(MAPPER.writeValueAsString(payload)));
        } catch (JsonProcessingException jpe) {
            log.warn("SSE serialize failed", jpe);
        } catch (IOException io) {
            // Client closed the connection — completion callback will clean up.
            em.complete();
        } catch (IllegalStateException ise) {
            // Emitter already completed; harmless during shutdown races.
        }
    }
}
