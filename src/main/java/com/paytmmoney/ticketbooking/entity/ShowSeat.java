package com.paytmmoney.ticketbooking.entity;

import com.paytmmoney.ticketbooking.enums.SeatStatus;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * Represents an individual seat within a {@link Show}.
 *
 * <p><b>Status machine:</b> AVAILABLE → HELD → CONFIRMED (on success)
 * or HELD → AVAILABLE (on failure/rollback). CONFIRMED → AVAILABLE on cancellation.
 *
 * <p><b>Design note on {@code reservationId}:</b> This field is a plain {@code UUID}
 * column with <em>no</em> {@code @ManyToOne} mapping. A FK to {@code reservations}
 * would create a circular dependency (insert reservation needs confirmed seats; confirm
 * seats needs reservation ID). Consistency is maintained by the service layer within
 * a single DB transaction.
 */
@Entity
@Table(name = "show_seats")
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Parent show — loaded lazily to avoid N+1 on seat list queries. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /**
     * The reservation that holds this seat — plain UUID, no FK mapping.
     * Null when status is AVAILABLE.
     */
    @Column(name = "reservation_id")
    private UUID reservationId;

    /** Required by JPA — do not use directly in application code. */
    protected ShowSeat() {}

    public ShowSeat(Show show, String seatNumber) {
        this.show = show;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId()              { return id; }
    public Show getShow()            { return show; }
    public String getSeatNumber()    { return seatNumber; }
    public SeatStatus getStatus()    { return status; }
    public UUID getReservationId()   { return reservationId; }

    // ── Setters (mutable fields only) ────────────────────────────────────────

    public void setStatus(SeatStatus status)           { this.status = status; }
    public void setReservationId(UUID reservationId)   { this.reservationId = reservationId; }
}
