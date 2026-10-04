package com.paytmmoney.ticketbooking.enums;

/**
 * Lifecycle states for a seat within a show.
 *
 * <p>State machine:
 * <pre>
 *   AVAILABLE ──► HELD ──► CONFIRMED
 *                  │
 *                  └──► AVAILABLE  (on booking failure / rollback)
 *   CONFIRMED ──► AVAILABLE        (on reservation cancellation)
 * </pre>
 */
public enum SeatStatus {

    /** Seat is open for booking. */
    AVAILABLE,

    /** Seat is temporarily held during an active booking transaction. */
    HELD,

    /** Seat has been successfully booked and payment confirmed. */
    CONFIRMED
}
