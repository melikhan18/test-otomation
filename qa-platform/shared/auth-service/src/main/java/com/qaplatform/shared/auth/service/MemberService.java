package com.qaplatform.shared.auth.service;

import com.qaplatform.shared.auth.api.dto.NotificationDtos;
import com.qaplatform.shared.auth.api.dto.TenancyDtos;
import com.qaplatform.shared.auth.domain.*;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Member management with project-scoped roles.
 *
 * Conceptually:
 *   - A company has OWNERs (full power) and MEMBERs (no implicit power).
 *   - A MEMBER gains capabilities by being assigned to projects in
 *     {@code project_members} with a per-project role (QA_MANAGER / TESTER).
 *   - Invites carry the same shape: either "make them OWNER" or "give them
 *     these project grants on accept".
 */
@Service
public class MemberService {

    private static final Set<String> PROJECT_ROLES = Set.of("QA_MANAGER", "TESTER");

    private final UserRepository users;
    private final CompanyMemberRepository companyMembers;
    private final CompanyRepository companies;
    private final CompanyInvitationRepository invitations;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final NotificationService notifications;
    private final TenancyAuthz authz;

    public MemberService(UserRepository users, CompanyMemberRepository companyMembers,
                         CompanyRepository companies, CompanyInvitationRepository invitations,
                         ProjectRepository projects, ProjectMemberRepository projectMembers,
                         NotificationService notifications, TenancyAuthz authz) {
        this.users = users;
        this.companyMembers = companyMembers;
        this.companies = companies;
        this.invitations = invitations;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.notifications = notifications;
        this.authz = authz;
    }

    /* ───────────────────── Invitations (email-based) ────────────────────── */

    /**
     * Send an invitation by email. The target must already have an account with
     * that email — we look them up and drop a {@code COMPANY_INVITATION}
     * notification onto their bell. Payload carries the OWNER flag and project
     * grants so accept can apply them in one shot.
     */
    @Transactional
    public TenancyDtos.MemberView invite(JwtPrincipal caller, long companyId,
                                         NotificationDtos.InviteByEmailRequest req) {
        User actor = authz.requireUser(caller);
        authz.requireOwner(actor, companyId);   // only OWNERs invite

        List<TenancyDtos.ProjectGrantInput> grants = sanitizeGrants(companyId, req.owner(), req.grants());

        User target = users.findByEmail(req.email())
                .orElseThrow(() -> ApiException.notFound("no user with that email"));
        if (companyMembers.findByUserIdAndCompanyId(target.getId(), companyId).isPresent()) {
            throw ApiException.conflict("user already a member");
        }
        if (invitations.findPendingByEmailAndCompany(req.email(), companyId).isPresent()) {
            throw ApiException.conflict("invitation already pending");
        }

        Company company = companies.findById(companyId).orElseThrow(() -> ApiException.notFound("company"));
        String role = req.owner() ? "OWNER" : "MEMBER";
        CompanyInvitation inv = invitations.save(new CompanyInvitation(
                companyId, req.email(), role, actor.getId(),
                Instant.now().plus(Duration.ofDays(7))));

        // Build notification payload: header info + serialised grants (with the
        // project's display name baked in) so the bell card can render
        // "Invited to Acme — QA Manager on BIP, Tester on Lifebox" without
        // needing a follow-up lookup on the recipient's side.
        Map<Long, Project> projectsById = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId)
                .stream().collect(Collectors.toMap(Project::getId, p -> p));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invitationId", inv.getId());
        payload.put("companyId", company.getId());
        payload.put("companySlug", company.getSlug());
        payload.put("companyName", company.getName());
        payload.put("owner", req.owner());
        payload.put("grants", grantsForPayload(grants, projectsById));
        payload.put("inviterUsername", actor.getUsername());
        payload.put("expiresAt", inv.getExpiresAt().toString());

        NotificationDtos.View nv =
                notifications.createForUser(target.getId(), "COMPANY_INVITATION", payload, actor.getId());
        inv.setNotificationId(nv.id());

