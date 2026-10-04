package com.paytmmoney.ticketbooking.dto;

import com.paytmmoney.ticketbooking.dto.ReservationResponse;

/**
 * Wrapper returned from the reservation service to indicate whether
 * the booking is brand-new (HTTP 201) or an idempotent replay (HTTP 200).
 *
 * @param reservation  The full reservation payload to return to the caller.
 * @param isNewBooking {@code true} if this is a freshly created reservation (→ 201 Created);
 *                     {@code false} if this is a replayed idempotent request (→ 200 OK).
 */
public record BookingResult(
        ReservationResponse reservation,
        boolean isNewBooking
) {}
