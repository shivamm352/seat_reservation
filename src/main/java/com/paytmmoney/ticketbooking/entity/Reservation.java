package com.paytmmoney.ticketbooking.entity;

import com.paytmmoney.ticketbooking.enums.ReservationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A confirmed seat booking for a user against a {@link Show}.
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>No PENDING state — the row only persists after full commit.</li>
 *   <li>Idempotency is enforced via unique constraint on (show_id, user_id, idempotency_key).</li>
 *   <li>{@code requestHash} is a SHA-256 of the request payload, used to detect
 *       key re-use with a different payload (replay attack prevention).</li>
 *   <li>Soft-cancel: {@code cancelled_at} is set instead of deleting the row.</li>
 * </ul>
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** Client-supplied token that uniquely identifies a booking attempt. */
    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    /** SHA-256 hex of request payload — detects key reuse with different payload. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** Total booking amount in paise (₹1 = 100 paise). */
    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Null unless status is CANCELLED. Provides audit trail without hard deletes. */
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    /** Cascade ALL — inserting a Reservation also persists its ReservationSeat children. */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationSeat> seats = new ArrayList<>();

    /** Required by JPA — do not use directly in application code. */
    protected Reservation() {}

    public Reservation(Show show, String userId, String idempotencyKey,
                       String requestHash, Long amountPaise) {
        this.show = show;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.amountPaise = amountPaise;
        this.status = ReservationStatus.CONFIRMED;
    }

    /** Convenience method: adds a seat to this reservation's collection. */
    public void addSeat(ReservationSeat seat) {
        seats.add(seat);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId()                      { return id; }
    public Show getShow()                    { return show; }
    public String getUserId()                { return userId; }
    public String getIdempotencyKey()        { return idempotencyKey; }
    public String getRequestHash()           { return requestHash; }
    public Long getAmountPaise()             { return amountPaise; }
    public ReservationStatus getStatus()     { return status; }
    public OffsetDateTime getCreatedAt()     { return createdAt; }
    public OffsetDateTime getCancelledAt()   { return cancelledAt; }
    public List<ReservationSeat> getSeats()  { return seats; }

    // ── Setters (mutable fields only) ────────────────────────────────────────

    public void setStatus(ReservationStatus status)       { this.status = status; }
    public void setCancelledAt(OffsetDateTime cancelledAt){ this.cancelledAt = cancelledAt; }
}
