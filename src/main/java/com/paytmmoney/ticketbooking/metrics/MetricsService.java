package com.paytmmoney.ticketbooking.metrics;

import com.paytmmoney.ticketbooking.enums.SeatStatus;
import com.paytmmoney.ticketbooking.repository.ShowSeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service exposing custom Micrometer and Prometheus metrics for the seat reservation system.
 */
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final Timer reservationTimer;
    private final ShowSeatRepository showSeatRepository;
    private final ConcurrentMap<String, Counter> confirmedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> declinedCounters = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry, @Lazy ShowSeatRepository showSeatRepository) {
        this.registry = registry;
        this.showSeatRepository = showSeatRepository;
        this.reservationTimer = Timer.builder("reservation_duration_seconds")
                .description("Execution duration of reservation requests in seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Register seats_available gauge per prompt specification
        Gauge.builder("seats_available", () -> (double) showSeatRepository.countByStatus(SeatStatus.AVAILABLE))
                .description("Current number of available seats across shows")
                .register(registry);
    }

    /**
     * Increments the confirmed reservations counter tagged by show_id.
     */
    public void incrementConfirmed(UUID showId) {
        String key = showId != null ? showId.toString() : "unknown";
        confirmedCounters.computeIfAbsent(key, id ->
                Counter.builder("reservations_confirmed_total")
                        .tag("show_id", id)
                        .description("Total number of confirmed reservations")
                        .register(registry)
        ).increment();
    }

    /**
     * Increments the declined reservations counter tagged by reason.
     */
    public void incrementDeclined(String reason) {
        String key = reason != null ? reason : "unknown";
        declinedCounters.computeIfAbsent(key, r ->
                Counter.builder("reservations_declined_total")
                        .tag("reason", r)
                        .description("Total number of declined reservations")
                        .register(registry)
        ).increment();
    }

    /**
     * Records duration of a reservation request.
     */
    public Timer getReservationTimer() {
        return reservationTimer;
    }
}
