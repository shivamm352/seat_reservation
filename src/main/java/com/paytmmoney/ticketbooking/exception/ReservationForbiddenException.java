package com.paytmmoney.ticketbooking.exception;

/** Thrown when a user attempts to operate on a reservation they do not own. Maps to HTTP 403. */
public class ReservationForbiddenException extends RuntimeException {
    public ReservationForbiddenException(String message) {
        super(message);
    }
}
