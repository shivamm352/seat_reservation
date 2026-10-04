package com.paytmmoney.ticketbooking.enums;

/**
 * Lifecycle states for a reservation.
 *
 * <p>No PENDING state exists by design: a {@code Reservation} row is only persisted
 * once the booking transaction fully commits (seats confirmed, record written).
 * In-flight operations live only inside the DB transaction.
 */
public enum ReservationStatus {

    /** Booking is active; seats are held for this user. */
    CONFIRMED,

    /** Booking was cancelled; seats have been released back to AVAILABLE. */
    CANCELLED
}
