package com.paytmmoney.ticketbooking.exception;

/**
 * Thrown when one or more requested seats are not in AVAILABLE status (i.e., already HELD
 * or CONFIRMED by another reservation). Maps to HTTP 409 Conflict.
 */
public class SeatAlreadyTakenException extends RuntimeException {
    public SeatAlreadyTakenException(String message) {
        super(message);
    }
}
