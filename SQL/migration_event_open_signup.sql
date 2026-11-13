BEGIN;
-- Fixed-roster events can collect confirmations without inventing a deadline or quota.
ALTER TABLE event ALTER COLUMN signup_end DROP NOT NULL;
ALTER TABLE event ALTER COLUMN max_signup DROP NOT NULL;
ALTER TABLE event ADD COLUMN IF NOT EXISTS signup_mode varchar(16) NOT NULL DEFAULT 'open'
    CHECK (signup_mode IN ('open', 'roster'));
CREATE TABLE IF NOT EXISTS event_roster (
    roster_id bigserial PRIMARY KEY,
    event_id bigint NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    university_name varchar(100) NOT NULL,
    number_id varchar(64) NOT NULL,
    real_name varchar(100) NOT NULL,
    group_label varchar(100) NOT NULL DEFAULT '',
    seat_number varchar(100) NOT NULL DEFAULT '',
    is_ranking integer NOT NULL DEFAULT 1 CHECK (is_ranking IN (0, 1)),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (event_id, university_name, number_id)
);
COMMIT;
