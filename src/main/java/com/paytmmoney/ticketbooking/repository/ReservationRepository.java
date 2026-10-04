package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
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
     *
     * <p>This is the enforcement query for {@code show.perUserLimit}. The service
     * calls this inside the locked transaction to check if adding the requested
     * seats would exceed the per-user cap.
     *
     * <p>Queries across {@code ReservationSeat} → {@code Reservation} to count
     * individual seat records (not reservation records), giving the true seat count
     * even when a user holds multiple reservations.
     *
     * @param showId The show to check.
     * @param userId The user whose seat count is being checked.
     * @return Total number of confirmed seats held by the user for this show.
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
     * Finds a reservation by ID and owner — used by the cancellation endpoint
     * to prevent users from cancelling other users' reservations.
     *
     * @param id     Reservation identifier.
     * @param userId Authenticated user's ID.
     * @return The reservation if it belongs to the user, otherwise empty.
     */
    Optional<Reservation> findByIdAndUserId(UUID id, String userId);

    /**
     * Returns all reservations for a user at a specific show, newest first.
     * Used by the booking history endpoint.
     */
    List<Reservation> findByShowIdAndUserIdOrderByCreatedAtDesc(UUID showId, String userId);
}
