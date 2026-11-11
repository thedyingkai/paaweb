BEGIN;
CREATE TABLE IF NOT EXISTS besti_honor_link (
 id bigserial PRIMARY KEY,
 honor_uid varchar(100) NOT NULL,
 member_index integer NOT NULL CHECK(member_index BETWEEN 1 AND 20),
 member_name varchar(100) NOT NULL DEFAULT '',
 source_hash varchar(64) NOT NULL DEFAULT '',
 source_snapshot text NOT NULL DEFAULT '{}',
 school_name varchar(100) NOT NULL DEFAULT '',
 student_number varchar(50) NOT NULL DEFAULT '',
 real_name varchar(100) NOT NULL DEFAULT '',
 user_id bigint REFERENCES users(user_id),
 status varchar(20) NOT NULL DEFAULT 'unassigned' CHECK(status IN ('unassigned','waiting','linked','revoked')),
 assigned_by bigint REFERENCES users(user_id),
 version integer NOT NULL DEFAULT 1,
 updated_at timestamp NOT NULL DEFAULT now(),
 UNIQUE(honor_uid,member_index)
);
CREATE INDEX IF NOT EXISTS besti_honor_link_identity ON besti_honor_link(school_name,student_number,real_name);
CREATE INDEX IF NOT EXISTS besti_honor_link_owner ON besti_honor_link(user_id);
CREATE TABLE IF NOT EXISTS besti_honor_claim (
 id bigserial PRIMARY KEY,
 link_id bigint NOT NULL REFERENCES besti_honor_link(id),
 claimant_id bigint NOT NULL REFERENCES users(user_id),
 claimant_school varchar(100) NOT NULL,
 claimant_number varchar(50) NOT NULL,
 claimant_name varchar(100) NOT NULL,
 source_hash varchar(64) NOT NULL,
 source_snapshot text NOT NULL,
 evidence_notes varchar(4000) NOT NULL,
 evidence_url varchar(1000) NOT NULL DEFAULT '',
 status varchar(20) NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected','withdrawn')),
 review_note varchar(1000) NOT NULL DEFAULT '',
 reviewed_by bigint REFERENCES users(user_id),
 version integer NOT NULL DEFAULT 1,
 created_at timestamp NOT NULL DEFAULT now(),
 updated_at timestamp NOT NULL DEFAULT now(),
 UNIQUE(link_id,claimant_id)
);
CREATE INDEX IF NOT EXISTS besti_honor_claim_review ON besti_honor_claim(status,updated_at);
CREATE TABLE IF NOT EXISTS besti_honor_audit (
 id bigserial PRIMARY KEY,
 link_id bigint NOT NULL REFERENCES besti_honor_link(id),
 claim_id bigint REFERENCES besti_honor_claim(id),
 actor_id bigint REFERENCES users(user_id),
 action varchar(40) NOT NULL,
 detail varchar(4000) NOT NULL,
 created_at timestamp NOT NULL DEFAULT now()
);
ALTER TABLE besti_member_record ADD COLUMN IF NOT EXISTS history_link_id bigint REFERENCES besti_honor_link(id);
ALTER TABLE besti_member_record ADD COLUMN IF NOT EXISTS is_starred boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS besti_record_history_unique ON besti_member_record(history_link_id);
COMMIT;
