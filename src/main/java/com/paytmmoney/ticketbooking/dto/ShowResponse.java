package com.paytmmoney.ticketbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytmmoney.ticketbooking.entity.Show;
import com.paytmmoney.ticketbooking.entity.ShowSeat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only response for show details.
 * Contains per-seat status and counts for reconciliation invariant checking.
 */
public record ShowResponse(
        UUID id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats,
        Map<String, Integer> counts,
        List<SeatDto> seats,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
    @JsonProperty("pricePaise")
    public long pricePaiseCamel() {
        return pricePaise;
    }

    @JsonProperty("perUserLimit")
    public int perUserLimitCamel() {
        return perUserLimit;
    }

    @JsonProperty("totalSeats")
    public int totalSeatsCamel() {
        return totalSeats;
    }

    public static ShowResponse of(Show show, List<ShowSeat> showSeats) {
        int available = 0;
        int held = 0;
        int confirmed = 0;

        if (showSeats != null) {
            for (ShowSeat s : showSeats) {
                switch (s.getStatus()) {
                    case AVAILABLE -> available++;
                    case HELD -> held++;
                    case CONFIRMED -> confirmed++;
                }
            }
        }

        Map<String, Integer> counts = Map.of(
                "available", available,
                "held", held,
                "confirmed", confirmed
        );

        List<SeatDto> seatDtos = (showSeats != null)
                ? showSeats.stream().map(SeatDto::from).toList()
                : List.of();

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),
                counts,
                seatDtos,
                show.getCreatedAt()
        );
    }

    public static ShowResponse from(Show show) {
        return of(show, List.of());
    }
}
