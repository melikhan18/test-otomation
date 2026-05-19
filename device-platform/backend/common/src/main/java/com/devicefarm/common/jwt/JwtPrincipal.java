package com.devicefarm.common.jwt;

/**
 * Authenticated principal extracted from a verified JWT.
 *
 * @param subject  raw "sub" claim — for users "user:{id}", for agents "device:{id}",
 *                 for sessions "session:{id}"
 * @param userId   set when token represents a human user (null otherwise)
 * @param deviceId set when token represents an agent or session bound to a device
 * @param sessionId set when token is a session token
 * @param role     USER | ADMIN | AGENT | SESSION
 * @param productId tenant boundary, always present
 */
public record JwtPrincipal(
        String subject,
        Long userId,
        Long deviceId,
        Long sessionId,
        String role,
        Long productId
) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean isAgent() { return "AGENT".equals(role); }
    public boolean isUser()  { return "USER".equals(role) || "ADMIN".equals(role); }
}
