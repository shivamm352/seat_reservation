package com.paytmmoney.ticketbooking;

import com.paytmmoney.ticketbooking.dto.CancelResponse;
import com.paytmmoney.ticketbooking.dto.CreateShowRequest;
import com.paytmmoney.ticketbooking.dto.ReservationResponse;
import com.paytmmoney.ticketbooking.dto.ReserveRequest;
import com.paytmmoney.ticketbooking.dto.ShowResponse;
import com.paytmmoney.ticketbooking.entity.ShowSeat;
import com.paytmmoney.ticketbooking.enums.SeatStatus;
import com.paytmmoney.ticketbooking.repository.ReservationRepository;
import com.paytmmoney.ticketbooking.repository.ShowSeatRepository;
import com.paytmmoney.ticketbooking.security.JwtService;
import com.paytmmoney.ticketbooking.service.ShowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReservationConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ticketbooking_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ShowService showService;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JwtService jwtService;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders createAuthHeaders(String userId) {
        String token = jwtService.generateToken(userId, "USER");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @DisplayName("Hot-Seat Race: 100 concurrent threads fight for single seat A12")
    void testHotSeatRaceCondition() throws Exception {
        // Create a show with seat A12
        CreateShowRequest showRequest = new CreateShowRequest(
                "Hot Seat Rock Concert",
                100000L,
                4,
                1,
                List.of("A12")
        );
        ShowResponse show = showService.createShow(showRequest);
        UUID showId = show.id();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger status201Count = new AtomicInteger(0);
        AtomicInteger status409Count = new AtomicInteger(0);
        AtomicInteger status5xxCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String userId = "user_hot_" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ReserveRequest req = new ReserveRequest(
                            showId,
                            List.of("A12"),
                            UUID.randomUUID().toString()
                    );
                    HttpEntity<ReserveRequest> entity = new HttpEntity<>(req, createAuthHeaders(userId));
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            getBaseUrl() + "/shows/" + showId + "/reserve",
                            entity,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        status201Count.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        status409Count.incrementAndGet();
                    } else if (response.getStatusCode().is5xxServerError()) {
                        status5xxCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown(); // simultaneous trigger
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(status201Count.get())
                .as("Exactly 1 thread should successfully reserve the seat")
                .isEqualTo(1);
        assertThat(status409Count.get())
                .as("Exactly 99 threads should receive 409 Conflict")
                .isEqualTo(99);
        assertThat(status5xxCount.get())
                .as("Zero threads should receive 5xx server errors")
                .isEqualTo(0);

        List<ShowSeat> seats = showSeatRepository.findByShowIdOrderBySeatNumberAsc(showId);
        assertThat(seats).hasSize(1);
        assertThat(seats.get(0).getStatus()).isEqualTo(SeatStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Per-User Limit: 1 user sends 10 parallel requests on show with limit 4")
    void testPerUserLimitConcurrency() throws Exception {
        List<String> seatLabels = List.of("B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "B9", "B10");
        CreateShowRequest showRequest = new CreateShowRequest(
                "Per User Limit Play",
                50000L,
                4,
                10,
                seatLabels
        );
        ShowResponse show = showService.createShow(showRequest);
        UUID showId = show.id();
        String userId = "greedy_user_1";

        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            final String seat = seatLabels.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ReserveRequest req = new ReserveRequest(
                            showId,
                            List.of(seat),
                            UUID.randomUUID().toString()
                    );
                    HttpEntity<ReserveRequest> entity = new HttpEntity<>(req, createAuthHeaders(userId));
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            getBaseUrl() + "/shows/" + showId + "/reserve",
                            entity,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        limitExceededCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get())
                .as("Exactly 4 reservations should succeed (matching limit)")
                .isEqualTo(4);
        assertThat(limitExceededCount.get())
                .as("Exactly 6 reservations should be rejected with 409 Conflict")
                .isEqualTo(6);

        int confirmedInDb = reservationRepository.countConfirmedSeatsForUser(showId, userId);
        assertThat(confirmedInDb).isEqualTo(4);
    }

    @Test
    @DisplayName("Idempotent Replay: 50 concurrent requests with identical idempotency key")
    void testIdempotencyRaceCondition() throws Exception {
        CreateShowRequest showRequest = new CreateShowRequest(
                "Idempotent Movie",
                25000L,
                4,
                1,
                List.of("C1")
        );
        ShowResponse show = showService.createShow(showRequest);
        UUID showId = show.id();
        String userId = "idempotent_user";
        String sharedKey = "shared-idempotency-key-c1";

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger validResponseCount = new AtomicInteger(0);
        AtomicInteger error5xxCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ReserveRequest req = new ReserveRequest(
                            showId,
                            List.of("C1"),
                            sharedKey
                    );
                    HttpEntity<ReserveRequest> entity = new HttpEntity<>(req, createAuthHeaders(userId));
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            getBaseUrl() + "/shows/" + showId + "/reserve",
                            entity,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                        validResponseCount.incrementAndGet();
                    } else if (response.getStatusCode().is5xxServerError()) {
                        error5xxCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(error5xxCount.get()).isEqualTo(0);
        assertThat(validResponseCount.get()).isEqualTo(threadCount);

        int userConfirmedSeats = reservationRepository.countConfirmedSeatsForUser(showId, userId);
        assertThat(userConfirmedSeats).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancellation & Re-Booking: User 1 cancels, User 2 books cleanly")
    void testCancellationAndRebooking() {
        CreateShowRequest showRequest = new CreateShowRequest(
                "Rebooking Drama",
                30000L,
                4,
                1,
                List.of("D1")
        );
        ShowResponse show = showService.createShow(showRequest);
        UUID showId = show.id();

        String user1 = "alice";
        String user2 = "bob";

        // Step 1: Alice books D1
        ReserveRequest req1 = new ReserveRequest(showId, List.of("D1"), "alice-key-1");
        ResponseEntity<ReservationResponse> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/shows/" + showId + "/reserve",
                new HttpEntity<>(req1, createAuthHeaders(user1)),
                ReservationResponse.class
        );
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID reservationId = Objects.requireNonNull(res1.getBody()).id();

        // Step 2: Bob tries to cancel Alice's reservation -> 403 Forbidden
        ResponseEntity<String> cancelByBob = restTemplate.postForEntity(
                getBaseUrl() + "/reservations/" + reservationId + "/cancel",
                new HttpEntity<>(null, createAuthHeaders(user2)),
                String.class
        );
        assertThat(cancelByBob.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Step 3: Alice cancels her own reservation -> 200 OK
        ResponseEntity<CancelResponse> cancelByAlice = restTemplate.postForEntity(
                getBaseUrl() + "/reservations/" + reservationId + "/cancel",
                new HttpEntity<>(null, createAuthHeaders(user1)),
                CancelResponse.class
        );
        assertThat(cancelByAlice.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 4: Bob can now successfully book D1 -> 201 Created
        ReserveRequest req2 = new ReserveRequest(showId, List.of("D1"), "bob-key-1");
        ResponseEntity<ReservationResponse> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/shows/" + showId + "/reserve",
                new HttpEntity<>(req2, createAuthHeaders(user2)),
                ReservationResponse.class
        );
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
