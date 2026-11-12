-- No row means default display; [] deliberately hides all personal honor cards.
-- These preferences are separate from award verification and rating tables.
CREATE TABLE IF NOT EXISTS besti_honor_showcase (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    selected_keys TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
