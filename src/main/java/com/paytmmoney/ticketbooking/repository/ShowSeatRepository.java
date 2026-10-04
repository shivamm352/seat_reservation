package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.ShowSeat;
import com.paytmmoney.ticketbooking.enums.SeatStatus;
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
 */
@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    /**
     * Fetches the requested seats and immediately acquires exclusive row locks
     * ({@code SELECT ... FOR UPDATE}).
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
     * Locks specific seats by their primary keys in ascending order of seatNumber.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.id IN :seatIds ORDER BY s.seatNumber ASC")
    List<ShowSeat> findSeatsByIdsForUpdate(@Param("seatIds") List<UUID> seatIds);

    /**
     * Retrieves all seats for a show, ordered by seat number.
     */
    List<ShowSeat> findByShowIdOrderBySeatNumberAsc(UUID showId);

    /**
     * Counts seats by status across all shows (used for Prometheus gauge).
     */
    long countByStatus(SeatStatus status);
}
