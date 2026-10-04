package com.paytmmoney.ticketbooking.exception;

/** Thrown when a reservation ID cannot be found. Maps to HTTP 404. */
public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
