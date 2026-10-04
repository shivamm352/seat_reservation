package com.paytmmoney.ticketbooking.dto;

import java.time.OffsetDateTime;

/**
 * Standardized error response body returned by the global exception handler.
 *
 * <p>Matches RFC 7807 (Problem Details) in spirit — all API errors return
 * a consistent JSON structure.
 *
 * @param status    HTTP status code (e.g., 400, 404, 409, 500).
 * @param message   Human-readable error description.
 * @param timestamp UTC timestamp when the error occurred.
 */
public record ErrorResponse(
        int status,
        String message,
        OffsetDateTime timestamp
) {
    /**
     * Convenience factory — captures current timestamp automatically.
     *
     * @param status  HTTP status code.
     * @param message Error description.
     * @return A new {@link ErrorResponse} with {@code timestamp = now()}.
     */
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, OffsetDateTime.now());
    }
}
