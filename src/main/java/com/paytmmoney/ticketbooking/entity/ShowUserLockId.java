package com.paytmmoney.ticketbooking.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link ShowUserLock}.
 *
 * <p>Used as {@code @IdClass} — field names must exactly match the
 * {@code @Id}-annotated fields in the owning entity.
 */
public class ShowUserLockId implements Serializable {

    private UUID showId;
    private String userId;

    /** Required by JPA spec and @IdClass contract. */
    public ShowUserLockId() {}

    public ShowUserLockId(UUID showId, String userId) {
        this.showId = showId;
        this.userId = userId;
    }

    public UUID getShowId() { return showId; }
    public String getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShowUserLockId that)) return false;
        return Objects.equals(showId, that.showId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showId, userId);
    }
}