        return memberView(target, false, List.of(), inv.getCreatedAt());
    }

    /** Invitee accepts → apply OWNER flag or project grants, mark notification ACCEPTED. */
    @Transactional
    public void acceptInvitation(JwtPrincipal caller, long notificationId) {
        User user = authz.requireUser(caller);
        CompanyInvitation inv = invitations.findByNotificationId(notificationId)
                .orElseThrow(() -> ApiException.notFound("invitation"));
        if (!inv.isPending()) throw ApiException.badRequest("already resolved");
        if (inv.getExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("invitation expired");
        // Confirm the recipient's email still matches the user — guards against
        // post-issue email changes on the user account.
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(inv.getEmail())) {
            throw ApiException.forbidden("not your invitation");
        }

        // Read grants out of the notification payload — invitation row doesn't
        // store them (kept normalized; payload is the source of truth).
        Map<String, Object> payload = notifications.findPayload(notificationId)
                .orElseThrow(() -> ApiException.notFound("notification"));
        boolean owner = Boolean.TRUE.equals(payload.get("owner"));
        List<TenancyDtos.ProjectGrantInput> grants = decodeGrants(payload.get("grants"));

        applyMembership(user.getId(), inv.getCompanyId(), owner, grants, user.getId());

        inv.markAccepted();
        notifications.resolve(caller, notificationId, Notification.STATUS_ACCEPTED);
    }

    @Transactional
    public void declineInvitation(JwtPrincipal caller, long notificationId) {
        User user = authz.requireUser(caller);
        CompanyInvitation inv = invitations.findByNotificationId(notificationId)
                .orElseThrow(() -> ApiException.notFound("invitation"));
        if (!inv.isPending()) throw ApiException.badRequest("already resolved");
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(inv.getEmail())) {
            throw ApiException.forbidden("not your invitation");
        }
        inv.markDeclined();
        notifications.resolve(caller, notificationId, Notification.STATUS_DECLINED);
    }

    /* ───────────────────── Company-wide members ────────────────────── */

    @Transactional(readOnly = true)
    public List<TenancyDtos.MemberView> listMembers(JwtPrincipal caller, long companyId) {
        User user = authz.requireUser(caller);
        authz.requireMembership(user, companyId);

        List<CompanyMember> all = companyMembers.findAllByCompanyId(companyId);
        if (all.isEmpty()) return List.of();

        // Bulk-fetch user info + every project_members row in the company so we
        // can build each MemberView in one pass.
        List<Long> userIds = all.stream().map(CompanyMember::getUserId).toList();
        Map<Long, User> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Project> projectById = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId)
                .stream().collect(Collectors.toMap(Project::getId, p -> p));
        Map<Long, List<ProjectMember>> grantsByUser = projectMembers.findAllByCompanyId(companyId)
                .stream().collect(Collectors.groupingBy(ProjectMember::getUserId));

        List<TenancyDtos.MemberView> out = new ArrayList<>(all.size());
        for (CompanyMember cm : all) {
            User u = userById.get(cm.getUserId());
            if (u == null) continue;
            boolean owner = "OWNER".equals(cm.getRole());
            List<TenancyDtos.ProjectGrantView> grants = owner
                    ? List.of()
                    : grantsByUser.getOrDefault(cm.getUserId(), List.of()).stream()
                            .map(pm -> {
                                Project p = projectById.get(pm.getProjectId());
                                return p == null ? null
                                        : new TenancyDtos.ProjectGrantView(p.getId(), p.getSlug(), p.getName(), pm.getRole());
                            })
                            .filter(Objects::nonNull).toList();
            out.add(memberView(u, owner, grants, cm.getJoinedAt()));
        }
        return out;
    }

    @Transactional
    public TenancyDtos.MemberView addMember(JwtPrincipal caller, long companyId, TenancyDtos.AddMember req) {
        User actor = authz.requireUser(caller);
        authz.requireOwner(actor, companyId);

        List<TenancyDtos.ProjectGrantInput> grants = sanitizeGrants(companyId, req.owner(), req.grants());

        User target = users.findByUsername(req.username())
                .orElseThrow(() -> ApiException.notFound("user '" + req.username() + "' not found"));
        if (companyMembers.findByUserIdAndCompanyId(target.getId(), companyId).isPresent()) {
            throw ApiException.conflict("user already a member");
        }

        applyMembership(target.getId(), companyId, req.owner(), grants, actor.getId());
        return loadMemberView(target.getId(), companyId);
    }

    /**
     * Replace an existing member's access set. Used by the matrix editor — UI
     * sends the desired OWNER flag + full grants list and we reconcile.
     */
    @Transactional
    public TenancyDtos.MemberView updateMember(JwtPrincipal caller, long companyId, long userId,
                                                TenancyDtos.UpdateMember req) {
        User actor = authz.requireUser(caller);
        authz.requireOwner(actor, companyId);

        CompanyMember existing = companyMembers.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> ApiException.notFound("membership"));

        // Last-OWNER safety on demotion.
        if ("OWNER".equals(existing.getRole()) && !req.owner()) {
            long owners = companyMembers.countByCompanyIdAndRole(companyId, "OWNER");
            if (owners <= 1) throw ApiException.badRequest("cannot demote the only OWNER");
        }

        List<TenancyDtos.ProjectGrantInput> grants = sanitizeGrants(companyId, req.owner(), req.grants());
        applyMembership(userId, companyId, req.owner(), grants, actor.getId());
        return loadMemberView(userId, companyId);
    }

    @Transactional
    public void removeMember(JwtPrincipal caller, long companyId, long userId) {
        User actor = authz.requireUser(caller);
        authz.requireOwner(actor, companyId);

        CompanyMember m = companyMembers.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> ApiException.notFound("membership"));

        if ("OWNER".equals(m.getRole())) {
            long owners = companyMembers.countByCompanyIdAndRole(companyId, "OWNER");
            if (owners <= 1) throw ApiException.badRequest("cannot remove the only OWNER");
        }

        projectMembers.deleteAllByUserIdAndCompanyId(userId, companyId);
        companyMembers.deleteByUserIdAndCompanyId(userId, companyId);
    }

    /* ───────────────────── Per-project members ────────────────────── */

    @Transactional(readOnly = true)
    public List<TenancyDtos.ProjectMemberView> listProjectMembers(JwtPrincipal caller, long companyId, long projectId) {
        User user = authz.requireUser(caller);
        authz.requireProjectAccess(user, companyId, projectId);
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        if (!p.getCompanyId().equals(companyId)) throw ApiException.notFound("project");

        Map<Long, CompanyMember> byUser = companyMembers.findAllByCompanyId(companyId).stream()
                .collect(Collectors.toMap(CompanyMember::getUserId, x -> x));
        List<TenancyDtos.ProjectMemberView> out = new ArrayList<>();

        // Implicit access rows: every OWNER appears in the list as "OWNER".
        for (CompanyMember cm : byUser.values()) {
            if ("OWNER".equals(cm.getRole())) {
                User u = users.findById(cm.getUserId()).orElse(null);
                if (u != null) {
                    out.add(new TenancyDtos.ProjectMemberView(u.getId(), u.getUsername(), "OWNER", cm.getJoinedAt()));
                }
            }
        }
        // Explicit project grants.
        for (ProjectMember pm : projectMembers.findAllByProjectId(projectId)) {
            User u = users.findById(pm.getUserId()).orElse(null);
            if (u != null) {
                out.add(new TenancyDtos.ProjectMemberView(u.getId(), u.getUsername(), pm.getRole(), pm.getAddedAt()));
            }
        }
        return out;
    }

    /** All projects (within the company) the user has an explicit grant on, with their role. */
    @Transactional(readOnly = true)
    public List<TenancyDtos.ProjectGrantView> listUserGrants(JwtPrincipal caller, long companyId, long userId) {
        User user = authz.requireUser(caller);
        authz.requireMembership(user, companyId);

        Map<Long, Project> projectById = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId)
                .stream().collect(Collectors.toMap(Project::getId, p -> p));
        return projectMembers.findAllByUserIdAndCompanyId(userId, companyId).stream()
                .map(pm -> {
                    Project p = projectById.get(pm.getProjectId());
                    return p == null ? null
                            : new TenancyDtos.ProjectGrantView(p.getId(), p.getSlug(), p.getName(), pm.getRole());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public TenancyDtos.ProjectMemberView addProjectMember(JwtPrincipal caller, long companyId, long projectId,
                                                          TenancyDtos.AddProjectMember req) {
        User actor = authz.requireUser(caller);
        authz.requireProjectManager(actor, companyId, projectId);
        validateProjectRole(req.role());

        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        if (!p.getCompanyId().equals(companyId)) throw ApiException.notFound("project");

        CompanyMember cm = companyMembers.findByUserIdAndCompanyId(req.userId(), companyId)
                .orElseThrow(() -> ApiException.badRequest("target must be a company member first"));
        if ("OWNER".equals(cm.getRole())) {
            throw ApiException.badRequest("OWNERs already have implicit access");
        }
        if (projectMembers.existsByUserIdAndProjectId(req.userId(), projectId)) {
            throw ApiException.conflict("already a project member");
        }
        ProjectMember pm = projectMembers.save(
                new ProjectMember(req.userId(), projectId, req.role(), actor.getId()));
        User u = users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("user"));
        return new TenancyDtos.ProjectMemberView(u.getId(), u.getUsername(), pm.getRole(), pm.getAddedAt());
    }

    @Transactional
    public TenancyDtos.ProjectMemberView updateProjectMemberRole(JwtPrincipal caller, long companyId, long projectId,
                                                                  long userId, TenancyDtos.UpdateProjectMemberRole req) {
        User actor = authz.requireUser(caller);
        authz.requireProjectManager(actor, companyId, projectId);
        validateProjectRole(req.role());

        // A user changing their own role is the classic privilege-escalation
        // footgun (TESTER → QA_MANAGER while logged in). Refuse outright; the
        // company OWNER can still adjust it through MembersPage.
        if (actor.getId().equals(userId)) {
            throw ApiException.badRequest("you can't change your own role");
        }

        ProjectMember pm = projectMembers.findByUserIdAndProjectId(userId, projectId)
                .orElseThrow(() -> ApiException.notFound("project membership"));
        pm.setRole(req.role());
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
        return new TenancyDtos.ProjectMemberView(u.getId(), u.getUsername(), pm.getRole(), pm.getAddedAt());
    }

    @Transactional
    public void removeProjectMember(JwtPrincipal caller, long companyId, long projectId, long userId) {
        User actor = authz.requireUser(caller);
        authz.requireProjectManager(actor, companyId, projectId);

        // Same guard rationale as updateProjectMemberRole: don't let a member
        // self-remove. The OWNER (or another manager) can do it for them.
        if (actor.getId().equals(userId)) {
            throw ApiException.badRequest("you can't remove yourself");
        }

        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        if (!p.getCompanyId().equals(companyId)) throw ApiException.notFound("project");
        projectMembers.deleteAllByUserIdAndProjectId(userId, projectId);
    }

    /* ─────────────────────────  helpers  ───────────────────────── */

    private TenancyDtos.MemberView memberView(User u, boolean owner,
                                              List<TenancyDtos.ProjectGrantView> grants,
                                              Instant joinedAt) {
        return new TenancyDtos.MemberView(u.getId(), u.getUsername(), u.getEmail(), owner, grants, joinedAt);
    }

    private TenancyDtos.MemberView loadMemberView(long userId, long companyId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
        CompanyMember cm = companyMembers.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> ApiException.notFound("membership"));
        boolean owner = "OWNER".equals(cm.getRole());
        List<TenancyDtos.ProjectGrantView> grants;
        if (owner) {
            grants = List.of();
        } else {
            // Build grants inline — the outer transaction already verified that the
            // caller can see this membership, so no need to re-run authz here.
            Map<Long, Project> byId = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId)
                    .stream().collect(Collectors.toMap(Project::getId, p -> p));
            grants = projectMembers.findAllByUserIdAndCompanyId(userId, companyId).stream()
                    .map(pm -> {
                        Project p = byId.get(pm.getProjectId());
                        return p == null ? null
                                : new TenancyDtos.ProjectGrantView(p.getId(), p.getSlug(), p.getName(), pm.getRole());
                    })
                    .filter(Objects::nonNull).toList();
        }
        return memberView(u, owner, grants, cm.getJoinedAt());
    }

    private void applyMembership(long userId, long companyId, boolean owner,
                                 List<TenancyDtos.ProjectGrantInput> grants, long actorId) {
        // Upsert company_members.
        CompanyMember cm = companyMembers.findByUserIdAndCompanyId(userId, companyId).orElse(null);
        if (cm == null) {
            companyMembers.save(new CompanyMember(userId, companyId, owner ? "OWNER" : "MEMBER"));
        } else {
            cm.setRole(owner ? "OWNER" : "MEMBER");
            companyMembers.save(cm);
        }
        // Wipe + re-insert grants. OWNERs get no rows (implicit access).
        projectMembers.deleteAllByUserIdAndCompanyId(userId, companyId);
        if (!owner && grants != null) {
            for (TenancyDtos.ProjectGrantInput g : grants) {
                projectMembers.save(new ProjectMember(userId, g.projectId(), g.role(), actorId));
            }
        }
    }

    /**
     * Validate grants & strip duplicates. Each project must belong to the company
     * and the role must be one of QA_MANAGER / TESTER. If {@code owner=true} we
     * always return an empty list (OWNERs need no grants).
     */
    private List<TenancyDtos.ProjectGrantInput> sanitizeGrants(long companyId, boolean owner,
                                                                List<TenancyDtos.ProjectGrantInput> grants) {
        if (owner) return List.of();
        if (grants == null || grants.isEmpty()) {
            throw ApiException.badRequest("either owner=true or at least one project grant is required");
        }
        Set<Long> companyProjectIds = projects.findAllByCompanyIdAndArchivedAtIsNullOrderByName(companyId)
                .stream().map(Project::getId).collect(Collectors.toSet());
        Map<Long, TenancyDtos.ProjectGrantInput> deduped = new LinkedHashMap<>();
        for (TenancyDtos.ProjectGrantInput g : grants) {
            if (g.projectId() == null || g.role() == null) {
                throw ApiException.badRequest("grant entry missing projectId or role");
            }
            if (!companyProjectIds.contains(g.projectId())) {
                throw ApiException.badRequest("project " + g.projectId() + " not in this company");
            }
            validateProjectRole(g.role());
            deduped.put(g.projectId(), g);   // last one wins for dupes
        }
        return List.copyOf(deduped.values());
    }

    private static void validateProjectRole(String role) {
        if (role == null || !PROJECT_ROLES.contains(role)) {
            throw ApiException.badRequest("project role must be one of " + PROJECT_ROLES);
        }
    }

    private static List<Map<String, Object>> grantsForPayload(List<TenancyDtos.ProjectGrantInput> grants,
                                                                Map<Long, Project> projectsById) {
        if (grants == null) return List.of();
        return grants.stream().<Map<String, Object>>map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", g.projectId());
            Project p = projectsById.get(g.projectId());
            if (p != null) {
                m.put("projectSlug", p.getSlug());
                m.put("projectName", p.getName());
            }
            m.put("role", g.role());
            return m;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<TenancyDtos.ProjectGrantInput> decodeGrants(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) return List.of();
        List<TenancyDtos.ProjectGrantInput> out = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object pid = m.get("projectId");
            Object role = m.get("role");
            if (pid instanceof Number n && role instanceof String r) {
                out.add(new TenancyDtos.ProjectGrantInput(n.longValue(), r));
            }
        }
        return out;
    }
}
