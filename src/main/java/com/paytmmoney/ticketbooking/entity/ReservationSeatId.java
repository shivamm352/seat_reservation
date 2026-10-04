package com.paytmmoney.ticketbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link ReservationSeat}.
 *
 * <p>Used with {@code @EmbeddedId} + {@code @MapsId} — field names
 * ({@code reservationId}, {@code seatId}) must exactly match the values
 * passed to {@code @MapsId} on the {@link ReservationSeat} entity.
 */
@Embeddable
public class ReservationSeatId implements Serializable {

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "seat_id")
    private UUID seatId;

    /** Required by JPA spec. */
    public ReservationSeatId() {}

    public ReservationSeatId(UUID reservationId, UUID seatId) {
        this.reservationId = reservationId;
        this.seatId = seatId;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getSeatId()        { return seatId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationSeatId that)) return false;
        return Objects.equals(reservationId, that.reservationId)
                && Objects.equals(seatId, that.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, seatId);
    }
}
