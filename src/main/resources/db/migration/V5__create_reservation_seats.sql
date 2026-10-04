-- V5: Create reservation_seats table
-- Junction table linking reservations to the specific seats they booked.
-- seat_number is denormalized here to avoid a join back to show_seats
-- when rendering booking receipts or confirmations.
--
-- IMPORTANT: There is intentionally NO UNIQUE(seat_id) constraint on this table.
-- Rationale: when a reservation is CANCELLED, its rows here are cascade-deleted,
-- and show_seats.status reverts to AVAILABLE — allowing the seat to be re-booked.
-- A global UNIQUE(seat_id) would permanently block re-booking after any cancellation.
-- Active-booking uniqueness is enforced by the show_seats.status state machine:
-- a seat can only be CONFIRMED by one reservation at a time.

CREATE TABLE reservation_seats (
    reservation_id  UUID            NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    seat_id         UUID            NOT NULL REFERENCES show_seats(id),
    seat_number     VARCHAR(50)     NOT NULL,   -- denormalized for receipt queries
    PRIMARY KEY (reservation_id, seat_id)
);
