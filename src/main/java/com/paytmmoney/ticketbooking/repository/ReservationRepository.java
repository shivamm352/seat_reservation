package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Reservation} entities.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Counts the total number of seats currently CONFIRMED for a user at a show.
     */
    @Query("SELECT COUNT(rs) FROM ReservationSeat rs " +
           "JOIN rs.reservation r " +
           "WHERE r.show.id = :showId " +
           "AND r.userId = :userId " +
           "AND r.status = 'CONFIRMED'")
    int countConfirmedSeatsForUser(
            @Param("showId") UUID showId,
            @Param("userId") String userId);

    /**
     * Finds a reservation by ID and owner — used by the cancellation endpoint.
     */
    Optional<Reservation> findByIdAndUserId(UUID id, String userId);

    /**
     * Returns all reservations for a user at a specific show, newest first.
     */
    List<Reservation> findByShowIdAndUserIdOrderByCreatedAtDesc(UUID showId, String userId);

    /**
     * Finds a reservation by showId, userId, and idempotencyKey.
     * Used for dual-check idempotency in the reservation engine.
     */
    Optional<Reservation> findByShowIdAndUserIdAndIdempotencyKey(UUID showId, String userId, String idempotencyKey);

    /**
     * Acquires a pessimistic write lock on a reservation record for safe cancellation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);
}
