package com.devicefarm.auth.api;

import com.devicefarm.auth.api.dto.NotificationDtos;
import com.devicefarm.auth.service.MemberService;
import com.devicefarm.auth.service.NotificationBroker;
import com.devicefarm.auth.service.NotificationService;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Notification + invitation endpoints.
 *
 * <p>The SSE stream piggybacks on the same /api/notifications base path; the
 * client opens an EventSource with the access token in the query string (since
 * the browser EventSource API doesn't support custom headers). NotificationBroker
 * is the single place that fans out messages to every open tab for a user.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final NotificationBroker broker;
    private final MemberService members;
    private final JwtTokenService tokens;

    public NotificationController(NotificationService service, NotificationBroker broker,
                                  MemberService members, JwtTokenService tokens) {
        this.service = service;
        this.broker = broker;
        this.members = members;
        this.tokens = tokens;
    }

    @GetMapping
    public List<NotificationDtos.View> list(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.listMine(caller);
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadCount unreadCount(@AuthenticationPrincipal JwtPrincipal caller) {
        return new NotificationDtos.UnreadCount(service.unreadCount(caller));
    }

    @PatchMapping("/{id}/read")
    public NotificationDtos.View markRead(@AuthenticationPrincipal JwtPrincipal caller,
                                          @PathVariable long id) {
        return service.markRead(caller, id);
    }

    @PostMapping("/mark-all-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal JwtPrincipal caller) {
        service.markAllRead(caller);
    }

    @PatchMapping("/{id}/dismiss")
    public NotificationDtos.View dismiss(@AuthenticationPrincipal JwtPrincipal caller,
                                         @PathVariable long id) {
        return service.dismiss(caller, id);
    }

    /* ── Invitation actions ── */

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        members.acceptInvitation(caller, id);
    }

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        members.declineInvitation(caller, id);
    }

    /* ── Real-time push (SSE) ──
     * Browser EventSource can't set Authorization headers, so we accept the
     * access token via ?access_token=…  The token is parsed and validated
     * inline; we deliberately do NOT rely on the JwtAuthFilter because it
     * looks for the Authorization header. Treats both flows the same way:
     * verify signature, extract userId, subscribe.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal JwtPrincipal headerCaller,
            @RequestParam(value = "access_token", required = false) String accessToken) {
        Long userId = headerCaller != null ? headerCaller.userId() : null;
        if (userId == null && accessToken != null) {
            JwtPrincipal p;
            try { p = tokens.parse(accessToken); }
            catch (Exception e) { throw ApiException.unauthorized("invalid access_token query param"); }
            userId = p.userId();
        }
        if (userId == null) throw ApiException.unauthorized("missing identity");
        return broker.subscribe(userId);
    }
}
