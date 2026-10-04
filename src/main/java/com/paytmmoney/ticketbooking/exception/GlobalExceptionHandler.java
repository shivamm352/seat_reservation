package com.paytmmoney.ticketbooking.exception;

import com.paytmmoney.ticketbooking.dto.ErrorResponse;
import com.paytmmoney.ticketbooking.filter.TraceIdFilter;
import com.paytmmoney.ticketbooking.metrics.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MetricsService metricsService;

    public GlobalExceptionHandler(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    private String getTraceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        return traceId != null ? traceId : "unknown";
    }

    @ExceptionHandler(SeatAlreadyTakenException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyTaken(SeatAlreadyTakenException ex) {
        metricsService.incrementDeclined("seat_taken");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "SEAT_ALREADY_TAKEN", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(BookingLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleBookingLimitExceeded(BookingLimitExceededException ex) {
        metricsService.incrementDeclined("per_user_limit");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "PER_USER_LIMIT_EXCEEDED", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        metricsService.incrementDeclined("idempotent_replay");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "IDEMPOTENCY_CONFLICT", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler({
            SeatNotFoundException.class,
            ShowNotFoundException.class,
            ReservationNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        metricsService.incrementDeclined("invalid_seats");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(ReservationForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ReservationForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", message, getTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handleLockTimeouts(Exception ex) {
        metricsService.incrementDeclined("concurrency_conflict");
        log.warn("Lock timeout / contention: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SERVER_BUSY_RETRY", "High contention on requested seat, please retry", getTraceId()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrityViolation(DataIntegrityViolationException ex) {
        metricsService.incrementDeclined("concurrency_conflict");
        log.warn("Data integrity violation / race condition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("IDEMPOTENCY_RACE", "Concurrent duplicate request detected", getTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled server exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred", getTraceId()));
    }
}
