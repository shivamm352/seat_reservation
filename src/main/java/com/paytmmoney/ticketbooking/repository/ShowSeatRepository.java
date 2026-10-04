package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ShowSeat} entities.
 *
 * <p>The key method {@link #findSeatsForUpdate} issues a
 * {@code SELECT ... FOR UPDATE} to acquire pessimistic write locks on
 * specific seat rows. This prevents concurrent transactions from booking
 * the same seats simultaneously.
 */
@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    /**
     * Fetches the requested seats and immediately acquires exclusive row locks
     * ({@code SELECT ... FOR UPDATE}).
     *
     * <p><b>Must be called inside a {@code @Transactional} method.</b>
     * The lock is held until the surrounding transaction commits or rolls back.
     * Results are ordered by seat number to ensure consistent lock acquisition
     * order across concurrent transactions and prevent deadlocks.
     *
     * @param showId      The show whose seats are being locked.
     * @param seatNumbers The specific seat labels to lock.
     * @return Locked seat entities ready for status update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s " +
           "WHERE s.show.id = :showId " +
           "AND s.seatNumber IN :seatNumbers " +
           "ORDER BY s.seatNumber ASC")
    List<ShowSeat> findSeatsForUpdate(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers);

    /**
     * Retrieves all seats for a show, ordered by seat number.
     * Used by the seat availability endpoint.
     */
    List<ShowSeat> findByShowIdOrderBySeatNumberAsc(UUID showId);
}
