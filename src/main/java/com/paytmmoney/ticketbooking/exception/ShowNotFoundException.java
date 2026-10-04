package com.paytmmoney.ticketbooking.exception;

/** Thrown when a requested show does not exist in the database. Maps to HTTP 404. */
public class ShowNotFoundException extends RuntimeException {
    public ShowNotFoundException(String message) {
        super(message);
    }
}
