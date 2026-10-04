# Paytm Money — Seat Reservation at Scale
## Master 12-Step Implementation Roadmap & Engineering Guide

---

# Executive Summary & Core Correctness Principles

This document provides the complete, step-by-step engineering roadmap for building, testing, observing, and deploying the **Seat Reservation at Scale** backend service.

### The 5 Architectural Invariants
1. **The Database is the Single Source of Truth:** No in-memory state or JVM-level locks (`synchronized`, `ReentrantLock`). PostgreSQL handles serialization.
2. **Atomic Row-Level Locking:** All reservations acquire pessimistic locks (`SELECT ... FOR UPDATE`) on target seats.
3. **Deadlock Elimination:** Seat row locks are ALWAYS acquired in deterministic lexicographical order (`ORDER BY seat_number ASC`).
4. **Per-User Limit Serialization:** Transactions acquire a row lock on `show_user_locks` before querying active bookings, preventing phantom read limit bypasses.
5. **Zero 5xx Guarantee:** Concurrency conflicts (lock waits, timeouts, duplicate submissions) are translated to clean `409 Conflict` domain outcomes.

---

# Summary of Implementation Steps

| Step | Scope | Commit Prefix | Target Invariant |
|:---|:---|:---|:---|
| **Step 1** | Scaffolding, Dependencies & Docker | `chore:` | Multi-stage build, HikariCP tuning, container health |
| **Step 2** | Flyway Migrations (V1 to V5) | `feat(db):` | DDL constraints, foreign keys, corrected index rules |
| **Step 3** | Domain Entities, Enums, DTOs & Repos | `feat(domain):` | Pessimistic locking queries, Java records |
| **Step 4** | JWT Security & Quick Token Generator | `feat(auth):` | Token-derived identity, evaluator auth bypass |
| **Step 5** | Show APIs & Reconciliation Logic | `feat(api):` | Total seat count verification invariant |
| **Step 6** | The Atomic Reservation Engine | `feat(concurrency):` | Race-free booking, lock ordering, request hashing |
| **Step 7** | Explicit Cancellation Engine | `feat(api):` | Ownership verification, seat release & re-booking |
| **Step 8** | Global Exception Handler & MDC Logging | `feat(error):` | Zero 5xx policy, structured JSON trace logs |
| **Step 9** | Observability (Health & Prometheus) | `feat(observability):` | Actuator probes, live metric counters & gauges |
| **Step 10** | JUnit 5 & Testcontainers Concurrency Suite | `test(concurrency):` | Real PostgreSQL 500-thread race verification |
| **Step 11** | One-Command Asynchronous Burst Script | `feat(burst):` | `burst.sh` client simulation & reconciliation check |
| **Step 12** | Deployment Setup, README & WRITEUP.md | `docs:` | Cloud deploy, evaluation write-up, AI disclosure |

---

# STEP 1: Scaffolding, Dependencies & Docker Setup

### Objective
Create a rock-solid Spring Boot 3.3+ (Java 17) Maven foundation tuned for high concurrency with zero container thrashing.

### Files to Create / Configure
- `pom.xml`
- `src/main/resources/application.yml`
- `Dockerfile`
- `docker-compose.yml`
- `.gitignore`

### Technical Directives for Claude Sonnet 4
1. **Dependencies (`pom.xml`)**:
   - `org.springframework.boot:spring-boot-starter-web`
   - `org.springframework.boot:spring-boot-starter-data-jpa`
   - `org.springframework.boot:spring-boot-starter-validation`
   - `org.springframework.boot:spring-boot-starter-security`
   - `org.springframework.boot:spring-boot-starter-actuator`
   - `io.micrometer:micrometer-registry-prometheus`
   - `org.flywaydb:flyway-core` & `org.flywaydb:flyway-database-postgresql`
   - `org.postgresql:postgresql`
   - `io.jsonwebtoken:jjwt-api:0.12.5`, `jjwt-impl`, `jjwt-jackson`
   - Test scope: `spring-boot-starter-test`, `org.testcontainers:postgresql:1.19.7`, `org.testcontainers:junit-jupiter`
