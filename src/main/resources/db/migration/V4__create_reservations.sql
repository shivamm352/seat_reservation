-- V4: Create reservations table
-- Persists confirmed booking records. No PENDING state — a reservation row only exists
-- once the booking is fully committed (seats confirmed, payment recorded).
--
-- Idempotency design:
--   idempotency_key  → client-supplied token identifying a unique booking attempt
--   request_hash     → SHA-256 of the request payload, ensures same key = same payload
--   UNIQUE(show_id, user_id, idempotency_key) → prevents duplicate rows on API retries

CREATE TABLE reservations (
    id                  UUID            PRIMARY KEY,
    show_id             UUID            NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    user_id             VARCHAR(100)    NOT NULL,
    idempotency_key     VARCHAR(200)    NOT NULL,
    request_hash        VARCHAR(64)     NOT NULL,   -- SHA-256 hex of request body
    amount_paise        BIGINT          NOT NULL CHECK (amount_paise >= 0),
    status              VARCHAR(20)     NOT NULL DEFAULT 'CONFIRMED'
                                        CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    cancelled_at        TIMESTAMPTZ,               -- NULL unless status = 'CANCELLED'
    CONSTRAINT uk_reservation_idempotency UNIQUE (show_id, user_id, idempotency_key)
);

-- Backs per-user booking count queries used to enforce per_user_limit:
-- WHERE show_id = ? AND user_id = ? AND status = 'CONFIRMED'
CREATE INDEX idx_reservations_user_show ON reservations(show_id, user_id, status);
