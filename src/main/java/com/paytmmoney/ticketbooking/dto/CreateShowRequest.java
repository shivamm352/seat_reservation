package com.paytmmoney.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Request body for creating a new show.
 * Supports both official prompt snake_case ("price_paise", "seats")
 * and camelCase ("pricePaise", "seatNumbers").
 */
public record CreateShowRequest(

        @NotBlank(message = "Show name must not be blank")
        @Size(max = 200, message = "Show name must not exceed 200 characters")
        String name,

        @JsonProperty("price_paise")
        @JsonAlias({"price_paise", "pricePaise"})
        @NotNull(message = "price_paise is required")
        @Min(value = 0, message = "Price cannot be negative")
        Long pricePaise,

        @JsonProperty("per_user_limit")
        @JsonAlias({"per_user_limit", "perUserLimit"})
        Integer perUserLimit,

        @JsonProperty("total_seats")
        @JsonAlias({"total_seats", "totalSeats"})
        Integer totalSeats,

        @JsonProperty("seats")
        @JsonAlias({"seats", "seatNumbers"})
        @NotEmpty(message = "At least one seat number is required")
        List<@NotBlank(message = "Seat numbers must not be blank") String> seatNumbers

) {
    public CreateShowRequest {
        if (perUserLimit == null || perUserLimit <= 0) {
            perUserLimit = 4;
        }
        if (totalSeats == null || totalSeats <= 0) {
            totalSeats = (seatNumbers != null) ? seatNumbers.size() : 0;
        }
    }
}
