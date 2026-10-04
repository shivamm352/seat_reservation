-- V3: Create show_user_locks table
-- Pessimistic advisory lock table for per-show-per-user mutual exclusion.
--
-- How it works:
--   1. At the start of a booking transaction, INSERT a row (show_id, user_id).
--   2. Concurrent requests from the same user for the same show will block
--      on the unique PK constraint until the first transaction commits/rolls back.
--   3. Service layer uses SELECT ... FOR UPDATE on this row to acquire the lock.
--
-- No created_at/expires_at: this is a DB-transaction-scoped lock, not a timeout hold.
-- Rows are cleaned up automatically when the wrapping transaction ends.

CREATE TABLE show_user_locks (
    show_id     UUID            NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    user_id     VARCHAR(100)    NOT NULL,
    PRIMARY KEY (show_id, user_id)
);
