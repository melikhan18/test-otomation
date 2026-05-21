package com.devicefarm.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTOs for company & project management endpoints. */
public class TenancyDtos {

    private static final String SLUG_PATTERN = "^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$";

    /* ─────────────────────────── Companies ──────────────────────────── */

    public record CompanyCreate(
            @NotBlank @Size(max = 128) String name,
            @Pattern(regexp = SLUG_PATTERN, message = "slug must be lowercase letters/digits/hyphens")
            String slug
    ) {}

    public record CompanyUpdate(
            @NotBlank @Size(max = 128) String name
    ) {}

    public record CompanyView(
            long id,
            String slug,
            String name,
            String role,             // OWNER | MEMBER | VIEWER (cosmetic — derived from caller's company_members row)
            int memberCount,
            int projectCount,
            Instant createdAt,
            Instant archivedAt
    ) {}

    /* ─────────────────────────── Projects ───────────────────────────── */

    public record ProjectCreate(
            @NotBlank @Size(max = 128) String name,
            @Pattern(regexp = SLUG_PATTERN, message = "slug must be lowercase letters/digits/hyphens")
            String slug,
            @Size(max = 2000) String description
    ) {}

    public record ProjectUpdate(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 2000) String description
    ) {}

    public record ProjectView(
            long id,
            long companyId,
            String slug,
            String name,
            String description,
            Instant createdAt,
            Instant archivedAt
    ) {}

    /* ─────────────────────────── Members ────────────────────────────── */

    /**
     * One row in the company-wide members table. Carries the OWNER flag plus
     * the per-project role list so the UI can render the access matrix.
     */
    public record MemberView(
            long userId,
            String username,
            String email,
            boolean owner,
            List<ProjectGrantView> grants,
            Instant joinedAt
    ) {}

    public record ProjectGrantView(
            long projectId,
            String projectSlug,
            String projectName,
            String role            // QA_MANAGER | TESTER
    ) {}

    /** One element in the grants matrix when inviting or editing. */
    public record ProjectGrantInput(
            @NotNull Long projectId,
            @NotBlank String role     // QA_MANAGER | TESTER
    ) {}

    /**
     * Direct-add by username payload. Either {@code owner=true} (add user as company OWNER,
     * grants ignored) or a non-empty list of per-project grants.
     */
    public record AddMember(
            @NotBlank String username,
            boolean owner,
            List<ProjectGrantInput> grants
    ) {}

    /**
     * Edit an existing member's access. Replaces the previous OWNER/grants set.
     */
    public record UpdateMember(
            boolean owner,
            List<ProjectGrantInput> grants
    ) {}

    /* ───── Per-project access dialog (manage a single project's members) ───── */

    public record ProjectMemberView(
            long userId,
            String username,
            String role,           // QA_MANAGER | TESTER (or OWNER for implicit-access rows)
            Instant addedAt
    ) {}

    public record AddProjectMember(
            @NotNull Long userId,
            @NotBlank String role
    ) {}

    public record UpdateProjectMemberRole(
            @NotBlank String role
    ) {}
}
