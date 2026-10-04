-- V1: Create shows table
-- Stores show metadata. price_paise uses integer minor units to avoid floating-point errors.
-- per_user_limit enforced here as DB-level fallback; primary enforcement is in service layer.

CREATE TABLE shows (
    id              UUID            PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    price_paise     BIGINT          NOT NULL CHECK (price_paise >= 0),
    per_user_limit  INT             NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
    total_seats     INT             NOT NULL CHECK (total_seats > 0),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
