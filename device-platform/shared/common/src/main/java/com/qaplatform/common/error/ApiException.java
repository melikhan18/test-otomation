package com.qaplatform.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException notFound(String what)    { return new ApiException(HttpStatus.NOT_FOUND, what + " not found"); }
    public static ApiException badRequest(String why)   { return new ApiException(HttpStatus.BAD_REQUEST, why); }
    public static ApiException forbidden(String why)    { return new ApiException(HttpStatus.FORBIDDEN, why); }
    public static ApiException conflict(String why)     { return new ApiException(HttpStatus.CONFLICT, why); }
    public static ApiException unauthorized(String why) { return new ApiException(HttpStatus.UNAUTHORIZED, why); }
    public static ApiException internal(String why)     { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, why); }
}
