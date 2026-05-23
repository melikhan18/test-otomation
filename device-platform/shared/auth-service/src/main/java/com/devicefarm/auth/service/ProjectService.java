package com.devicefarm.auth.service;

import com.devicefarm.auth.api.dto.TenancyDtos;
import com.devicefarm.auth.domain.*;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Set<String> SLUG_RESERVED = Set.of("new", "default", "settings", "members", "admin", "api");

    private final CompanyRepository companies;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final TenancyAuthz authz;

    public ProjectService(CompanyRepository companies, ProjectRepository projects,
                          ProjectMemberRepository projectMembers, TenancyAuthz authz) {
        this.companies = companies;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.authz = authz;
    }

    /* ──────────────────────────── reads ──────────────────────────── */

    @Transactional(readOnly = true)
    public List<TenancyDtos.ProjectView> list(JwtPrincipal caller, long companyId) {
        User user = authz.requireUser(caller);
        authz.requireMembership(user, companyId);

        List<Project> all = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId);
        // OWNERs (and platform admins) see every project; everyone else only the
        // projects they have explicit grants on.
        if (!authz.isOwner(user, companyId)) {
            Set<Long> visible = projectMembers.findAllByUserIdAndCompanyId(user.getId(), companyId)
                    .stream().map(ProjectMember::getProjectId).collect(Collectors.toSet());
            all = all.stream().filter(p -> visible.contains(p.getId())).toList();
        }
        return all.stream().map(ProjectService::toView).toList();
    }

    @Transactional(readOnly = true)
    public TenancyDtos.ProjectView get(JwtPrincipal caller, long companyId, long projectId) {
        User user = authz.requireUser(caller);
        authz.requireProjectAccess(user, companyId, projectId);
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        return toView(p);
    }

    /* ──────────────────────────── writes ─────────────────────────── */

    /** Create a new project. Only OWNER can spin up new projects in the company. */
    @Transactional
    public TenancyDtos.ProjectView create(JwtPrincipal caller, long companyId, TenancyDtos.ProjectCreate req) {
        User user = authz.requireUser(caller);
        authz.requireOwner(user, companyId);

        if (!companies.existsById(companyId)) throw ApiException.notFound("company");

        String slug = (req.slug() == null || req.slug().isBlank())
                ? slugify(req.name())
                : req.slug().toLowerCase(Locale.ROOT);
        if (SLUG_RESERVED.contains(slug)) throw ApiException.badRequest("slug is reserved: " + slug);
        if (projects.existsByCompanyIdAndSlug(companyId, slug)) {
            throw ApiException.conflict("slug already used in this company");
        }
        Project p = new Project(companyId, slug, req.name().trim());
        p.setDescription(req.description());
        return toView(projects.save(p));
    }

    /** Rename + edit description. OWNER or QA_MANAGER on the project. */
    @Transactional
    public TenancyDtos.ProjectView update(JwtPrincipal caller, long companyId, long projectId,
                                          TenancyDtos.ProjectUpdate req) {
        User user = authz.requireUser(caller);
        authz.requireProjectManager(user, companyId, projectId);

        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        if (!p.getCompanyId().equals(companyId)) throw ApiException.notFound("project");
        p.setName(req.name().trim());
        p.setDescription(req.description());
        return toView(p);
    }

    /** Archive (soft delete). Only OWNER — destructive enough to need company-wide consent. */
    @Transactional
    public void archive(JwtPrincipal caller, long companyId, long projectId) {
        User user = authz.requireUser(caller);
        authz.requireOwner(user, companyId);

        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        if (!p.getCompanyId().equals(companyId)) throw ApiException.notFound("project");

        long active = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId).size();
        if (active <= 1) throw ApiException.badRequest("cannot archive the only project");

        p.setArchivedAt(Instant.now());
    }

    /* ─────────────────────────  helpers  ─────────────────────────── */

    private static TenancyDtos.ProjectView toView(Project p) {
        return new TenancyDtos.ProjectView(
                p.getId(), p.getCompanyId(), p.getSlug(), p.getName(),
                p.getDescription(), p.getCreatedAt(), p.getArchivedAt());
    }

    /** Lowercase, replace non-alphanumeric runs with single hyphen, trim hyphens. */
    static String slugify(String input) {
        if (input == null) return "p";
        String s = input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.isEmpty()) s = "p";
        if (s.length() > 64) s = s.substring(0, 64);
        return s;
    }
}
