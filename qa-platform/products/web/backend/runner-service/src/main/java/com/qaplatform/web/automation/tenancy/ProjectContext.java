package com.qaplatform.web.automation.tenancy;

/**
 * Resolved tenancy for the current request. Identical shape to Android's
 * (see {@code com.qaplatform.android.automation.tenancy.ProjectContext}) —
 * the two services don't share a Java type because they're independently
 * deployable and the tenancy guard semantics may diverge later (web stack
 * has no device whitelist concept, for instance).
 */
public record ProjectContext(
        Long projectId,
        Long companyId,
        String role
) {
    public boolean canManage()  { return "OWNER".equals(role) || "QA_MANAGER".equals(role) || "PLATFORM_ADMIN".equals(role); }
    public boolean isPlatform() { return "PLATFORM_ADMIN".equals(role); }
    public boolean isOwner()    { return "OWNER".equals(role) || "PLATFORM_ADMIN".equals(role); }
}
