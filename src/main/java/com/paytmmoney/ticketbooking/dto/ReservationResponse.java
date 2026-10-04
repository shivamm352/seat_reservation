package com.paytmmoney.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytmmoney.ticketbooking.entity.Reservation;
import com.paytmmoney.ticketbooking.entity.ReservationSeat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response returned after a successful seat reservation.
 * Emits snake_case fields matching prompt requirement:
 * {"reservation_id": "...", "show_id": "...", "user_id": "...", "seats": [...], "amount_paise": 25000, "status": "confirmed"}
 * Also exposes camelCase fields for backward compatibility.
 */
public record ReservationResponse(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("show_id") UUID showId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("seats") List<String> seats,
        @JsonProperty("amount_paise") long amountPaise,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
    @JsonProperty("id")
    public UUID id() {
        return reservationId;
    }

    @JsonProperty("showId")
    public UUID showIdCamel() {
        return showId;
    }

    @JsonProperty("userId")
    public String userIdCamel() {
        return userId;
    }

    @JsonProperty("amountPaise")
    public long amountPaiseCamel() {
        return amountPaise;
    }

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getShow().getId(),
                r.getUserId(),
                r.getSeats().stream().map(ReservationSeat::getSeatNumber).toList(),
                r.getAmountPaise(),
                r.getStatus().name().toLowerCase(),
                r.getCreatedAt()
        );
    }
}
