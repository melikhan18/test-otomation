package com.qaplatform.android.bridge.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.stream.Collectors;

/**
 * WebFlux-specific exception mappings the common (servlet-based) handler can't
 * register without dragging spring-webflux into every module.
 *
 * <p>Right now this is just {@link WebExchangeBindException} → 400 with the field-error
 * summary, so {@code @Valid} bodies on app-control endpoints reject bad input cleanly
 * instead of bubbling up as 500.</p>
 */
@RestControllerAdvice
public class BridgeExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleBind(WebExchangeBindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (msg.isBlank()) msg = "request body validation failed";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    }
}