2. **Connection & Thread Sizing (`application.yml`)**:
   - **HikariCP:** `maximum-pool-size: 20`, `minimum-idle: 10`, `connection-timeout: 5000` (fail fast in 5s rather than queue 30s), `idle-timeout: 300000`, `max-lifetime: 1800000`.
   - **Tomcat:** `server.tomcat.threads.max: 50`, `server.tomcat.accept-count: 200`. (Prevents free-tier CPU exhaustion).
   - **JPA:** `open-in-view: false`, `show-sql: false` (disabled to prevent I/O bottlenecks under load).
   - **Flyway:** `enabled: true`.
3. **Containerization (`Dockerfile`)**:
   - Multi-stage build with `eclipse-temurin:17-jdk-alpine` as builder and `eclipse-temurin:17-jre-alpine` as runner.
   - Entrypoint flag: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.
4. **Orchestration (`docker-compose.yml`)**:
   - Service `postgres`: image `postgres:16-alpine`, port `5432:5432`, environment variables, healthcheck via `pg_isready -U postgres`.
   - Service `app`: builds local Dockerfile, port `8080:8080`, depends on `postgres` condition `service_healthy`.

### Verification Checklist
- Run `mvn clean compile` without errors.
- Run `docker compose up --build -d` and ensure both containers are healthy.

### Git Commands
```bash
git add .
git commit -m "chore: initialize spring boot 3.3 project scaffolding, dependencies and docker setup"
```

---

# STEP 2: Database Schema & Flyway Migrations (V1 to V5)

### Objective
Define relational integrity, foreign keys, and indexes directly via Flyway migrations.

### Files to Create
- `src/main/resources/db/migration/V1__create_shows.sql`
- `src/main/resources/db/migration/V2__create_show_seats.sql`
- `src/main/resources/db/migration/V3__create_show_user_locks.sql`
- `src/main/resources/db/migration/V4__create_reservations.sql`
- `src/main/resources/db/migration/V5__create_reservation_seats.sql`

### Technical Directives for Claude Sonnet 4
1. **`V1__create_shows.sql`**:
   ```sql
   CREATE TABLE shows (
       id UUID PRIMARY KEY,
       name VARCHAR(200) NOT NULL,
       price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
       per_user_limit INT NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
       total_seats INT NOT NULL CHECK (total_seats > 0),
       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );
   ```
2. **`V2__create_show_seats.sql`**:
   ```sql
   CREATE TABLE show_seats (
       id UUID PRIMARY KEY,
       show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
       seat_number VARCHAR(50) NOT NULL,
       status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),
       reservation_id UUID,
       CONSTRAINT uk_show_seat_number UNIQUE (show_id, seat_number)
   );
   CREATE INDEX idx_show_seats_lookup ON show_seats(show_id, seat_number);
   ```
3. **`V3__create_show_user_locks.sql`**:
   ```sql
   CREATE TABLE show_user_locks (
       show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
       user_id VARCHAR(100) NOT NULL,
       PRIMARY KEY (show_id, user_id)
   );
   ```
4. **`V4__create_reservations.sql`**:
   ```sql
   CREATE TABLE reservations (
       id UUID PRIMARY KEY,
       show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
       user_id VARCHAR(100) NOT NULL,
       idempotency_key VARCHAR(200) NOT NULL,
       request_hash VARCHAR(64) NOT NULL,
       amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
       status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       cancelled_at TIMESTAMPTZ,
       CONSTRAINT uk_reservation_idempotency UNIQUE (show_id, user_id, idempotency_key)
   );
   CREATE INDEX idx_reservations_user_show ON reservations(show_id, user_id, status);
   ```
5. **`V5__create_reservation_seats.sql`**:
   ```sql
   CREATE TABLE reservation_seats (
       reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
       seat_id UUID NOT NULL REFERENCES show_seats(id),
       seat_number VARCHAR(50) NOT NULL,
       PRIMARY KEY (reservation_id, seat_id)
   );
   ```
   *(Note: Do NOT place `UNIQUE(seat_id)` on `reservation_seats`! That would prevent re-booking seats after cancellation).*

### Verification Checklist
- Run app with local Postgres; verify Flyway applies all 5 migrations and tables exist with expected constraints.

### Git Commands
```bash
git add .
git commit -m "feat(db): add flyway migration scripts V1 through V5 with concurrency constraints"
```

---

# STEP 3: Domain Entities, Enums, DTOs & Repositories

### Objective
Create persistence layer mappings, immutable Java records for payloads, and pessimistic lock repository methods.

