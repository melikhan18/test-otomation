package com.qaplatform.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Cross-service exception → ProblemDetail mapper. Lives in :common so every
 * Servlet-stack service exposes the same error contract on the wire.
 *
 * <p>Gated on the Servlet API being present, which excludes the reactive
 * api-gateway (WebFlux only) — without this gate, gateway startup blows up
 * trying to introspect this class because its parameters used to reference
 * {@code HttpServletRequest} which isn't on a reactive classpath.</p>
 *
 * <p>Logging policy:</p>
 * <ul>
 *   <li>4xx domain errors (ApiException, validation, auth) → {@code WARN}
 *       with a short message. Stack traces aren't useful here — the cause
 *       is the caller, not us.</li>
 *   <li>5xx infra/unknown errors → {@code ERROR} with full stack trace.
 *       These are bugs in our code or downstream outages and must surface
 *       in the log aggregator — the previous implementation swallowed
 *       them silently, which made production incidents invisible.</li>
 * </ul>
 *
 * <p>Response bodies stay safe: the client gets the high-level message
 * (or "Internal server error" for the catch-all) but never a stack trace.</p>
 *
 * <p>Request path / method are NOT captured here — they'd require an
 * {@code HttpServletRequest} parameter which classloading-breaks the
 * reactive stack. Once we wire a request-logging filter that pushes
 * path into MDC, the structured log line will carry it automatically.</p>
 */
@RestControllerAdvice
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException e) {
        if (e.getStatus().is5xxServerError()) {
            log.error("ApiException {}: {}", e.getStatus().value(), e.getMessage(), e);
        } else {
            log.warn("ApiException {}: {}", e.getStatus().value(), e.getMessage());
        }
        return ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation 400: {}", msg);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException e) {
        log.warn("Auth 401: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccess(AccessDeniedException e) {
        log.warn("Access denied 403: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * Catch-all for anything we didn't explicitly map. These are the lines
     * that used to vanish silently. Always log the full stack so the log
     * aggregator captures it; only return a generic message to the client
     * so we don't leak internals.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleOther(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error");
    }
}
