package com.devicefarm.auth.service;

import com.devicefarm.auth.api.dto.AdminUserDtos;
import com.devicefarm.auth.domain.*;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-admin-only user CRUD. Distinct from {@link MemberService} (which
 * scopes to a company); this view is global across all tenants and lets the
 * vendor's operations team create / fix-up accounts.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final CompanyMemberRepository companyMembers;
    private final ProductRepository products;
    private final PasswordEncoder encoder;

    public AdminUserService(UserRepository users, CompanyMemberRepository companyMembers,
                            ProductRepository products, PasswordEncoder encoder) {
        this.users = users;
        this.companyMembers = companyMembers;
        this.products = products;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDtos.View> list(JwtPrincipal caller) {
        requirePlatformAdmin(caller);
        List<User> all = users.findAll();
        Map<Long, Long> companyCounts = new HashMap<>();
        for (CompanyMember cm : companyMembers.findAll()) {
            companyCounts.merge(cm.getUserId(), 1L, Long::sum);
        }
        return all.stream().map(u -> toView(u, companyCounts.getOrDefault(u.getId(), 0L).intValue())).toList();
    }

    @Transactional
    public AdminUserDtos.View create(JwtPrincipal caller, AdminUserDtos.Create req) {
        requirePlatformAdmin(caller);
        if (users.existsByUsername(req.username())) throw ApiException.conflict("username already taken");
        if (req.email() != null && !req.email().isBlank()) {
            users.findByEmail(req.email().trim().toLowerCase())
                    .ifPresent(u -> { throw ApiException.conflict("email already in use"); });
        }
        Long productId = products.findByCode("DEFAULT").map(Product::getId).orElse(1L);
        User u = new User(req.username().trim(), encoder.encode(req.password()), productId, "USER");
        u.setEmail(req.email());
        u.setPlatformAdmin(req.platformAdmin());
        u = users.save(u);
        return toView(u, 0);
    }

    @Transactional
    public AdminUserDtos.View update(JwtPrincipal caller, long userId, AdminUserDtos.Update req) {
        requirePlatformAdmin(caller);
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
        if (req.email() != null) {
            String norm = req.email().trim().toLowerCase();
            users.findByEmail(norm).ifPresent(other -> {
                if (!other.getId().equals(u.getId())) throw ApiException.conflict("email already in use");
            });
            u.setEmail(norm.isEmpty() ? null : norm);
        }
        if (req.enabled() != null) u.setEnabled(req.enabled());
        if (req.platformAdmin() != null) {
            // Don't allow the caller to demote themselves to non-admin via this endpoint
            // (footgun: they'd lose access to the very page they're on).
            if (caller.userId() != null && caller.userId().equals(userId) && !req.platformAdmin()) {
                throw ApiException.badRequest("cannot demote yourself");
            }
            u.setPlatformAdmin(req.platformAdmin());
        }
        users.save(u);
        long companyCount = companyMembers.findAllByUserId(u.getId()).size();
        return toView(u, (int) companyCount);
    }

    @Transactional
    public void resetPassword(JwtPrincipal caller, long userId, AdminUserDtos.PasswordReset req) {
        requirePlatformAdmin(caller);
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
    }

    /* ──────────────────────  helpers  ─────────────────────── */

    private void requirePlatformAdmin(JwtPrincipal caller) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        // We re-check against the DB rather than trusting only the JWT claim — staff
        // demotions should take effect even on still-valid tokens.
        User u = users.findById(caller.userId())
                .orElseThrow(() -> ApiException.unauthorized("user not found"));
        if (!u.isPlatformAdmin()) throw ApiException.forbidden("platform admin only");
    }

    private AdminUserDtos.View toView(User u, int companyCount) {
        return new AdminUserDtos.View(
                u.getId(), u.getUsername(), u.getEmail(), u.getRole(),
                u.isPlatformAdmin(), u.isEnabled(), u.getCreatedAt(), companyCount);
    }
}