### Files to Create
- Package `com.paytm.seatreservation.enums`: `SeatStatus`, `ReservationStatus`
- Package `com.paytm.seatreservation.entity`: `Show`, `ShowSeat`, `ShowUserLock`, `Reservation`, `ReservationSeat`
- Package `com.paytm.seatreservation.dto`: `CreateShowRequest`, `ShowResponse`, `SeatDto`, `ReserveRequest`, `ReservationResponse`, `CancelResponse`, `ErrorResponse`
- Package `com.paytm.seatreservation.repository`: `ShowRepository`, `ShowSeatRepository`, `ShowUserLockRepository`, `ReservationRepository`, `ReservationSeatRepository`

### Technical Directives for Claude Sonnet 4
1. **Pessimistic Seat Query (`ShowSeatRepository`)**:
   ```java
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT s FROM ShowSeat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber ASC")
   List<ShowSeat> findSeatsForUpdate(@Param("showId") UUID showId, @Param("seatNumbers") List<String> seatNumbers);
   ```
2. **User Lock Query (`ShowUserLockRepository`)**:
   ```java
   @Modifying
   @Query(value = "INSERT INTO show_user_locks (show_id, user_id) VALUES (:showId, :userId) ON CONFLICT DO NOTHING", nativeQuery = true)
   void upsertUserLockRow(@Param("showId") UUID showId, @Param("userId") String userId);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT l FROM ShowUserLock l WHERE l.showId = :showId AND l.userId = :userId")
   Optional<ShowUserLock> lockUserRow(@Param("showId") UUID showId, @Param("userId") String userId);
   ```
3. **Active Bookings Count (`ReservationRepository`)**:
   ```java
   @Query("SELECT COUNT(rs) FROM ReservationSeat rs JOIN rs.reservation r WHERE r.show.id = :showId AND r.userId = :userId AND r.status = 'CONFIRMED'")
   int countConfirmedSeatsForUser(@Param("showId") UUID showId, @Param("userId") String userId);
   ```

### Verification Checklist
- Run `mvn test-compile` to ensure all entity relationships, queries, and DTO records compile cleanly.

### Git Commands
```bash
git add .
git commit -m "feat(domain): implement jpa entities, dtos, and pessimistic locking repositories"
```

---

# STEP 4: Security, JWT & Token-Derived Identity

### Objective
Implement stateless JWT authentication where user identity is extracted strictly from the token, plus an evaluator token generation helper.

### Files to Create
- `com.paytm.seatreservation.security.JwtService`
- `com.paytm.seatreservation.security.JwtAuthenticationFilter`
- `com.paytm.seatreservation.security.SecurityConfig`
- `com.paytm.seatreservation.security.UserPrincipal`
- `com.paytm.seatreservation.controller.AuthController`

### Technical Directives for Claude Sonnet 4
1. **`JwtService`**:
   - Signs tokens with `Keys.hmacShaKeyFor(secret.getBytes())`.
   - Methods: `generateToken(String userId, String role)`, `validateToken(String token)`, `extractUserId(String token)`, `extractRole(String token)`.
