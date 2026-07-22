package com.parcelflow.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    AUTH_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN),
    AUTH_IP_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    AUTH_TEMP_PASSWORD_EXPIRED(HttpStatus.FORBIDDEN),
    AUTH_PASSWORD_POLICY(HttpStatus.BAD_REQUEST),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED),
    AUTH_ACCOUNT_PERMANENTLY_LOCKED(HttpStatus.LOCKED),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
