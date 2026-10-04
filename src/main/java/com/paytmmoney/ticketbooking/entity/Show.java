package com.paytmmoney.ticketbooking.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a ticketed show/event.
 *
 * <p>Money is stored as {@code price_paise} (integer minor units) to
 * eliminate floating-point rounding errors. Display layer converts to
 * rupees: {@code pricePaise / 100.0}.
 */
@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Price in paise (₹1 = 100 paise). Never use floating-point for money. */
    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    /** Maximum seats one user may book for this show. Defaults to 4. */
    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Required by JPA — do not use directly in application code. */
    protected Show() {}

    public Show(String name, Long pricePaise, int perUserLimit, int totalSeats) {
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.totalSeats = totalSeats;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId()            { return id; }
    public String getName()        { return name; }
    public Long getPricePaise()    { return pricePaise; }
    public int getPerUserLimit()   { return perUserLimit; }
    public int getTotalSeats()     { return totalSeats; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // ── Setters (mutable fields only) ────────────────────────────────────────

    public void setName(String name)                { this.name = name; }
    public void setPricePaise(Long pricePaise)       { this.pricePaise = pricePaise; }
    public void setPerUserLimit(int perUserLimit)    { this.perUserLimit = perUserLimit; }
    public void setTotalSeats(int totalSeats)        { this.totalSeats = totalSeats; }
}