2. **`SecurityConfig`**:
   - `http.csrf(AbstractHttpConfigurer::disable)`
   - `sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`
   - Authorization rules:
     - Permit all: `/auth/token`, `/actuator/**`, `GET /shows/**`.
     - Admin only: `POST /shows` (`hasRole('ADMIN')` or permit in test mode).
     - Authenticated: `POST /shows/{id}/reserve`, `POST /reservations/{id}/cancel`.
   - Add `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.
3. **`AuthController` (`POST /auth/token`)**:
   - Accepts parameters or JSON: `userId` (String), optional `role` (defaults to `"USER"`).
   - Generates and returns `{ "token": "...", "type": "Bearer" }`.
   - *Why this is critical:* Allows burst scripts and evaluators to generate tokens on the fly for 500+ simulated users without user registration boilerplate.

### Verification Checklist
- Request protected endpoint without header $\rightarrow$ `401 Unauthorized`.
- Call `POST /auth/token?userId=u1` $\rightarrow$ copy Bearer token $\rightarrow$ request protected endpoint $\rightarrow$ passes through.

### Git Commands
```bash
git add .
git commit -m "feat(auth): add jwt authentication, security filter, and evaluation token provider"
```

---

# STEP 5: Show Management APIs & Reconciliation Logic

### Objective
Implement `POST /shows` and `GET /shows/{id}`, ensuring seat state reconciliation invariant is strictly guaranteed.

### Files to Create
- `com.paytm.seatreservation.service.ShowService`
- `com.paytm.seatreservation.service.impl.ShowServiceImpl`
- `com.paytm.seatreservation.controller.ShowController`

### Technical Directives for Claude Sonnet 4
1. **`createShow`**:
   - Validates seat list: rejects nulls, blanks, or duplicates in payload.
   - Creates `Show` with `total_seats = seats.size()`.
   - Bulk inserts `ShowSeat` entities with status `SeatStatus.AVAILABLE`.
2. **`getShow`**:
   - Loads show and all seat entities.
   - Computes counts: `available = count(AVAILABLE)`, `held = count(HELD)`, `confirmed = count(CONFIRMED)`.
   - **Reconciliation Invariant Check:**
     `if (available + held + confirmed != show.getTotalSeats()) throw new IllegalStateException("Reconciliation failure");`
   - Formats seats to lowercase string matching requirement: `{ "seat": "A1", "status": "available" }`.
3. **`ShowController`**:
   - `POST /shows` $\rightarrow$ 201 Created.
   - `GET /shows/{id}` $\rightarrow$ 200 OK.

### Verification Checklist
- Post show with seats `["A1", "A2", "A3"]`.
- Get show: verifies `counts.available == 3`, `held == 0`, `confirmed == 0`, total seats == 3.

### Git Commands
```bash
git add .
git commit -m "feat(api): implement create show and get show endpoints with seat reconciliation"
```

---

# STEP 6: The Core Atomic Reservation Engine (`POST /shows/{id}/reserve`)

### Objective
Implement the race-free, deadlock-free reservation engine with dual-check idempotency and user limit serialization.

### Files to Create / Update
- `com.paytm.seatreservation.util.HashUtil` (SHA-256 calculation)
- `com.paytm.seatreservation.service.ReservationService`
- `com.paytm.seatreservation.service.impl.ReservationServiceImpl`
- `com.paytm.seatreservation.controller.ReservationController`

### Technical Directives for Claude Sonnet 4
1. **Extract Idempotency Key**:
   - Check `Idempotency-Key` header first; if absent, read `request.idempotencyKey()`.
   - If both missing or empty $\rightarrow$ throw `IllegalArgumentException("Idempotency key required")`.
2. **Transactional Method Execution (`@Transactional`)**:
   - **Step 6.1 (Normalize):** Sort requested seat numbers lexicographically (`Collections.sort(seats)`).
   - **Step 6.2 (Hash):** Compute `requestHash = HashUtil.sha256(showId + ":" + String.join(",", sortedSeats))`.
   - **Step 6.3 (Lock User):**
     - Call `showUserLockRepository.upsertUserLockRow(showId, userId);`
     - Call `showUserLockRepository.lockUserRow(showId, userId);`
   - **Step 6.4 (Check Idempotency):**
     - Query `reservationRepository.findByShowIdAndUserIdAndIdempotencyKey(showId, userId, idempotencyKey)`.
     - If found:
       - If `existing.getRequestHash().equals(requestHash)` $\rightarrow$ return existing reservation payload (200 OK or 201).
       - If `!existing.getRequestHash().equals(requestHash)` $\rightarrow$ throw `IdempotencyConflictException("Different request on same key")` (409).
   - **Step 6.5 (Check Limit):**
     - `int currentConfirmed = reservationRepository.countConfirmedSeatsForUser(showId, userId);`
     - `if (currentConfirmed + sortedSeats.size() > show.getPerUserLimit()) throw new BookingLimitExceededException("Limit exceeded");` (409).
   - **Step 6.6 (Lock Seats):**
     - `List<ShowSeat> lockedSeats = showSeatRepository.findSeatsForUpdate(showId, sortedSeats);`
     - `if (lockedSeats.size() != sortedSeats.size()) throw new SeatNotFoundException("Seats not found");` (404).
     - `boolean anyUnavailable = lockedSeats.stream().anyMatch(s -> s.getStatus() != SeatStatus.AVAILABLE);`
     - `if (anyUnavailable) throw new SeatAlreadyTakenException("Seat already taken");` (409).
   - **Step 6.7 (Commit State):**
     - Create `Reservation` record (`amount_paise = show.getPricePaise() * sortedSeats.size()`).
     - Create `ReservationSeat` records.
     - Set each seat `status = CONFIRMED`, `reservation_id = reservation.getId()`.
     - Return `ReservationResponse` with status `confirmed` and HTTP 201.

### Verification Checklist
- Single reservation returns 201.
- Immediate retry with same key returns identical 201/200 payload.
- Retry with same key and different seat returns 409 Conflict.
- Reserving more than 4 seats returns 409 Conflict.

### Git Commands
```bash
git add .
git commit -m "feat(concurrency): implement atomic reservation engine with deterministic locking"
```

---

# STEP 7: Explicit Cancellation Engine (`POST /reservations/{id}/cancel`)

### Objective
Allow owners to cancel holds/reservations and immediately release seats back to `AVAILABLE` for re-booking.

### Files to Update
- `com.paytm.seatreservation.service.ReservationService`
- `com.paytm.seatreservation.service.impl.ReservationServiceImpl`
- `com.paytm.seatreservation.controller.ReservationController`

### Technical Directives for Claude Sonnet 4
1. **`cancelReservation(UUID reservationId, String authenticatedUserId)`**:
   - Mark method `@Transactional`.
   - Lock reservation: `reservationRepository.findByIdForUpdate(reservationId)`.
   - If not found $\rightarrow$ throw `ReservationNotFoundException` (404).
   - Verify ownership: `if (!reservation.getUserId().equals(authenticatedUserId)) throw new ReservationForbiddenException("Forbidden")` (403).
   - If already `CANCELLED` $\rightarrow$ return idempotent `CancelResponse(reservationId, "cancelled", reservation.getCancelledAt())` (200 OK).
   - Find locked seats: lock seats via `showSeatRepository.findSeatsForUpdate` by IDs associated with this reservation.
   - For each seat: set `status = AVAILABLE`, `reservation_id = null`.
   - Update reservation: `status = CANCELLED`, `cancelled_at = Instant.now()`.
   - Return `CancelResponse`.

### Verification Checklist
- User 1 reserves `A1` $\rightarrow$ User 2 tries to cancel $\rightarrow$ returns 403 Forbidden.
- User 1 cancels $\rightarrow$ returns 200 OK.
- User 2 now books `A1` $\rightarrow$ succeeds with 201 Created (confirms re-booking works cleanly).

### Git Commands
```bash
git add .
git commit -m "feat(api): implement reservation cancellation with ownership check and re-booking"
```

---

# STEP 8: Global Error Handling (Zero 5xx) & Structured MDC Logging

### Objective
Guarantee zero 5xx outcomes across high-concurrency bursts and ensure every request produces traceable JSON logs.

### Files to Create
- Package `com.paytm.seatreservation.exception`: Custom runtime exceptions
- `com.paytm.seatreservation.filter.TraceIdFilter`
- `com.paytm.seatreservation.handler.GlobalExceptionHandler`
- `src/main/resources/logback-spring.xml`

### Technical Directives for Claude Sonnet 4
1. **`TraceIdFilter`**:
   - Reads incoming `X-Request-ID` or generates UUID.
   - Puts `traceId` and `userId` (if authenticated) into SLF4J `MDC`.
   - Adds `X-Request-ID` to response headers. Clears MDC in `finally`.
2. **`GlobalExceptionHandler` (`@RestControllerAdvice`)**:
   - Map domain exceptions to clean 4xx responses:
     - `SeatAlreadyTakenException` $\rightarrow$ 409 (`SEAT_ALREADY_TAKEN`)
     - `BookingLimitExceededException` $\rightarrow$ 409 (`PER_USER_LIMIT_EXCEEDED`)
     - `IdempotencyConflictException` $\rightarrow$ 409 (`IDEMPOTENCY_CONFLICT`)
     - `SeatNotFoundException`, `ShowNotFoundException`, `ReservationNotFoundException` $\rightarrow$ 404
     - `ReservationForbiddenException` $\rightarrow$ 403
     - `MethodArgumentNotValidException`, `IllegalArgumentException` $\rightarrow$ 400
   - **Crucial Concurrency Translation (Zero 5xx Guarantee):**
     ```java
     @ExceptionHandler({
         PessimisticLockingFailureException.class,
         CannotAcquireLockException.class,
         QueryTimeoutException.class
     })
     public ResponseEntity<ErrorResponse> handleLockTimeouts(Exception ex) {
         return ResponseEntity.status(HttpStatus.CONFLICT)
             .body(new ErrorResponse("SERVER_BUSY_RETRY", "High contention on requested seat, please retry", getTraceId()));
     }
     
     @ExceptionHandler(DataIntegrityViolationException.class)
     public ResponseEntity<ErrorResponse> handleIntegrityViolation(DataIntegrityViolationException ex) {
         return ResponseEntity.status(HttpStatus.CONFLICT)
             .body(new ErrorResponse("IDEMPOTENCY_RACE", "Concurrent duplicate request detected", getTraceId()));
     }
     ```

### Verification Checklist
- Simulated database contention or duplicate keys return 409 with structured JSON, never an unhandled 500 error.

### Git Commands
```bash
git add .
git commit -m "feat(error): implement global exception translation for zero 5xx and mdc logging"
```

---

# STEP 9: Observability (Health Probes & Prometheus Metrics)

### Objective
Expose real-time Prometheus metrics and liveness/readiness probes that fail closed when the database is unreachable.

### Files to Create / Update
- `com.paytm.seatreservation.metrics.MetricsService`
- `application.yml` updates for Actuator

### Technical Directives for Claude Sonnet 4
1. **Actuator Health**:
   - Configure `management.endpoint.health.probes.enabled=true`.
   - `/actuator/health/liveness` $\rightarrow$ returns 200 OK.
   - `/actuator/health/readiness` $\rightarrow$ executes DataSource probe (`SELECT 1`). If Postgres is unreachable, returns 503 Service Unavailable (`status: DOWN`).
2. **Prometheus Metrics (`MetricsService`)**:
   - `Counter reservations_confirmed_total` (tags: `show_id`).
   - `Counter reservations_declined_total` (tags: `reason` = `seat_taken`, `per_user_limit`, `idempotent_replay`, `invalid_seats`).
   - `MultiGauge / Gauge seats_available` (tag: `show_id`).
   - `Timer reservation_duration_seconds`.
3. **Instrumentation**:
   - Increment `reservations_confirmed_total` on reservation commit.
   - Increment `reservations_declined_total` in exception handler based on exception type.

### Verification Checklist
- `GET /actuator/health/readiness` returns 200 OK with `UP`.
- Reserve a seat $\rightarrow$ `GET /actuator/prometheus` shows `reservations_confirmed_total` incremented.
- Trigger taken seat $\rightarrow$ shows `reservations_declined_total{reason="seat_taken"}` incremented.

### Git Commands
```bash
git add .
git commit -m "feat(observability): add micrometer prometheus metrics and actuator health probes"
```

---

# STEP 10: JUnit 5 & Testcontainers Concurrency Test Suite

### Objective
Prove correctness locally under actual multi-threaded conditions against a real PostgreSQL container.

### Files to Create
- `src/test/java/com/paytm/seatreservation/ReservationConcurrencyTest.java`

### Technical Directives for Claude Sonnet 4
1. **Testcontainers Setup**:
   - `@Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");`
