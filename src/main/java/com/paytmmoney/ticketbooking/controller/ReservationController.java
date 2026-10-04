package com.paytmmoney.ticketbooking.controller;

import com.paytmmoney.ticketbooking.dto.BookingResult;
import com.paytmmoney.ticketbooking.dto.CancelResponse;
import com.paytmmoney.ticketbooking.dto.ReservationResponse;
import com.paytmmoney.ticketbooking.dto.ReserveRequest;
import com.paytmmoney.ticketbooking.security.UserPrincipal;
import com.paytmmoney.ticketbooking.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody ReserveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        // Resolve idempotency key: header takes precedence over request body
        String resolvedKey = StringUtils.hasText(idempotencyKeyHeader)
                ? idempotencyKeyHeader.trim()
                : (StringUtils.hasText(request.idempotencyKey()) ? request.idempotencyKey().trim() : null);

        if (!StringUtils.hasText(resolvedKey)) {
            throw new IllegalArgumentException("Idempotency key required");
        }

        String userId = principal.userId();
        BookingResult result = reservationService.reserveSeats(showId, userId, resolvedKey, request);

        if (result.isNewBooking()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.reservation());
        } else {
            return ResponseEntity.ok(result.reservation());
        }
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<CancelResponse> cancel(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String userId = principal.userId();
        CancelResponse response = reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok(response);
    }
}
