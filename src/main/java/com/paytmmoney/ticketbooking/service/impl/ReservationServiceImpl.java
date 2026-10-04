package com.paytmmoney.ticketbooking.service.impl;

import com.paytmmoney.ticketbooking.dto.BookingResult;
import com.paytmmoney.ticketbooking.dto.CancelResponse;
import com.paytmmoney.ticketbooking.dto.ReservationResponse;
import com.paytmmoney.ticketbooking.dto.ReserveRequest;
import com.paytmmoney.ticketbooking.entity.Reservation;
import com.paytmmoney.ticketbooking.entity.ReservationSeat;
import com.paytmmoney.ticketbooking.entity.Show;
import com.paytmmoney.ticketbooking.entity.ShowSeat;
import com.paytmmoney.ticketbooking.enums.ReservationStatus;
import com.paytmmoney.ticketbooking.enums.SeatStatus;
import com.paytmmoney.ticketbooking.exception.*;
import com.paytmmoney.ticketbooking.metrics.MetricsService;
import com.paytmmoney.ticketbooking.repository.ReservationRepository;
import com.paytmmoney.ticketbooking.repository.ShowRepository;
import com.paytmmoney.ticketbooking.repository.ShowSeatRepository;
import com.paytmmoney.ticketbooking.repository.ShowUserLockRepository;
import com.paytmmoney.ticketbooking.service.ReservationService;
import com.paytmmoney.ticketbooking.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowUserLockRepository showUserLockRepository;
    private final ReservationRepository reservationRepository;
    private final MetricsService metricsService;

    public ReservationServiceImpl(
            ShowRepository showRepository,
            ShowSeatRepository showSeatRepository,
            ShowUserLockRepository showUserLockRepository,
            ReservationRepository reservationRepository,
            MetricsService metricsService) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.showUserLockRepository = showUserLockRepository;
        this.reservationRepository = reservationRepository;
        this.metricsService = metricsService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResult reserveSeats(UUID showId, String userId, String idempotencyKey, ReserveRequest request) {
        return metricsService.getReservationTimer().record(() -> {
            // Step 6.1: Normalize requested seats by sorting lexicographically
            List<String> sortedSeats = new ArrayList<>(request.seatNumbers());
            Collections.sort(sortedSeats);

            // Step 6.2: Compute request hash
            String requestHash = HashUtil.sha256(showId + ":" + String.join(",", sortedSeats));

            // Step 6.3: Lock user row for this show (serialize per-user concurrency)
            showUserLockRepository.upsertUserLockRow(showId, userId);
            showUserLockRepository.lockUserRow(showId, userId);

            // Step 6.4: Check idempotency
            Optional<Reservation> existingOpt = reservationRepository
                    .findByShowIdAndUserIdAndIdempotencyKey(showId, userId, idempotencyKey);

            if (existingOpt.isPresent()) {
                Reservation existing = existingOpt.get();
                if (existing.getRequestHash().equals(requestHash)) {
                    // Same request hash -> idempotent replay
                    return new BookingResult(ReservationResponse.from(existing), false);
                } else {
                    // Different request hash -> conflict
                    throw new IdempotencyConflictException(
                            "Idempotency key has already been used with a different request payload");
                }
            }

            // Fetch show to check metadata
            Show show = showRepository.findById(showId)
                    .orElseThrow(() -> new ShowNotFoundException("Show not found with id: " + showId));

            // Step 6.5: Check user booking limit
            int currentConfirmed = reservationRepository.countConfirmedSeatsForUser(showId, userId);
            if (currentConfirmed + sortedSeats.size() > show.getPerUserLimit()) {
                throw new BookingLimitExceededException(
                        "Booking limit exceeded. Current: " + currentConfirmed +
                        ", requested: " + sortedSeats.size() +
                        ", limit: " + show.getPerUserLimit());
            }

            // Step 6.6: Lock seats with SELECT FOR UPDATE
            List<ShowSeat> lockedSeats = showSeatRepository.findSeatsForUpdate(showId, sortedSeats);
            if (lockedSeats.size() != sortedSeats.size()) {
                throw new SeatNotFoundException("One or more requested seats do not exist for show: " + showId);
            }

            boolean anyUnavailable = lockedSeats.stream()
                    .anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE);
            if (anyUnavailable) {
                throw new SeatAlreadyTakenException("One or more requested seats are already taken");
            }

            // Step 6.7: Commit state
            long totalAmountPaise = show.getPricePaise() * sortedSeats.size();
            Reservation reservation = new Reservation(
                    show,
                    userId,
                    idempotencyKey,
                    requestHash,
                    totalAmountPaise
            );

            for (ShowSeat seat : lockedSeats) {
                ReservationSeat reservationSeat = new ReservationSeat(reservation, seat);
                reservation.addSeat(reservationSeat);
            }

            Reservation savedReservation = reservationRepository.save(reservation);

            for (ShowSeat seat : lockedSeats) {
                seat.setStatus(SeatStatus.CONFIRMED);
                seat.setReservationId(savedReservation.getId());
            }
            showSeatRepository.saveAll(lockedSeats);

            // Increment Prometheus metric
            metricsService.incrementConfirmed(showId);

            return new BookingResult(ReservationResponse.from(savedReservation), true);
        });
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancelResponse cancelReservation(UUID reservationId, String authenticatedUserId) {
        // Lock reservation row
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with id: " + reservationId));

        // Verify ownership
        if (!reservation.getUserId().equals(authenticatedUserId)) {
            throw new ReservationForbiddenException("User is not authorized to cancel this reservation");
        }

        // Idempotent cancellation check
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return new CancelResponse(
                    reservationId,
                    ReservationStatus.CANCELLED,
                    reservation.getCancelledAt()
            );
        }

        // Lock associated seats to prevent concurrent modifications
        List<UUID> seatIds = reservation.getSeats().stream()
                .map(rs -> rs.getSeat().getId())
                .toList();

        List<ShowSeat> seats = showSeatRepository.findSeatsByIdsForUpdate(seatIds);
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setReservationId(null);
        }
        showSeatRepository.saveAll(seats);

        // Update reservation status
        OffsetDateTime now = OffsetDateTime.now();
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
        reservationRepository.save(reservation);

        return new CancelResponse(reservationId, ReservationStatus.CANCELLED, now);
    }
}
