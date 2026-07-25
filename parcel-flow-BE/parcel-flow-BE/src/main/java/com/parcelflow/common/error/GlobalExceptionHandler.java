package com.parcelflow.common.error;

import com.parcelflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getErrorCode(), ex.getMessage(), ex.getDetails(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest req) {
        return build(ErrorCode.AUTH_FORBIDDEN, "Access denied", List.of(), req);
    }

    /**
     * Body that Jackson cannot read: malformed JSON, or a string that is not a
     * member of a target enum (e.g. {"status":"NOT_A_STATUS"}). Client error,
     * not a server fault.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, "Malformed request body",
                List.of(rootMessage(ex)), req);
    }

    /** Path variable or query parameter of the wrong type, e.g. /parcels/abc where a Long is expected. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                HttpServletRequest req) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
        return build(ErrorCode.VALIDATION_ERROR, "Invalid request parameter",
                List.of(ex.getName() + ": expected " + expected + ", got '" + ex.getValue() + "'"), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, "Missing request parameter",
                List.of(ex.getParameterName() + " is required"), req);
    }

    /** @Validated on path/query params (as opposed to a @RequestBody). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                       HttpServletRequest req) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details, req);
    }

    /**
     * {@code ?sort=} naming a property the entity does not have. Spring Data
     * resolves the sort against the entity and throws before the query runs.
     *
     * <p>Unhandled, this surfaced as a 500 on every paged endpoint — a caller
     * typo reported as a server fault — and the exception message names the
     * mapped type ("No property 'foo' found for type 'Order'"), so the default
     * handler also leaked the internal class name. The property the caller
     * actually sent is echoed back; the entity it failed against is not.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadSortProperty(PropertyReferenceException ex,
                                                                   HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, "Invalid sort property",
                List.of("Unknown sort property: '" + ex.getPropertyName() + "'"), req);
    }

    /**
     * A database constraint refused the write. This is almost always the client
     * pointing at something that does not exist (unknown hub id) or re-using a
     * unique value — both client errors. The DB message is not echoed back: it
     * names tables and constraints the caller has no business seeing.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                 HttpServletRequest req) {
        String raw = rootMessage(ex);
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), raw);

        if (raw.contains("Duplicate entry") || raw.contains("uk_") || raw.contains("unique")) {
            return build(ErrorCode.RESOURCE_CONFLICT,
                    "A record with the same unique value already exists", List.of(), req);
        }
        if (raw.contains("foreign key constraint fails") || raw.contains("fk_")) {
            return build(ErrorCode.VALIDATION_ERROR,
                    "Request references a record that does not exist", List.of(), req);
        }
        return build(ErrorCode.VALIDATION_ERROR, "Request violates a data constraint", List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR,
                "HTTP method " + ex.getMethod() + " is not supported by this endpoint", List.of(), req);
    }

    /** Unknown URL. Without this Spring Boot renders its own error page instead of the API envelope. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex,
                                                              HttpServletRequest req) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint for " + req.getRequestURI(),
                List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "Internal server error", List.of(), req);
    }

    /** Deepest cause message — the outer Spring wrappers say little of use. */
    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "" : cause.getMessage();
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message,
                                                    List<String> details, HttpServletRequest req) {
        ApiResponse<Void> body = ApiResponse.failure(code.name(), message, details, req.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
