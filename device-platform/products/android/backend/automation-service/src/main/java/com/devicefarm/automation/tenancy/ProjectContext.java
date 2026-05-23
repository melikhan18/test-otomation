package com.devicefarm.automation.tenancy;

/**
 * Resolved tenancy context for the current request. Carries everything a service
 * method needs to read or write a project-scoped resource without re-doing
 * permission checks or DB lookups.
 *
 * <p>Fields are boxed (Long) so service code can call {@code .equals()} against
 * entity getters — which also return {@code Long} — without manual boxing.</p>
 *
 * @param projectId        the active project (X-Project-Id header)
 * @param companyId        the project's parent company
 * @param legacyProductId  the company's old product_id, used to keep NOT-NULL
 *                         legacy columns satisfied until they're dropped
 * @param role             the caller's role in the company (OWNER/QA_MANAGER/TESTER)
 *                         or "PLATFORM_ADMIN" if they're a platform-level super-user
 */
public record ProjectContext(
        Long projectId,
        Long companyId,
        Long legacyProductId,
        String role
) {
    public boolean canManage()   { return "OWNER".equals(role) || "QA_MANAGER".equals(role) || "PLATFORM_ADMIN".equals(role); }
    public boolean isPlatform()  { return "PLATFORM_ADMIN".equals(role); }
    public boolean isOwner()     { return "OWNER".equals(role) || "PLATFORM_ADMIN".equals(role); }
}