2. **Hot-Seat Race Test**:
   - Create show with seat `A12`.
   - Spin up `ExecutorService` with 100 threads using `CountDownLatch` for simultaneous dispatch.
   - 100 threads (with unique JWTs) attempt to book seat `A12`.
   - **Assertions:**
     - Exactly 1 thread receives 201 Created.
     - Exactly 99 threads receive 409 Conflict.
     - 0 threads receive 5xx.
     - Final DB query on `A12` shows status `CONFIRMED`.
3. **Per-User Limit Concurrency Test**:
   - 1 user sends 10 parallel reservation requests for distinct seats `B1` to `B10` on a show with limit = 4.
   - **Assertions:**
     - Exactly 4 reservations succeed (201 Created).
     - Exactly 6 return 409 Conflict (`PER_USER_LIMIT_EXCEEDED`).
     - Total confirmed seats for user in DB $\equiv$ 4.
4. **Idempotent Replay Concurrency Test**:
   - 50 concurrent threads submit the exact same idempotency key and same seat `C1`.
   - **Assertions:**
     - Exactly 1 reservation record created in DB.
     - All 50 threads receive valid response without server error.

### Verification Checklist
- Run `mvn test` $\rightarrow$ all concurrency and integration tests pass cleanly.

### Git Commands
```bash
git add .
git commit -m "test(concurrency): add testcontainers integration tests for hot seat and idempotency races"
```

