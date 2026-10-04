package com.paytmmoney.ticketbooking.dto;

import com.paytmmoney.ticketbooking.entity.Show;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only response for show details.
 *
 * @param id           Unique show identifier.
 * @param name         Display name.
 * @param pricePaise   Ticket price in paise (₹1 = 100 paise).
 * @param perUserLimit Max seats one user may book.
 * @param totalSeats   Total venue capacity.
 * @param createdAt    UTC creation timestamp.
 */
public record ShowResponse(
        UUID id,
        String name,
        long pricePaise,
        int perUserLimit,
        int totalSeats,
        OffsetDateTime createdAt
) {
    /** Static factory — maps a {@link Show} entity to this response record. */
    public static ShowResponse from(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),
                show.getCreatedAt()
        );
    }
}
