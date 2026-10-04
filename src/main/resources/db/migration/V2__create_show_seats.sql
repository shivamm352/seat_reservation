-- V2: Create show_seats table
-- Represents individual seats within a show.
-- Status machine: AVAILABLE → HELD (during booking flow) → CONFIRMED (booked)
--
-- NOTE: reservation_id is intentionally an unlinked UUID column (no FK to reservations).
-- Adding REFERENCES reservations(id) would create a circular dependency:
--   insert reservation requires confirmed seats,
--   confirm seats requires reservation ID.
-- Consistency is maintained by the service layer within a single DB transaction.

CREATE TABLE show_seats (
    id              UUID            PRIMARY KEY,
    show_id         UUID            NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number     VARCHAR(50)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE'
                                    CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),
    reservation_id  UUID,                          -- intentionally no FK; see note above
    CONSTRAINT uk_show_seat_number UNIQUE (show_id, seat_number)
);

-- Composite index backs both the UNIQUE constraint and availability lookup queries:
-- WHERE show_id = ? AND seat_number = ?
CREATE INDEX idx_show_seats_lookup ON show_seats(show_id, seat_number);
