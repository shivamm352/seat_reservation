package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.ShowUserLock;
import com.paytmmoney.ticketbooking.entity.ShowUserLockId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the pessimistic advisory lock table {@link ShowUserLock}.
 *
 * <h3>Two-Step Locking Protocol</h3>
 * <ol>
 *   <li>{@link #upsertUserLockRow} — idempotently ensures the lock row exists.
 *       Uses {@code ON CONFLICT DO NOTHING} so first-time users get a row without
 *       contention, while repeat callers are no-ops.</li>
 *   <li>{@link #lockUserRow} — acquires an exclusive row lock via
 *       {@code SELECT ... FOR UPDATE}. Concurrent booking attempts from the same
 *       user for the same show will block here until the first transaction ends.</li>
 * </ol>
 *
 * <p><b>Both methods must be called inside the same {@code @Transactional} context.</b>
 */
@Repository
public interface ShowUserLockRepository extends JpaRepository<ShowUserLock, ShowUserLockId> {

    /**
     * Idempotently inserts a lock row for the given (show, user) pair.
     *
     * <p>{@code ON CONFLICT DO NOTHING} ensures this is safe to call concurrently:
     * the first caller inserts, subsequent callers are no-ops. The row persists
     * between transactions so {@link #lockUserRow} always has something to lock.
     *
     * @param showId The show being booked.
     * @param userId The user initiating the booking.
     */
    @Modifying
    @Query(
        value = "INSERT INTO show_user_locks (show_id, user_id) " +
                "VALUES (:showId, :userId) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    void upsertUserLockRow(@Param("showId") UUID showId, @Param("userId") String userId);

    /**
     * Acquires an exclusive row lock ({@code SELECT ... FOR UPDATE}) on the
     * (show, user) lock row.
     *
     * <p>If the row doesn't exist (e.g., upsert wasn't called first), returns empty.
     * Always call {@link #upsertUserLockRow} before this method.
     *
     * @param showId The show being booked.
     * @param userId The user initiating the booking.
     * @return The locked row, or empty if not found.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ShowUserLock l WHERE l.showId = :showId AND l.userId = :userId")
    Optional<ShowUserLock> lockUserRow(
            @Param("showId") UUID showId,
            @Param("userId") String userId);
}
