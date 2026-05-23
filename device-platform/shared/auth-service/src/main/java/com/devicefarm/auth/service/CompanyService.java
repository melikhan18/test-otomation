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

@Service
public class CompanyService {

    private static final Set<String> SLUG_RESERVED =
            Set.of("new", "settings", "admin", "api", "login", "signup");

    private final CompanyRepository companies;
    private final CompanyMemberRepository companyMembers;
    private final ProjectRepository projects;
    private final TenancyAuthz authz;

    public CompanyService(CompanyRepository companies, CompanyMemberRepository companyMembers,
                          ProjectRepository projects, TenancyAuthz authz) {
        this.companies = companies;
        this.companyMembers = companyMembers;
        this.projects = projects;
        this.authz = authz;
    }

    /** Companies the caller belongs to. Platform admins see everything. Archived hidden. */
    @Transactional(readOnly = true)
    public List<TenancyDtos.CompanyView> listMine(JwtPrincipal caller) {
        User user = authz.requireUser(caller);
        List<Company> visible;
        if (user.isPlatformAdmin()) {
            visible = companies.findAllByArchivedAtIsNull();
        } else {
            List<Long> ids = companyMembers.findAllByUserId(user.getId()).stream()
                    .map(CompanyMember::getCompanyId).toList();
            visible = ids.isEmpty() ? List.of() : companies.findAllByIdInAndArchivedAtIsNull(ids);
        }
        return visible.stream().map(c -> toView(c, user)).toList();
    }

    @Transactional(readOnly = true)
    public TenancyDtos.CompanyView get(JwtPrincipal caller, long id) {
        User user = authz.requireUser(caller);
        authz.requireMembership(user, id);
        Company c = companies.findById(id).orElseThrow(() -> ApiException.notFound("company"));
        return toView(c, user);
    }

    /**
     * Create a new company. The caller automatically becomes the {@code OWNER} and a
     * "default" project is provisioned so they can start working immediately.
     *
     * Slug handling
     * ─────────────
     * If the caller left {@code slug} blank we derive it from the name and append
     * a numeric suffix when there's a collision (archived companies still reserve
     * the slug at the DB level). If the caller typed a slug explicitly we treat
     * the collision as an intentional conflict and surface a 409.
     */
    @Transactional
    public TenancyDtos.CompanyView create(JwtPrincipal caller, TenancyDtos.CompanyCreate req) {
        User user = authz.requireUser(caller);

        boolean userProvidedSlug = req.slug() != null && !req.slug().isBlank();
        String slug = userProvidedSlug
                ? req.slug().toLowerCase(Locale.ROOT)
                : ProjectService.slugify(req.name());
        if (SLUG_RESERVED.contains(slug)) throw ApiException.badRequest("slug is reserved: " + slug);

        if (companies.existsBySlug(slug)) {
            if (userProvidedSlug) {
                throw ApiException.conflict("slug already taken");
            }
            // Auto-suffix so onboarding doesn't dead-end on archived/peer collisions.
            String base = slug;
            int n = 2;
            while (companies.existsBySlug(slug)) {
                slug = base + "-" + n++;
                if (n > 999) throw ApiException.conflict("could not allocate a free slug");
            }
        }

        Company c = companies.save(new Company(slug, req.name().trim()));
        companyMembers.save(new CompanyMember(user.getId(), c.getId(), "OWNER"));
        // Day-zero usability: every company starts with a Default project.
        projects.save(new Project(c.getId(), "default", "Default"));
        return toView(c, user);
    }

    @Transactional
    public TenancyDtos.CompanyView update(JwtPrincipal caller, long id, TenancyDtos.CompanyUpdate req) {
        User user = authz.requireUser(caller);
        authz.requireOwner(user, id);

        Company c = companies.findById(id).orElseThrow(() -> ApiException.notFound("company"));
        if (c.getArchivedAt() != null) throw ApiException.badRequest("company is archived");
        c.setName(req.name().trim());
        return toView(c, user);
    }

    /**
     * Archive (soft delete) a company. Only OWNER can do this. Members will see it
     * disappear from their workspace switcher on next reload; existing tokens that
     * still hold the company id will hit 403 once they re-fetch memberships.
     *
     * Cascades to every active project under the company so project-scoped queries
     * (workspace tree, project lookups in device/automation services) stop returning
     * the orphans even if the company is ever restored.
     */
    @Transactional
    public void archive(JwtPrincipal caller, long id) {
        User user = authz.requireUser(caller);
        authz.requireOwner(user, id);

        Company c = companies.findById(id).orElseThrow(() -> ApiException.notFound("company"));
        if (c.getArchivedAt() != null) throw ApiException.badRequest("company already archived");
        Instant now = Instant.now();
        c.setArchivedAt(now);
        for (Project p : projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(id)) {
            p.setArchivedAt(now);
        }
    }

    /* ──────────────────────  Admin (platform-wide) ────────────────── */

    /**
     * Every company on the platform — including archived ones. Used by the
     * /admin/companies page. Platform-admin only.
     */
    @Transactional(readOnly = true)
    public List<TenancyDtos.CompanyView> listAllForAdmin(JwtPrincipal caller) {
        User user = authz.requireUser(caller);
        if (!user.isPlatformAdmin()) throw ApiException.forbidden("platform admin only");
        return companies.findAll().stream().map(c -> toView(c, user)).toList();
    }

    /**
     * Reverse {@link #archive}. Restoring a company also restores its projects so
     * the workspace is usable immediately; OWNERs can re-archive specific projects
     * later if they want a clean slate.
     */
    @Transactional
    public TenancyDtos.CompanyView unarchive(JwtPrincipal caller, long id) {
        User user = authz.requireUser(caller);
        if (!user.isPlatformAdmin()) throw ApiException.forbidden("platform admin only");

        Company c = companies.findById(id).orElseThrow(() -> ApiException.notFound("company"));
        if (c.getArchivedAt() == null) return toView(c, user);
        c.setArchivedAt(null);
        for (Project p : projects.findAllByCompanyIdOrderByName(id)) {
            // Only restore projects that were archived as part of the company cascade.
            // A standalone projectArchive flow could have predated the company archive,
            // but we can't distinguish — pragmatic choice: bring them all back.
            p.setArchivedAt(null);
        }
        return toView(c, user);
    }

    /* ──────────────────────  helpers  ─────────────────────── */

    private TenancyDtos.CompanyView toView(Company c, User caller) {
        // Post-refactor company role is OWNER or MEMBER (project-level roles live
        // on project_members). Platform admins are treated as OWNER everywhere.
        String role = caller.isPlatformAdmin()
                ? "OWNER"
                : companyMembers.findByUserIdAndCompanyId(caller.getId(), c.getId())
                    .map(CompanyMember::getRole).orElse("VIEWER");
        int memberCount  = (int) companyMembers.findAllByCompanyId(c.getId()).size();
        int projectCount = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(c.getId()).size();
        return new TenancyDtos.CompanyView(
                c.getId(), c.getSlug(), c.getName(), role,
                memberCount, projectCount, c.getCreatedAt(), c.getArchivedAt());
    }
}
