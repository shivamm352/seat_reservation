package com.paytmmoney.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytmmoney.ticketbooking.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response returned after successfully cancelling a reservation.
 */
public record CancelResponse(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("status") String status,
        @JsonProperty("cancelled_at") OffsetDateTime cancelledAt
) {
    @JsonProperty("id")
    public UUID id() {
        return reservationId;
    }

    public CancelResponse(UUID reservationId, ReservationStatus status, OffsetDateTime cancelledAt) {
        this(reservationId, status.name().toLowerCase(), cancelledAt);
    }
}
