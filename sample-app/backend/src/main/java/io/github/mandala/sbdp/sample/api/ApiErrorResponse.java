package io.github.mandala.sbdp.sample.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            String path) {
        return new ApiErrorResponse(Instant.now(), status, error, code, message, path, List.of());
    }
}
