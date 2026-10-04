package com.paytmmoney.ticketbooking.exception;

/**
 * Thrown when adding the requested seats would push the user over the show's per-user
 * booking limit ({@code show.perUserLimit}). Maps to HTTP 409 Conflict.
 */
public class BookingLimitExceededException extends RuntimeException {
    public BookingLimitExceededException(String message) {
        super(message);
    }
}
