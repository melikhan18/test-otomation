package com.qaplatform.shared.auth.service;

import com.qaplatform.shared.auth.api.dto.NotificationDtos;
import com.qaplatform.shared.auth.domain.*;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Read/write API for the {@link Notification} table. Other services call
 * {@link #createForUser} to drop new events into someone's bell; the broker
 * then pushes those events out over SSE.
 */
@Service
public class NotificationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final NotificationBroker broker;

    public NotificationService(NotificationRepository notifications, UserRepository users,
                               NotificationBroker broker) {
        this.notifications = notifications;
        this.users = users;
        this.broker = broker;
    }

    /* ─────────────────────────  reads  ───────────────────────── */

    @Transactional(readOnly = true)
    public List<NotificationDtos.View> listMine(JwtPrincipal caller) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        List<Notification> rows = notifications.findTop100ByUserIdOrderByCreatedAtDesc(caller.userId());
        // Bulk lookup of actor usernames so the bell can render "{actor} invited you".
        Map<Long, String> actorNames = collectActorNames(rows);
        return rows.stream().map(n -> toView(n, actorNames)).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(JwtPrincipal caller) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        return notifications.countByUserIdAndStatus(caller.userId(), Notification.STATUS_UNREAD);
    }

    /* ─────────────────────────  writes  ──────────────────────── */

    /** Marks a single row read. No-op if already non-UNREAD. */
    @Transactional
    public NotificationDtos.View markRead(JwtPrincipal caller, long id) {
        Notification n = ensureOwned(caller, id);
        n.markRead();
        return toView(n, Map.of());
    }

    @Transactional
    public void markAllRead(JwtPrincipal caller) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        notifications.markAllRead(caller.userId());
    }

    @Transactional
    public NotificationDtos.View dismiss(JwtPrincipal caller, long id) {
        Notification n = ensureOwned(caller, id);
        // Info notifications (RUN_COMPLETED etc.) can be hidden; actionable ones
        // (COMPANY_INVITATION) should be resolved instead.
        n.setStatus(Notification.STATUS_DISMISSED);
        return toView(n, Map.of());
    }

    /**
     * Push a new notification onto a user's bell and broadcast over SSE.
     * Returns the persisted view so the caller can include it in its own response
     * (e.g. the invite endpoint returns the freshly-created notification).
     */
    @Transactional
    public NotificationDtos.View createForUser(long userId, String type,
                                                Map<String, Object> payload, Long actorUserId) {
        String payloadJson;
        try { payloadJson = MAPPER.writeValueAsString(payload == null ? Map.of() : payload); }
        catch (Exception e) { throw new IllegalArgumentException("payload not serializable", e); }

        Notification n = notifications.save(new Notification(userId, type, payloadJson, actorUserId));

        Map<Long, String> actorNames = actorUserId == null
                ? Map.of()
                : users.findById(actorUserId).map(u -> Map.of(u.getId(), u.getUsername())).orElse(Map.of());
        NotificationDtos.View view = toView(n, actorNames);
        // Real-time push so the bell badge updates without a page refresh.
        broker.publish(userId, "notification", view);
        return view;
    }

    /** Used by invite accept/decline — resolves the row with a terminal status. */
    @Transactional
    public NotificationDtos.View resolve(JwtPrincipal caller, long id, String terminalStatus) {
        Notification n = ensureOwned(caller, id);
        n.resolve(terminalStatus);
        return toView(n, Map.of());
    }

    /**
     * Read a stored notification's payload as a Map. Used by services that need
     * to act on payload contents during accept/decline flows.
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findPayload(long id) {
        return notifications.findById(id).map(n -> {
            try { return MAPPER.readValue(n.getPayload() == null ? "{}" : n.getPayload(), MAP_TYPE); }
            catch (Exception e) { return Map.of(); }
        });
    }

    /* ─────────────────────────  helpers  ─────────────────────── */

    private Notification ensureOwned(JwtPrincipal caller, long id) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        Notification n = notifications.findById(id)
                .orElseThrow(() -> ApiException.notFound("notification"));
        if (!n.getUserId().equals(caller.userId()) && !caller.platformAdmin()) {
            throw ApiException.forbidden("not your notification");
        }
        return n;
    }

    private NotificationDtos.View toView(Notification n, Map<Long, String> actorNames) {
        Map<String, Object> payload;
        try { payload = MAPPER.readValue(n.getPayload() == null ? "{}" : n.getPayload(), MAP_TYPE); }
        catch (Exception e) { payload = Map.of(); }
        return new NotificationDtos.View(
                n.getId(), n.getType(), n.getStatus(), payload,
                n.getActorUserId(), n.getActorUserId() != null ? actorNames.get(n.getActorUserId()) : null,
                n.getCreatedAt(), n.getResolvedAt(), n.getExpiresAt());
    }

    private Map<Long, String> collectActorNames(List<Notification> rows) {
        Set<Long> ids = new HashSet<>();
        for (Notification n : rows) if (n.getActorUserId() != null) ids.add(n.getActorUserId());
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>(ids.size());
        users.findAllById(ids).forEach(u -> out.put(u.getId(), u.getUsername()));
        return out;
    }
}
