package com.paytmmoney.ticketbooking.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Pessimistic advisory lock row — one per (show, user) pair.
 *
 * <h3>Concurrency Protocol</h3>
 * <ol>
 *   <li>{@code upsertUserLockRow(showId, userId)} — idempotently ensures the row exists.</li>
 *   <li>{@code lockUserRow(showId, userId)} — issues {@code SELECT ... FOR UPDATE},
 *       blocking concurrent booking attempts from the same user for the same show.</li>
 *   <li>The service proceeds with seat selection and reservation creation.</li>
 *   <li>The lock is automatically released when the wrapping {@code @Transactional}
 *       method commits or rolls back.</li>
 * </ol>
 *
 * <p>No {@code created_at} / {@code expires_at} — this is a DB-transaction-scoped
 * lock, not a timeout-based hold. Rows persist between transactions so the lock
 * row is always available to lock on.
 */
@Entity
@Table(name = "show_user_locks")
@IdClass(ShowUserLockId.class)
public class ShowUserLock {

    @Id
    @Column(name = "show_id")
    private UUID showId;

    @Id
    @Column(name = "user_id", length = 100)
    private String userId;

    /** Required by JPA — do not use directly in application code. */
    protected ShowUserLock() {}

    public ShowUserLock(UUID showId, String userId) {
        this.showId = showId;
        this.userId = userId;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getShowId()   { return showId; }
    public String getUserId() { return userId; }
}
