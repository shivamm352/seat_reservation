package com.paytmmoney.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Request body for reserving seats at a show.
 *
 * Supports both prompt snake_case ("seats", "idempotency_key")
 * and camelCase ("seatNumbers", "idempotencyKey").
 *
 * showId is optional in body because it is extracted from the URL path.
 * idempotencyKey is optional in body because it can be passed via the Idempotency-Key HTTP header.
 */
public record ReserveRequest(

        @JsonProperty("show_id")
        @JsonAlias({"show_id", "showId"})
        UUID showId,

        @JsonProperty("seats")
        @JsonAlias({"seats", "seatNumbers"})
        @NotEmpty(message = "At least one seat must be selected")
        List<@NotBlank(message = "Seat numbers must not be blank") String> seatNumbers,

        @JsonProperty("idempotency_key")
        @JsonAlias({"idempotency_key", "idempotencyKey"})
        String idempotencyKey

) {
    public ReserveRequest(List<String> seatNumbers, String idempotencyKey) {
        this(null, seatNumbers, idempotencyKey);
    }
}
