package com.devicefarm.auth.service;

import com.devicefarm.auth.api.dto.AuthDtos;
import com.devicefarm.auth.domain.*;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtProperties;
import com.devicefarm.common.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final CompanyRepository companies;
    private final CompanyMemberRepository companyMembers;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final JwtProperties jwtProps;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       CompanyRepository companies, CompanyMemberRepository companyMembers,
                       ProjectRepository projects, ProjectMemberRepository projectMembers,
                       ProductRepository products,
                       PasswordEncoder encoder, JwtTokenService tokens, JwtProperties jwtProps) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.companies = companies;
        this.companyMembers = companyMembers;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.products = products;
        this.encoder = encoder;
        this.tokens = tokens;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public AuthDtos.LoginResponse signup(AuthDtos.SignupRequest req) {
        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();

        if (users.existsByUsername(username)) {
            throw ApiException.conflict("username already taken");
        }
        users.findByEmail(email).ifPresent(u -> { throw ApiException.conflict("email already registered"); });

        // New users always start under the default product so the legacy
        // product_id column stays satisfied. Tenancy is now driven by
        // company_members + project_members, so productId is cosmetic.
        Long defaultProductId = legacyDefaultProductId();
        User user = new User(username, encoder.encode(req.password()), defaultProductId, "USER");
        user.setEmail(email);
        user = users.save(user);

        // Optional bootstrap company so the new user has somewhere to start.
        // Caller becomes the company OWNER — OWNER has implicit access to every
        // project in the company, so we don't write a project_members row here.
        if (req.createCompanyName() != null && !req.createCompanyName().isBlank()) {
            String slug = ProjectService.slugify(req.createCompanyName());
            // Slug collision is rare here (companies share a global namespace) but
            // we suffix with the user id rather than failing — onboarding shouldn't
            // hit a dead end because someone else picked "acme" first.
            if (companies.existsBySlug(slug)) slug = slug + "-" + user.getId();
            Company c = companies.save(new Company(slug, req.createCompanyName().trim()));
            companyMembers.save(new CompanyMember(user.getId(), c.getId(), "OWNER"));
            projects.save(new Project(c.getId(), "default", "Default"));
        }

        return issueLoginResponse(user);
    }

    /** Best-effort lookup of the legacy default product id. Falls back to 1. */
    private long legacyDefaultProductId() {
        return products.findByCode("DEFAULT").map(Product::getId).orElse(1L);
    }

    @Transactional
    public AuthDtos.LoginResponse login(String username, String password) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("invalid credentials"));
        if (!user.isEnabled()) throw ApiException.unauthorized("user disabled");
        if (!encoder.matches(password, user.getPasswordHash()))
            throw ApiException.unauthorized("invalid credentials");

        return issueLoginResponse(user);
    }

    @Transactional
    public AuthDtos.LoginResponse refresh(String refreshTokenJwt) {
        Claims claims;
        try {
            claims = tokens.parseClaims(refreshTokenJwt);
        } catch (JwtTokenService.InvalidJwtException e) {
            throw ApiException.unauthorized("invalid refresh token");
        }
        if (!"refresh".equals(claims.get("type"))) {
            throw ApiException.unauthorized("not a refresh token");
        }

        String hash = sha256(refreshTokenJwt);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("refresh token unknown"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("refresh token expired/revoked");
        }

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("user not found"));

        // rotate
        stored.revoke();
        return issueLoginResponse(user);
    }

    /** /api/auth/me — same memberships shape as login, no tokens. */
    @Transactional(readOnly = true)
    public AuthDtos.CurrentUserResponse currentUser(long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("user not found"));
        List<AuthDtos.CompanyMembership> memberships = loadMembershipsForApi(user);
        return new AuthDtos.CurrentUserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isPlatformAdmin(), user.getProductId(), memberships);
    }

    /**
     * Profile self-update. Current password is required as a soft re-auth so a
     * stolen access token can't repurpose the account by silently flipping the
     * recovery email.
     */
    @Transactional
    public AuthDtos.CurrentUserResponse updateProfile(long userId, AuthDtos.ProfileUpdate req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("user not found"));
        if (req.currentPassword() == null || !encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("current password is wrong");
        }
        boolean dirty = false;
        if (req.email() != null && !req.email().isBlank()) {
            String normalised = req.email().trim().toLowerCase();
            if (!normalised.equals(user.getEmail())) {
                // Uniqueness guard — relies on the partial unique index from V4.
                users.findByEmail(normalised).ifPresent(other -> {
                    if (!other.getId().equals(user.getId())) {
                        throw ApiException.conflict("email already in use");
                    }
                });
                user.setEmail(normalised);
                dirty = true;
            }
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            user.setPasswordHash(encoder.encode(req.newPassword()));
            dirty = true;
        }
        if (dirty) users.save(user);
        return currentUser(user.getId());
    }

    /* ─────────────────────────  helpers  ───────────────────────── */

    private AuthDtos.LoginResponse issueLoginResponse(User user) {
        List<CompanyMember> rawMemberships = companyMembers.findAllByUserId(user.getId());
        List<AuthDtos.CompanyMembership> apiMemberships = buildApiMemberships(user, rawMemberships);
        List<JwtPrincipal.CompanyMembership> jwtMemberships = buildJwtMemberships(user, rawMemberships);

        String access = tokens.issueUserAccessToken(
                user.getId(), user.getRole(), user.getProductId(),
                user.isPlatformAdmin(), jwtMemberships);
        String refresh = issueRefreshToken(user);

        return new AuthDtos.LoginResponse(
                access, refresh,
                jwtProps.getAccessTokenTtl().toSeconds(),
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isPlatformAdmin(), user.getProductId(),
                apiMemberships);
    }

    private List<AuthDtos.CompanyMembership> loadMembershipsForApi(User user) {
        return buildApiMemberships(user, companyMembers.findAllByUserId(user.getId()));
    }

    /**
     * Resolve each {@link CompanyMember} into a rich API representation: name,
     * slug, owner flag, and per-project grants. An OWNER's project list contains
     * every active project in the company; a MEMBER's list only contains rows
     * from {@code project_members} (their QA_MANAGER / TESTER assignments).
     *
     * Platform admins additionally see every active company on the platform as
     * an implicit OWNER row, so the workspace switcher lets them jump into any
     * tenant without manual seeding.
     */
    private List<AuthDtos.CompanyMembership> buildApiMemberships(User user, List<CompanyMember> memberships) {
        Map<Long, String> roleByProjectId = projectMembers.findAllByUserId(user.getId()).stream()
                .collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getRole));

        // Real memberships keyed by company.
        Map<Long, CompanyMember> mineByCompany = memberships.stream()
                .collect(Collectors.toMap(CompanyMember::getCompanyId, m -> m, (a, b) -> a));

        // Universe of companies the user should see in the switcher.
        List<Company> visible;
        if (user.isPlatformAdmin()) {
            visible = companies.findAllByArchivedAtIsNull();
        } else if (mineByCompany.isEmpty()) {
            return List.of();
        } else {
            visible = companies.findAllByIdInAndArchivedAtIsNull(List.copyOf(mineByCompany.keySet()));
        }

        List<AuthDtos.CompanyMembership> out = new ArrayList<>(visible.size());
        for (Company c : visible) {
            CompanyMember m = mineByCompany.get(c.getId());
            // Platform admin without a row is treated as OWNER everywhere.
            boolean owner = user.isPlatformAdmin() || (m != null && "OWNER".equals(m.getRole()));
            Instant joinedAt = m != null ? m.getJoinedAt() : c.getCreatedAt();
            List<Project> companyProjects = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(c.getId());

            List<AuthDtos.ProjectAccess> grants = new ArrayList<>();
            for (Project p : companyProjects) {
                if (owner) {
                    grants.add(new AuthDtos.ProjectAccess(p.getId(), p.getSlug(), p.getName(), "OWNER"));
                } else {
                    String role = roleByProjectId.get(p.getId());
                    if (role != null) {
                        grants.add(new AuthDtos.ProjectAccess(p.getId(), p.getSlug(), p.getName(), role));
                    }
                }
            }
            out.add(new AuthDtos.CompanyMembership(
                    c.getId(), c.getSlug(), c.getName(),
                    owner, grants, joinedAt));
        }
        return out;
    }

    private List<JwtPrincipal.CompanyMembership> buildJwtMemberships(User user, List<CompanyMember> memberships) {
        Map<Long, String> roleByProjectId = projectMembers.findAllByUserId(user.getId()).stream()
                .collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getRole));
        Map<Long, CompanyMember> mineByCompany = memberships.stream()
                .collect(Collectors.toMap(CompanyMember::getCompanyId, m -> m, (a, b) -> a));

        List<Company> visible;
        if (user.isPlatformAdmin()) {
            visible = companies.findAllByArchivedAtIsNull();
        } else if (mineByCompany.isEmpty()) {
            return List.of();
        } else {
            visible = companies.findAllByIdInAndArchivedAtIsNull(List.copyOf(mineByCompany.keySet()));
        }

        List<JwtPrincipal.CompanyMembership> out = new ArrayList<>(visible.size());
        for (Company c : visible) {
            CompanyMember m = mineByCompany.get(c.getId());
            boolean owner = user.isPlatformAdmin() || (m != null && "OWNER".equals(m.getRole()));
            List<JwtPrincipal.ProjectGrant> grants;
            if (owner) {
                // OWNERs (and platform admins) have implicit access — keep the
                // grants list empty so downstream services treat them as such.
                grants = List.of();
            } else {
                List<Long> activeIds = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(c.getId())
                        .stream().map(Project::getId).toList();
                grants = new ArrayList<>();
                for (Long pid : activeIds) {
                    String role = roleByProjectId.get(pid);
                    if (role != null) grants.add(new JwtPrincipal.ProjectGrant(pid, role));
                }
            }
            out.add(new JwtPrincipal.CompanyMembership(c.getId(), c.getSlug(), owner, grants));
        }
        return out;
    }

    private String issueRefreshToken(User user) {
        String token = tokens.issueRefreshToken(user.getId(), user.getProductId());
        Instant expires = Instant.now().plus(jwtProps.getRefreshTokenTtl());
        refreshTokens.save(new RefreshToken(user.getId(), sha256(token), expires));
        return token;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
