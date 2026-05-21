package com.devicefarm.auth.api;

import com.devicefarm.auth.api.dto.NotificationDtos;
import com.devicefarm.auth.api.dto.TenancyDtos;
import com.devicefarm.auth.service.MemberService;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/members")
public class MemberController {

    private final MemberService service;
    public MemberController(MemberService service) { this.service = service; }

    @GetMapping
    public List<TenancyDtos.MemberView> list(@AuthenticationPrincipal JwtPrincipal caller,
                                             @PathVariable long companyId) {
        return service.listMembers(caller, companyId);
    }

    /** Per-project grants the target user has (id, slug, name, role). */
    @GetMapping("/{userId}/grants")
    public List<TenancyDtos.ProjectGrantView> userGrants(@AuthenticationPrincipal JwtPrincipal caller,
                                                          @PathVariable long companyId,
                                                          @PathVariable long userId) {
        return service.listUserGrants(caller, companyId, userId);
    }

    /** Add an existing user by username — owner=true OR a list of project grants. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenancyDtos.MemberView add(@AuthenticationPrincipal JwtPrincipal caller,
                                      @PathVariable long companyId,
                                      @RequestBody @Valid TenancyDtos.AddMember req) {
        return service.addMember(caller, companyId, req);
    }

    /** Invite by email — drops a notification on their bell for accept/decline. */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public TenancyDtos.MemberView invite(@AuthenticationPrincipal JwtPrincipal caller,
                                         @PathVariable long companyId,
                                         @RequestBody @Valid NotificationDtos.InviteByEmailRequest req) {
        return service.invite(caller, companyId, req);
    }

    /** Replace a member's OWNER flag + per-project grants. */
    @PutMapping("/{userId}")
    public TenancyDtos.MemberView update(@AuthenticationPrincipal JwtPrincipal caller,
                                          @PathVariable long companyId,
                                          @PathVariable long userId,
                                          @RequestBody @Valid TenancyDtos.UpdateMember req) {
        return service.updateMember(caller, companyId, userId, req);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal JwtPrincipal caller,
                       @PathVariable long companyId, @PathVariable long userId) {
        service.removeMember(caller, companyId, userId);
    }
}
