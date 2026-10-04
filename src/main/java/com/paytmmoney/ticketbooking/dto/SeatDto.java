package com.paytmmoney.ticketbooking.dto;

import com.paytmmoney.ticketbooking.entity.ShowSeat;
import com.paytmmoney.ticketbooking.enums.SeatStatus;

/**
 * Lightweight read-only view of a single seat.
 *
 * @param seatNumber  Label of the seat (e.g., "A1", "B12").
 * @param status      Current availability status of the seat.
 */
public record SeatDto(
        String seatNumber,
        SeatStatus status
) {
    /** Static factory — maps a {@link ShowSeat} entity to this DTO. */
    public static SeatDto from(ShowSeat seat) {
        return new SeatDto(seat.getSeatNumber(), seat.getStatus());
    }
}
