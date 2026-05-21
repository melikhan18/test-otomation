package com.devicefarm.common.tenancy;

/**
 * HTTP header names used to carry the active tenancy context on every request.
 * The frontend adds these automatically; backend services read them via
 * {@code @RequestHeader} to scope queries.
 */
public final class TenancyHeaders {
    private TenancyHeaders() {}

    /** Numeric company id of the user's currently-selected workspace. */
    public static final String COMPANY_ID = "X-Company-Id";

    /** Numeric project id within the active company. */
    public static final String PROJECT_ID = "X-Project-Id";
}
