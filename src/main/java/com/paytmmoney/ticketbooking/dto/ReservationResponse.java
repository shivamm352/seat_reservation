package com.paytmmoney.ticketbooking.dto;

import com.paytmmoney.ticketbooking.entity.Reservation;
import com.paytmmoney.ticketbooking.entity.ReservationSeat;
import com.paytmmoney.ticketbooking.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response returned after a successful seat reservation.
 *
 * @param id           Reservation ID (use this for cancellation).
 * @param showId       Show that was booked.
 * @param userId       User who made the booking.
 * @param seats        List of booked seat labels.
 * @param amountPaise  Total charged amount in paise.
 * @param status       Reservation status (always CONFIRMED on creation).
 * @param createdAt    UTC timestamp of booking.
 */
public record ReservationResponse(
        UUID id,
        UUID showId,
        String userId,
        List<String> seats,
        long amountPaise,
        ReservationStatus status,
        OffsetDateTime createdAt
) {
    /** Static factory — maps a {@link Reservation} entity to this response. */
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getShow().getId(),
                r.getUserId(),
                r.getSeats().stream().map(ReservationSeat::getSeatNumber).toList(),
                r.getAmountPaise(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}
