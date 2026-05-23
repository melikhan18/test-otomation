package com.qaplatform.shared.auth.api;

import com.qaplatform.shared.auth.api.dto.TenancyDtos;
import com.qaplatform.shared.auth.service.CompanyService;
import com.qaplatform.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;
    public CompanyController(CompanyService service) { this.service = service; }

    @GetMapping
    public List<TenancyDtos.CompanyView> mine(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.listMine(caller);
    }

    @GetMapping("/{id}")
    public TenancyDtos.CompanyView get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenancyDtos.CompanyView create(@AuthenticationPrincipal JwtPrincipal caller,
                                          @RequestBody @Valid TenancyDtos.CompanyCreate req) {
        return service.create(caller, req);
    }

    @PutMapping("/{id}")
    public TenancyDtos.CompanyView update(@AuthenticationPrincipal JwtPrincipal caller,
                                          @PathVariable long id,
                                          @RequestBody @Valid TenancyDtos.CompanyUpdate req) {
        return service.update(caller, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        service.archive(caller, id);
    }

    /* ───── Platform admin ───── */

    /** Every company on the platform — including archived ones. */
    @GetMapping("/admin/all")
    public List<TenancyDtos.CompanyView> adminListAll(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.listAllForAdmin(caller);
    }

    /** Reverse archive. Platform-admin only. */
    @PostMapping("/{id}/unarchive")
    public TenancyDtos.CompanyView unarchive(@AuthenticationPrincipal JwtPrincipal caller,
                                              @PathVariable long id) {
        return service.unarchive(caller, id);
    }
}
