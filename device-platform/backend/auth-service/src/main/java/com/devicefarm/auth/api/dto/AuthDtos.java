package com.devicefarm.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public class AuthDtos {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /** Self-service signup. Email is mandatory so other users can invite the new account. */
    public record SignupRequest(
            @NotBlank @jakarta.validation.constraints.Size(min = 3, max = 64) String username,
            @NotBlank @jakarta.validation.constraints.Email String email,
            @NotBlank @jakarta.validation.constraints.Size(min = 8, max = 128) String password,
            /** Optional company name. If provided, a new company + default project is provisioned
             *  and the user becomes its OWNER. Otherwise they start without any company and rely
             *  on an admin to invite them. */
            String createCompanyName
    ) {}

    /** PATCH /api/auth/me body. Each field is optional — only non-null ones are applied. */
    public record ProfileUpdate(
            @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.Size(min = 8, max = 128) String newPassword,
            @jakarta.validation.constraints.NotBlank String currentPassword
    ) {}

    /**
     * Login result. {@code companies} carries every membership the user has so the
     * client can render the sidebar switcher without an extra round-trip; the
     * {@code productId} field is kept for legacy callers still scoping by product.
     */
    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            long userId,
            String username,
            String email,
            String role,
            boolean platformAdmin,
            long productId,
            List<CompanyMembership> companies
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    /** Used by /api/auth/me — same shape as login response minus the tokens. */
    public record CurrentUserResponse(
            long userId,
            String username,
            String email,
            String role,
            boolean platformAdmin,
            long productId,
            List<CompanyMembership> companies
    ) {}

    /**
     * One company the user belongs to + per-project role grants inside it.
     *
     * @param owner    true → company OWNER (implicit access to every active project).
     * @param projects every project the caller can see: OWNERs get the full list,
     *                 MEMBERs only see projects they have an explicit grant on.
     *                 {@code role} on each entry is "OWNER" (only when {@code owner=true})
     *                 or one of QA_MANAGER / TESTER.
     */
    public record CompanyMembership(
            long id,
            String slug,
            String name,
            boolean owner,
            List<ProjectAccess> projects,
            Instant joinedAt
    ) {}

    public record ProjectAccess(
            long id,
            String slug,
            String name,
            String role               // OWNER | QA_MANAGER | TESTER
    ) {}
}
