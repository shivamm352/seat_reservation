package com.paytmmoney.ticketbooking.service;

import com.paytmmoney.ticketbooking.dto.BookingResult;
import com.paytmmoney.ticketbooking.dto.CancelResponse;
import com.paytmmoney.ticketbooking.dto.ReserveRequest;

import java.util.UUID;

public interface ReservationService {

    /**
     * Executes atomic, race-free reservation with user locks, idempotency check,
     * per-user limit check, and pessimistic seat locking.
     */
    BookingResult reserveSeats(UUID showId, String userId, String idempotencyKey, ReserveRequest request);

    /**
     * Cancels an existing reservation, verifying ownership and releasing seats back to AVAILABLE.
     */
    CancelResponse cancelReservation(UUID reservationId, String authenticatedUserId);
}
