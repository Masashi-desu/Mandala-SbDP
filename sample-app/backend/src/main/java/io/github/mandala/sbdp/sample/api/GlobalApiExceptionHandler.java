package io.github.mandala.sbdp.sample.api;

import io.github.mandala.sbdp.sample.domain.ForbiddenOperationException;
import io.github.mandala.sbdp.sample.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException failure, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "not_found", failure.getMessage(), request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> routeNotFound(NoResourceFoundException failure, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "not_found", "Resource not found", request, List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiErrorResponse> forbidden(ForbiddenOperationException failure, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "access_denied", failure.getMessage(), request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> authentication(AuthenticationException failure, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid username or password", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException failure, HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> violations = failure.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiErrorResponse.FieldViolation(field.getField(), field.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraint(ConstraintViolationException failure, HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> violations = failure.getConstraintViolations().stream()
                .map(item -> new ApiErrorResponse.FieldViolation(
                        item.getPropertyPath().toString(), item.getMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException failure, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", "Malformed or unsupported request value", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException failure, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", "Path or query parameter has an invalid value", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> dataConflict(DataIntegrityViolationException failure, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "data_conflict", "The request conflicts with persisted data", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception failure, HttpServletRequest request) {
        log.error("Unhandled API error", failure);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred", request, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> violations) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                violations));
    }
}
