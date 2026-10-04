package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.ReservationSeat;
import com.paytmmoney.ticketbooking.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link ReservationSeat} junction entities.
 * Standard CRUD operations only — seat-level queries are handled via
 * {@link ReservationRepository} joins.
 */
@Repository
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {
}
