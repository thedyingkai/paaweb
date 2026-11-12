-- BESTI ACM: member-maintained records and an authenticated team forum.
BEGIN;
CREATE TABLE IF NOT EXISTS besti_member_record (
 id bigserial PRIMARY KEY,
 user_id bigint NOT NULL REFERENCES users(user_id),
 kind varchar(20) NOT NULL CHECK (kind IN ('award','position','registration','organizing')),
 family varchar(40) NOT NULL DEFAULT '',
 title varchar(200) NOT NULL,
 organization varchar(200) NOT NULL DEFAULT '',
 result varchar(100) NOT NULL DEFAULT '',
 team_name varchar(200) NOT NULL DEFAULT '',
 start_date date,
 end_date date,
 source_url varchar(1000) NOT NULL DEFAULT '',
 notes varchar(4000) NOT NULL DEFAULT '',
 status varchar(20) NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','verified','rejected')),
 review_note varchar(1000) NOT NULL DEFAULT '',
 reviewed_by bigint REFERENCES users(user_id),
 created_by bigint NOT NULL REFERENCES users(user_id),
 version integer NOT NULL DEFAULT 1,
 created_at timestamp NOT NULL DEFAULT now(),
 updated_at timestamp NOT NULL DEFAULT now(),
 CHECK(end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS besti_record_owner ON besti_member_record(user_id,updated_at DESC);
CREATE INDEX IF NOT EXISTS besti_record_review ON besti_member_record(status,updated_at DESC);
CREATE TABLE IF NOT EXISTS besti_forum_topic (
 id bigserial PRIMARY KEY,
 author_id bigint NOT NULL REFERENCES users(user_id),
 category varchar(30) NOT NULL CHECK(category IN ('discussion','training','experience','announcement')),
 title varchar(200) NOT NULL,
 content varchar(20000) NOT NULL,
 pinned boolean NOT NULL DEFAULT false,
 locked boolean NOT NULL DEFAULT false,
 created_at timestamp NOT NULL DEFAULT now(),
 updated_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS besti_topic_order ON besti_forum_topic(pinned DESC,updated_at DESC);
CREATE TABLE IF NOT EXISTS besti_forum_reply (
 id bigserial PRIMARY KEY,
 topic_id bigint NOT NULL REFERENCES besti_forum_topic(id) ON DELETE CASCADE,
 author_id bigint NOT NULL REFERENCES users(user_id),
 content varchar(10000) NOT NULL,
 created_at timestamp NOT NULL DEFAULT now(),
 updated_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS besti_reply_topic ON besti_forum_reply(topic_id,id);
COMMIT;
