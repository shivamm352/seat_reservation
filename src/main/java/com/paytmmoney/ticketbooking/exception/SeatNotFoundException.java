package com.paytmmoney.ticketbooking.exception;

/** Thrown when one or more requested seat labels do not exist for the given show. Maps to HTTP 404. */
public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(String message) {
        super(message);
    }
}
