package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.AppDtos;
import com.devicefarm.automation.service.AppService;
import com.devicefarm.automation.tenancy.ProjectContext;
import com.devicefarm.automation.tenancy.TenancyGuard;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.tenancy.TenancyHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/automation/apps/{appId}/versions")
public class AppVersionController {

    private final AppService service;
    private final TenancyGuard guard;

    public AppVersionController(AppService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<AppDtos.VersionView> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long appId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.listVersions(ctx, appId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.VersionView upload(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long appId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "notes", required = false) String notes) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.uploadVersion(caller, ctx, appId, file, notes);
    }

    @DeleteMapping("/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long appId,
            @PathVariable long versionId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.deleteVersion(ctx, appId, versionId);
    }
}