---

# STEP 11: High-Throughput One-Command Burst Script (`burst.sh`)

### Objective
Deliver the single-command executable script that reproduces the on-sale stampede against the live URL and prints the outcome distribution and reconciliation report.

### Files to Create
- `scripts/burst.py` (Python `asyncio` + `aiohttp`)
- `burst.sh` (Bash wrapper)

### Technical Directives for Claude Sonnet 4
1. **`scripts/burst.py`**:
   - Accepts `--base-url` argument (e.g., `http://localhost:8080` or live cloud URL).
   - Generates test tokens via `POST /auth/token`.
   - Creates a show with 100 seats (`A1` to `A100`, price 25000 paise, limit 4).
   - **Phase 1: Hot Seat Storm:** 500 concurrent requests all target seat `A12`.
   - **Phase 2: User Limit Storm:** 1 user sends 10 concurrent requests for seats `B1`–`B10`.
   - **Phase 3: Idempotent Retries:** 50 concurrent retries with identical key on `C1`.
   - **Phase 4: Key Collision:** Same key with different seat `C2` (asserts 409).
   - **Phase 5: Reconciliation Query:** Calls `GET /shows/{id}` and verifies:
     `available + held + confirmed == total_seats`.
   - Prints formatted ASCII summary table with outcome distribution (`201`, `409`, `5xx`) and PASS/FAIL status.
