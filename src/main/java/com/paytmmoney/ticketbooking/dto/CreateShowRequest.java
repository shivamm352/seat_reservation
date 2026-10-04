package com.paytmmoney.ticketbooking.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Request body for creating a new show.
 *
 * <p>The caller must supply exactly the seat numbers that will be created —
 * the service validates that {@code seatNumbers.size() == totalSeats}.
 *
 * @param name         Display name of the show (max 200 chars).
 * @param pricePaise   Ticket price in paise (₹1 = 100 paise). Must be ≥ 0.
 * @param perUserLimit Max seats one user can book. Defaults to 4 in DB but caller can override.
 * @param totalSeats   Total number of seats in the venue.
 * @param seatNumbers  Ordered list of seat labels (e.g., "A1", "B12").
 */
public record CreateShowRequest(

        @NotBlank(message = "Show name must not be blank")
        @Size(max = 200, message = "Show name must not exceed 200 characters")
        String name,

        @NotNull(message = "pricePaise is required")
        @Min(value = 0, message = "Price cannot be negative")
        Long pricePaise,

        @Min(value = 1, message = "perUserLimit must be at least 1")
        @Max(value = 20, message = "perUserLimit cannot exceed 20")
        int perUserLimit,

        @Min(value = 1, message = "totalSeats must be at least 1")
        int totalSeats,

        @NotEmpty(message = "At least one seat number is required")
        List<@NotBlank(message = "Seat numbers must not be blank") String> seatNumbers

) {}
