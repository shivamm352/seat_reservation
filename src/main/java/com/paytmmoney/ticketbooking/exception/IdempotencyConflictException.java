package com.paytmmoney.ticketbooking.exception;

/**
 * Thrown when an idempotency key is reused with a payload whose hash differs from
 * the original request that used that key. This indicates the caller is attempting
 * to make a different booking under a previously-used idempotency token.
 * Maps to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
