package com.paytmmoney.ticketbooking.entity;

import jakarta.persistence.*;

/**
 * Junction table record linking a {@link Reservation} to a specific {@link ShowSeat}.
 *
 * <h3>Composite PK via @EmbeddedId + @MapsId</h3>
 * <p>{@code @MapsId("reservationId")} wires the {@code reservation} FK column to
 * {@link ReservationSeatId#getReservationId()}. Same pattern for {@code seatId}.
 * JPA populates the embedded id fields automatically when the entity is persisted.
 *
 * <h3>Why no UNIQUE(seat_id)?</h3>
 * <p>A cancelled reservation cascade-deletes its {@code reservation_seats} rows,
 * freeing the seat for re-booking. A global {@code UNIQUE(seat_id)} would permanently
 * block re-booking. Active-booking uniqueness is enforced by the
 * {@link com.paytmmoney.ticketbooking.enums.SeatStatus} state machine on {@link ShowSeat}.
 */
@Entity
@Table(name = "reservation_seats")
public class ReservationSeat {

    @EmbeddedId
    private ReservationSeatId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id")
    private ShowSeat seat;

    /**
     * Denormalized seat number — avoids a join back to show_seats when
     * rendering booking receipts or confirmation responses.
     */
    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    /** Required by JPA — do not use directly in application code. */
    protected ReservationSeat() {}

    public ReservationSeat(Reservation reservation, ShowSeat seat) {
        this.id = new ReservationSeatId(reservation.getId(), seat.getId());
        this.reservation = reservation;
        this.seat = seat;
        this.seatNumber = seat.getSeatNumber();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public ReservationSeatId getId()    { return id; }
    public Reservation getReservation() { return reservation; }
    public ShowSeat getSeat()           { return seat; }
    public String getSeatNumber()       { return seatNumber; }
}
