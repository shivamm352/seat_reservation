# Paytm Money Seat Reservation at Scale

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Multi--stage-2496ED.svg)](https://www.docker.com/)

A production-grade, highly concurrent seat reservation service built with Spring Boot 3.3, PostgreSQL 16, and Flyway. Designed to handle intense flash-sale on-sale stampedes with **zero overselling**, **zero deadlocks**, **dual-check idempotency**, and a **zero 5xx error guarantee** under contention.

---

## 🏛️ System Architecture

```
                                 [ Client Traffic ]
                                          │
                                    (HTTP / JSON)
                                          │
                        ┌─────────────────▼─────────────────┐
                        │        Tomcat Thread Pool         │
                        │    (max: 50, accept-count: 200)   │
                        └─────────────────┬─────────────────┘
                                          │
                              ┌───────────▼───────────┐
                              │     TraceIdFilter     │
                              │ (MDC: traceId, userId)│
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │ JwtAuthenticationFilt │
                              │  (Stateless Token)    │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │  ReservationController│
                              └───────────┬───────────┘
                                          │
                         [ @Transactional READ COMMITTED ]
                                          │
        ┌─────────────────────────────────┴─────────────────────────────────┐
        │                                                                   │
        ▼                                                                   ▼
 1. Advisory Lock                       2. Dual-Check Idempotency    3. Pessimistic Seats
┌───────────────────────┐              ┌───────────────────────────┐ ┌───────────────────────────┐
│ show_user_locks       │              │ reservations              │ │ show_seats                │
│ INSERT ON CONFLICT    │              │ SELECT by (show,user,key) │ │ SELECT ... FOR UPDATE     │
│ SELECT ... FOR UPDATE │              │ Hash == body? Return 200  │ │ ORDER BY seat_number ASC  │
│ (Serializes per-user) │              │ Hash != body? 409 Conflict│ │ (Eliminates deadlocks)    │
└───────────────────────┘              └───────────────────────────┘ └───────────────────────────┘
```

---

## 🚀 Quickstart (Local Docker)

### 1. Boot Environment with Docker Compose
```bash
docker compose up --build -d
```
* Starts **PostgreSQL 16 Alpine** container on `localhost:5432` with a health check.
* Flyway migrations run automatically (`V1` to `V5`).
* Starts the **Spring Boot application** on `http://localhost:8080`.

### 2. Verify Health Probes
```bash
# Liveness probe
curl -i http://localhost:8080/actuator/health/liveness

# Readiness probe (fails closed if database is disconnected)
curl -i http://localhost:8080/actuator/health/readiness
```

---

## ⚡ High-Throughput Burst Script (`burst.sh`)

Test the system under extreme load (500-thread hot-seat race, user limits, and idempotent retries) in a single command:

```bash
# Make executable and run against local or cloud endpoint
chmod +x burst.sh
./burst.sh http://localhost:8080
```

*(On Windows PowerShell, run: `.\burst.ps1 http://localhost:8080`)*

### Test Phases Executed:
1. **Hot Seat Storm:** 500 concurrent users compete for seat `A12` $\rightarrow$ Exactly 1 `201 Created`, 499 `409 Conflict`, 0 `5xx`.
2. **User Limit Storm:** 1 user sends 10 parallel requests with `per_user_limit = 4` $\rightarrow$ Exactly 4 `201 Created`, 6 `409 Conflict`.
3. **Idempotent Retries:** 50 concurrent requests with identical key $\rightarrow$ Exactly 1 DB booking, all 50 receive valid responses (`200`/`201`).
4. **Key Collision:** Same key used with different payload $\rightarrow$ `409 Conflict`.
5. **Reconciliation Query:** `available + held + confirmed == total_seats` checked for invariant integrity.

---

## 📡 API Reference

### 1. Evaluation Token Generator
* **`POST /auth/token?userId={id}&role={USER|ADMIN}`**
  * Generates a stateless HMAC-SHA256 JWT without user registration.

### 2. Show Management
* **`POST /shows`** *(Requires ADMIN token)*
  * Creates a show and bulk-inserts seats with status `AVAILABLE`.
* **`GET /shows/{id}`**
  * Read show metadata.
* **`GET /shows/{id}/seats`**
  * Real-time seat availability map.

### 3. Core Reservation Engine
* **`POST /shows/{id}/reserve`** *(Requires Bearer Token)*
  * Header: `Idempotency-Key: <key>`
  * Body: `{"showId": "...", "seatNumbers": ["A1", "A2"], "idempotencyKey": "..."}`
  * Returns `201 Created` for fresh reservation, `200 OK` for idempotent re-deliveries.

### 4. Cancellation Engine
* **`POST /reservations/{id}/cancel`** *(Requires Bearer Token)*
  * Verifies ownership, sets status `CANCELLED`, releases seats back to `AVAILABLE` for instant re-booking.

### 5. Observability
* **`GET /actuator/health`**: Real-time liveness and database readiness.
* **`GET /actuator/prometheus`**: Real-time Prometheus metrics (`reservations_confirmed_total`, `reservations_declined_total`, `reservation_duration_seconds`).
