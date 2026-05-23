package com.qaplatform.shared.auth.api;

import com.qaplatform.shared.auth.api.dto.TenancyDtos;
import com.qaplatform.shared.auth.service.MemberService;
import com.qaplatform.shared.auth.service.ProjectService;
import com.qaplatform.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/projects")
public class ProjectController {

    private final ProjectService projects;
    private final MemberService members;

    public ProjectController(ProjectService projects, MemberService members) {
        this.projects = projects;
        this.members = members;
    }

    /* ───── Project CRUD ───── */

    @GetMapping
    public List<TenancyDtos.ProjectView> list(@AuthenticationPrincipal JwtPrincipal caller,
                                              @PathVariable long companyId) {
        return projects.list(caller, companyId);
    }

    @GetMapping("/{id}")
    public TenancyDtos.ProjectView get(@AuthenticationPrincipal JwtPrincipal caller,
                                       @PathVariable long companyId, @PathVariable long id) {
        return projects.get(caller, companyId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenancyDtos.ProjectView create(@AuthenticationPrincipal JwtPrincipal caller,
                                          @PathVariable long companyId,
                                          @RequestBody @Valid TenancyDtos.ProjectCreate req) {
        return projects.create(caller, companyId, req);
    }

    @PutMapping("/{id}")
    public TenancyDtos.ProjectView update(@AuthenticationPrincipal JwtPrincipal caller,
                                          @PathVariable long companyId, @PathVariable long id,
                                          @RequestBody @Valid TenancyDtos.ProjectUpdate req) {
        return projects.update(caller, companyId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal JwtPrincipal caller,
                        @PathVariable long companyId, @PathVariable long id) {
        projects.archive(caller, companyId, id);
    }

    /* ───── Project members (TESTER assignment) ───── */

    @GetMapping("/{id}/members")
    public List<TenancyDtos.ProjectMemberView> listMembers(@AuthenticationPrincipal JwtPrincipal caller,
                                                            @PathVariable long companyId,
                                                            @PathVariable long id) {
        return members.listProjectMembers(caller, companyId, id);
    }

    @PostMapping("/{id}/members")
    public TenancyDtos.ProjectMemberView addMember(@AuthenticationPrincipal JwtPrincipal caller,
                                                    @PathVariable long companyId,
                                                    @PathVariable long id,
                                                    @RequestBody @Valid TenancyDtos.AddProjectMember req) {
        return members.addProjectMember(caller, companyId, id, req);
    }

    @PutMapping("/{id}/members/{userId}")
    public TenancyDtos.ProjectMemberView changeMemberRole(@AuthenticationPrincipal JwtPrincipal caller,
                                                           @PathVariable long companyId, @PathVariable long id,
                                                           @PathVariable long userId,
                                                           @RequestBody @Valid TenancyDtos.UpdateProjectMemberRole req) {
        return members.updateProjectMemberRole(caller, companyId, id, userId, req);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal JwtPrincipal caller,
                             @PathVariable long companyId, @PathVariable long id,
                             @PathVariable long userId) {
        members.removeProjectMember(caller, companyId, id, userId);
    }
}
