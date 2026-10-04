package com.paytmmoney.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytmmoney.ticketbooking.entity.ShowSeat;

/**
 * Read-only view of a single seat.
 * Emits {"seat": "A1", "status": "available"} per assignment specification.
 */
public record SeatDto(
        @JsonProperty("seat")
        @JsonAlias({"seat", "seatNumber"})
        String seat,

        @JsonProperty("status")
        String status
) {
    @JsonProperty("seatNumber")
    public String seatNumber() {
        return seat;
    }

    public static SeatDto from(ShowSeat seat) {
        return new SeatDto(
                seat.getSeatNumber(),
                seat.getStatus().name().toLowerCase()
        );
    }
}
