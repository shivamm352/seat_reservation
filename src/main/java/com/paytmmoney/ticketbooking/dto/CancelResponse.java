package com.paytmmoney.ticketbooking.dto;

import com.paytmmoney.ticketbooking.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response returned after successfully cancelling a reservation.
 *
 * @param reservationId  ID of the cancelled reservation.
 * @param status         Always {@link ReservationStatus#CANCELLED}.
 * @param cancelledAt    UTC timestamp when the cancellation was processed.
 */
public record CancelResponse(
        UUID reservationId,
        ReservationStatus status,
        OffsetDateTime cancelledAt
) {}
