package com.chainreaction.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String errorCode,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldErrorDetail> fieldErrors) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String correlationId) {
        return new ErrorResponse(errorCode.name(), message, correlationId, Instant.now(), List.of());
    }

    public static ErrorResponse validation(String message, String correlationId, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(ErrorCode.VALIDATION_FAILED.name(), message, correlationId, Instant.now(), fieldErrors);
    }
}
