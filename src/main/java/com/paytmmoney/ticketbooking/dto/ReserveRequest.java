package com.paytmmoney.ticketbooking.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Request body for reserving seats at a show.
 *
 * <p><b>userId is intentionally absent</b> — it is extracted from the JWT
 * security context in the service layer, preventing users from booking on
 * behalf of others by manipulating the request body.
 *
 * <p><b>Max seatNumbers size is not hard-coded here</b> — the actual limit
 * is read from {@code show.perUserLimit} at runtime and enforced in the
 * service layer to support shows with different per-user caps.
 *
 * @param showId         Target show to book.
 * @param seatNumbers    Ordered list of seat labels to reserve.
 * @param idempotencyKey Client-generated unique token for this booking attempt.
 *                       Re-sending the same key returns the original response.
 */
public record ReserveRequest(

        @NotNull(message = "showId is required")
        UUID showId,

        @NotEmpty(message = "At least one seat must be selected")
        @Size(min = 1, max = 10, message = "Cannot request more than 10 seats in one call")
        List<@NotBlank(message = "Seat numbers must not be blank") String> seatNumbers,

        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 200, message = "idempotencyKey must not exceed 200 characters")
        String idempotencyKey

) {}
