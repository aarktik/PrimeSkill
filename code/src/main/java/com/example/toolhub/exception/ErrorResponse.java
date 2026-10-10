package com.example.toolhub.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldErrorResponse> fieldErrors,
        String traceId) {
}
