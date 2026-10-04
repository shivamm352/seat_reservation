# Engineering Writeup: Seat Reservation at Scale

* **Live Service URL:** [`https://ticket-booking-service-production-27ac.up.railway.app`](https://ticket-booking-service-production-27ac.up.railway.app)
* **Health / Liveness:** [`https://ticket-booking-service-production-27ac.up.railway.app/actuator/health/liveness`](https://ticket-booking-service-production-27ac.up.railway.app/actuator/health/liveness)
* **Health / Readiness:** [`https://ticket-booking-service-production-27ac.up.railway.app/actuator/health/readiness`](https://ticket-booking-service-production-27ac.up.railway.app/actuator/health/readiness)
* **Prometheus Metrics:** [`https://ticket-booking-service-production-27ac.up.railway.app/actuator/prometheus`](https://ticket-booking-service-production-27ac.up.railway.app/actuator/prometheus)
* **Public GitHub Repository:** [`https://github.com/shivamm352/seat_reservation`](https://github.com/shivamm352/seat_reservation)

---

## 1. The Atomic Decision: Deterministic Row-Level Locking

### Concurrency Dilemma
At the onset of high-demand ticket sales, thousands of users compete for the exact same subset of seats (the "hot seat" problem). High-concurrency architectures commonly choose between **Optimistic Concurrency Control (OCC)** (e.g., `@Version` columns) and **Pessimistic Locking** (`SELECT ... FOR UPDATE`).

Under low to moderate contention, OCC excels because it minimizes lock overhead. However, under extreme contention (e.g., 500 threads fighting for a single seat `A12`), OCC collapses into an **Optimistic Retry Storm**:
1. 500 transactions concurrently read version $V_0$.
2. 1 transaction commits successfully, advancing to version $V_1$.
3. 499 transactions fail with optimistic lock exceptions and re-execute, hammering the database with dead-end retries, thrashing CPU, and filling HikariCP connection pools.

### Our Decision: Deterministic Pessimistic Row Locking
We chose pessimistic row-level locking via PostgreSQL:
```sql
SELECT s FROM ShowSeat s
WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers
ORDER BY s.seatNumber ASC
FOR UPDATE
```

### Mathematical Proof of Deadlock Elimination
Deadlocks occur if and only if the four **Coffman conditions** are met simultaneously: Mutual Exclusion, Hold and Wait, No Preemption, and **Circular Wait**.

Consider two concurrent transactions booking multiple overlapping seats:
* Transaction $T_1$ requests seats $\{A2, A1\}$.
* Transaction $T_2$ requests seats $\{A1, A2\}$.

If unconstrained:
1. $T_1$ locks $A2$ and waits for $A1$.
2. $T_2$ locks $A1$ and waits for $A2$.
3. Result: **Circular Wait $\rightarrow$ Deadlock**.

**Resolution through Strict Total Ordering:**
Our engine normalizes and sorts all seat requests lexicographically prior to issuing the query:
$$\text{Seats}(T_1) = [A1, A2], \quad \text{Seats}(T_2) = [A1, A2]$$
Because the SQL query enforces `ORDER BY s.seatNumber ASC`, both transactions attempt to acquire row locks in the exact same sequence ($A1$ followed by $A2$):
1. Whichever transaction acquires the exclusive row lock on $A1$ first proceeds.
2. The second transaction blocks on $A1$ *before* acquiring any lock on $A2$.
3. **Circular wait is mathematically impossible.** Once $T_1$ commits, $T_2$ awakens, inspects $A1$'s status (now `CONFIRMED`), and immediately aborts with `409 Conflict` (`SeatAlreadyTakenException`) without any deadlock.

---

## 2. Per-User Limit Mechanism: Advisory Row Serialization

A critical vulnerability in concurrent reservation systems is the **Phantom Booking Race Condition**:
* User has a limit of 4 seats.
* User currently has 0 seats.
* User fires 10 simultaneous requests for 1 seat each ($B1$ through $B10$).
* In an uncoordinated system, all 10 transactions execute:
  ```sql
  SELECT COUNT(*) FROM reservations ... -- all 10 read count = 0
  -- 0 + 1 <= 4 evaluates to true in all 10 threads!
  -- all 10 insert -> user books 10 seats, exceeding the limit!
  ```

### Our Solution: `show_user_locks` Table
Instead of coarse JVM-level synchronized locks (which fail across multiple container replicas) or distributed Redis locks (which add network hops and partial-failure modes), we implement transactional advisory row serialization:

1. **Idempotent Lock Anchor Creation:**
   ```sql
   INSERT INTO show_user_locks (show_id, user_id)
   VALUES (:showId, :userId)
   ON CONFLICT DO NOTHING;
   ```
2. **Pessimistic Row Lock:**
   ```sql
   SELECT l FROM ShowUserLock l
   WHERE l.showId = :showId AND l.userId = :userId
   FOR UPDATE;
   ```

Because this query locks the unique `(show_id, user_id)` row, all concurrent reservation requests from that user for that show are **strictly serialized at the database transaction level**:
* Request 1 locks the user row, reads `count = 0`, books 1 seat, commits.
* Request 2 acquires the lock, reads `count = 1`, books 1 seat, commits.
* ...
* Request 5 acquires the lock, reads `count = 4`, evaluates `4 + 1 > 4`, and throws `BookingLimitExceededException` (`409 Conflict`).
* When the transaction ends, the lock is released automatically by PostgreSQL.

---

## 3. Idempotency Architecture: Dual-Check Verification

Network drops and client retries during flash sales frequently cause duplicate HTTP requests. We implement a two-layer idempotency defense:

### Layer 1: Request Fingerprint Comparison (SHA-256)
When a request arrives with an `Idempotency-Key`:
1. The engine computes a deterministic hash of the request:
   $$\text{requestHash} = \text{SHA-256}(\text{showId} + ":" + \text{String.join}(",", \text{sortedSeats}))$$
2. It queries `reservations` by `(show_id, user_id, idempotency_key)` under the user lock:
   * **Matching Hash:** The client is re-requesting an already-completed booking. The engine returns the original reservation payload (`200 OK` or `201 Created`).
   * **Different Hash:** The client is reusing an idempotency key for a different set of seats. The engine rejects the request with `409 Conflict` (`IdempotencyConflictException`).

### Layer 2: Database Unique Constraint Fallback
```sql
CONSTRAINT uk_reservation_idempotency UNIQUE (show_id, user_id, idempotency_key)
```
In the event of an ultra-rare edge condition bypassing user locking, any secondary insert on the same key causes a `DataIntegrityViolationException`. Our `GlobalExceptionHandler` intercepts this and translates it directly into `409 Conflict` (`IDEMPOTENCY_RACE`), preserving the **Zero 5xx Guarantee**.

---

## 4. Holds & Expiry Choice: Explicit Cancellation with Re-Booking

### Architectural Evaluation
Many ticketing systems introduce a temporary `HELD` state with a 10-minute TTL. While intuitive, TTL holds introduce significant operational complexity under burst conditions:
* **Timer/Reaper Thrashing:** A background scheduled job polling `WHERE held_at < NOW() - INTERVAL '10 min'` suffers from database table scan contention during peak sales.
* **Zombie Reclaims:** A user completing checkout at 10m01s risks having their hold reaped mid-payment, requiring distributed distributed locks across hold timers and payment webhooks.

### Our Design: Clean Atomic Booking with Instant Cancellation
1. **Immediate Finality:** A reservation proceeds directly from `AVAILABLE` to `CONFIRMED` within an atomic transaction.
2. **Explicit Cancellation Engine (`POST /reservations/{id}/cancel`):**
   * Uses `SELECT ... FOR UPDATE` on the reservation.
   * Verifies identity: only the owner can cancel (`403 Forbidden` for non-owners).
   * Idempotent: cancelling an already-cancelled reservation safely returns `200 OK`.
   * Acquires pessimistic locks on the associated seats (`findSeatsByIdsForUpdate`), sets `status = AVAILABLE` and clears `reservation_id`.
   * Sets `reservation.status = CANCELLED`.
3. **Re-Booking Invariant:**
   Because `reservation_seats` has no `UNIQUE(seat_id)` constraint, released seats can be immediately booked by subsequent customers without constraint violations.

---

## 5. Consistency vs. Availability under Partition (CAP Theorem)

In Eric Brewer’s CAP Theorem, our seat reservation system is strictly **CP (Consistent and Partition-Tolerant)**:

$$\text{Overselling} = \text{Catastrophic Real-World Breach}$$

If a network partition isolates a replica or database node, the system **must fail closed**:
* **Why not AP?** An AP architecture using eventual consistency or multi-master replication allows two partitioned nodes to allocate seat `A12` simultaneously. In ticketing, two people showing up with identical boarding passes or theatre tickets creates legal, financial, and reputational liability that cannot be reconciled automatically.
* **Failure Semantics:** If the primary PostgreSQL instance is unreachable, Actuator's `/actuator/health/readiness` fails immediately (`503 Service Unavailable`, status `DOWN`). All incoming booking requests fail closed with structured `409`/`503` responses rather than accepting unverified reservations.

---

## 6. 2 AM Alerts: Production SRE Runbook

When on-call alerts fire at 2 AM, the following alerts require immediate escalation:

| Metric / Symptom | Threshold | Root Cause Hypothesis | Immediate Remediation |
|---|---|---|---|
| **`HTTP 5xx Rate`** | $> 0$ for 1 minute | Unhandled exception or bug bypassing `GlobalExceptionHandler` | Inspect `traceId` in centralized logs; rollback or deploy patch. |
| **`Readiness Probe DOWN`** | `readiness == DOWN` | Database unreachable or connection pool exhausted | Check Postgres health, disk space, and cloud network gateway. |
| **`Reconciliation Drift`** | `avail + held + conf != total` | Corrupt transaction or non-atomic SQL write | Quarantine show; run DB audit query to identify conflicting seat records. |
| **`Hikari Pool Saturation`** | `Active == 20` for $> 10\text{s}$ | Slow queries or long-running transactions holding connections | Check Postgres locks via `pg_stat_activity`; terminate blocking PID. |
| **`Deadlock Spike`** | `pg_stat_database.deadlocks > 0` | Query ordering regression | Verify all seat queries maintain `ORDER BY seat_number ASC`. |

---

## 7. AI Usage Disclosure: Directed vs. Decided

| Dimension | Directed (Human Architect) | Decided / Generated (AI) |
|---|---|---|
| **Tech Stack** | Spring Boot 3.3, Java 17, PostgreSQL 16, Docker | Specific library versions (JJWT 0.12.5, Testcontainers 1.19.7) |
| **Concurrency Strategy** | Requirement for pessimistic locking and race-free limits | Exact implementation of `show_user_locks` advisory two-step upsert protocol |
| **Deadlock Prevention** | Requirement for zero deadlocks | Enforcing lexicographical pre-sort in Java and `ORDER BY s.seatNumber ASC` in JPQL |
| **Idempotency** | Requirement for dual-check idempotency | Combination of `HashUtil.sha256` payload digest and DB unique constraint |
| **Zero 5xx Guarantee** | Requirement to avoid 500 errors on high contention | Mapping `PessimisticLockingFailureException` and `DataIntegrityViolationException` to `409` |
| **Validation** | 500-thread burst requirement and reconciliation invariant | Asynchronous Python `aiohttp` script and Testcontainers test suite |

---

## 8. What We’d Do Next (Scaling to 10,000+ Req/sec)

1. **Redis + Lua Hot Seat Pre-Filter:**
   * Place an in-memory Redis cluster in front of PostgreSQL.
   * Execute an atomic Lua script `redis.call('get', seat_key)` to immediately bounce 99.9% of requests in $< 1\text{ms}$ before they ever touch the relational database.
2. **Read-Replica Routing:**
   * Direct all `GET /shows/**` and `GET /shows/{id}/seats` queries to PostgreSQL read-replicas with `ApplicationConnection.isReadOnly()` routing, reserving the primary master exclusively for `POST /reserve` transactions.
3. **Distributed Tracing (OpenTelemetry):**
   * Export the `traceId` captured by `TraceIdFilter` to Jaeger / Datadog via OpenTelemetry OTLP exporters for full-trace distributed profiling across gateways, services, and database queries.
