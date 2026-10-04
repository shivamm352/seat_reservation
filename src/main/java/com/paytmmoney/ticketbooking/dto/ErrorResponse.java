package com.paytmmoney.ticketbooking.dto;

import java.time.OffsetDateTime;

/**
 * Standardized error response body returned by the global exception handler.
 */
public record ErrorResponse(
        int status,
        String errorCode,
        String message,
        String traceId,
        OffsetDateTime timestamp
) {

    public ErrorResponse(String errorCode, String message, String traceId) {
        this(409, errorCode, message, traceId, OffsetDateTime.now());
    }

    public static ErrorResponse of(int status, String errorCode, String message, String traceId) {
        return new ErrorResponse(status, errorCode, message, traceId, OffsetDateTime.now());
    }
}
