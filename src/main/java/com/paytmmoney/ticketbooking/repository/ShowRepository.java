package com.paytmmoney.ticketbooking.repository;

import com.paytmmoney.ticketbooking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link Show} entities.
 * Inherits standard CRUD + pagination from {@link JpaRepository}.
 */
@Repository
public interface ShowRepository extends JpaRepository<Show, UUID> {
}