2. **`burst.sh`**:
   ```bash
   #!/usr/bin/env bash
   set -e
   TARGET_URL=${1:-"http://localhost:8080"}
   echo "Running Burst Load Test against: $TARGET_URL"
   python3 -m pip install -q aiohttp
   python3 scripts/burst.py --base-url "$TARGET_URL"
   ```

### Verification Checklist
- Run `./burst.sh http://localhost:8080` against local running service $\rightarrow$ runs all phases and outputs green PASS summary.

### Git Commands
```bash
git add .
git commit -m "feat(burst): add one-command asynchronous burst test script and reconciliation report"
```

---

# STEP 12: Documentation, Production Deployment Config & `WRITEUP.md`

### Objective
Deploy the service to a public URL (Railway / Render / Fly.io) and author the mandatory writeup and documentation.

### Files to Create
- `README.md`
- `WRITEUP.md`
- Deployment descriptor (e.g. `railway.json` / `render.yaml` if applicable)

### Technical Directives for Claude Sonnet 4
1. **`README.md`**:
   - System overview and architecture diagram.
   - Quickstart commands: `docker compose up --build`.
   - Live URL, health endpoint, metrics endpoint.
   - One-command burst instructions: `./burst.sh <URL>`.
2. **`WRITEUP.md` Structure**:
   - **1. The Atomic Decision:** PostgreSQL row lock (`SELECT ... FOR UPDATE ORDER BY seat_number ASC`). Explain why it's race-free and how ordering mathematically prevents deadlocks.
   - **2. Per-User Limit Mechanism:** Explain `show_user_locks` serialization.
   - **3. Idempotency:** Detail dual-check mechanism, request SHA-256 hash comparison, and unique constraint fallback.
   - **4. Holds & Expiry Choice:** Detail explicit cancellation model with re-booking capability.
   - **5. Consistency vs. Availability under Partition:** Explicit CP choice. Seat reservation cannot tolerate split-brain overselling; service fails closed during database partitions.
   - **6. 2 AM Alerts:** What to page on (`5xx rate > 0`, `readiness == DOWN`, reconciliation drift, connection pool exhaustion).
   - **7. AI Usage Disclosure:** Detailed "Directed vs. Decided" breakdown.
   - **8. What You'd Do Next:** Redis caching / Lua scripts for hot seats, distributed tracing, read-replicas for `GET /shows/{id}`.

### Verification Checklist
- Run `./burst.sh <LIVE_DEPLOYED_URL>` against your public cloud deployment $\rightarrow$ 100% passes.

### Git Commands
```bash
git add .
git commit -m "docs: add comprehensive readme, writeup, and deployment configuration"
```
