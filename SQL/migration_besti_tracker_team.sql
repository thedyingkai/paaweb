BEGIN;
ALTER TABLE oj_user_account ADD COLUMN IF NOT EXISTS qoj_id varchar(100);
ALTER TABLE oj_user_account ADD COLUMN IF NOT EXISTS ucup_id varchar(100);
ALTER TABLE oj_user_account ADD COLUMN IF NOT EXISTS qoj_cookie text;

INSERT INTO oj_platform (oj_id, oj_name, oj_url) VALUES
    (6, 'QOJ', 'https://qoj.ac/'), (7, 'UCup', 'https://contest.ucup.ac/')
ON CONFLICT (oj_id) DO NOTHING;
SELECT setval('oj_platform_oj_id_seq', (SELECT MAX(oj_id) FROM oj_platform));
CREATE INDEX IF NOT EXISTS idx_team_member_accepted ON team_member(member_id, team_id) WHERE status='1';

-- Only direct, accepted teammates share records. This is a live view, so leaving
-- a team cannot leave copied awards/records behind or spread records transitively.
CREATE OR REPLACE VIEW oj_tracker_peers AS
SELECT user_id AS user_id, user_id AS owner_id FROM users
UNION
SELECT mine.member_id, peer.member_id
FROM team_member mine
JOIN team t ON t.team_id=mine.team_id
JOIN team_member peer ON peer.team_id=mine.team_id
JOIN users viewer ON viewer.user_id=mine.member_id AND viewer.account_status='normal'
JOIN users contributor ON contributor.user_id=peer.member_id AND contributor.account_status='normal'
WHERE mine.status='1' AND peer.status='1';

CREATE OR REPLACE VIEW oj_effective_practice_record AS
SELECT practice_problem_id, user_id, source_id, problem_id, problem_name,
       problem_difficulty, problem_tag, ac_time
FROM (
    SELECT r.practice_problem_id, p.user_id,
           CASE WHEN r.source_id=7 THEN 6 ELSE r.source_id END AS source_id,
           r.problem_id, r.problem_name, r.problem_difficulty, r.problem_tag, r.ac_time,
           ROW_NUMBER() OVER (
               PARTITION BY p.user_id, CASE WHEN r.source_id=7 THEN 6 ELSE r.source_id END, r.problem_id
               ORDER BY r.ac_time, r.practice_problem_id
           ) AS occurrence
    FROM oj_tracker_peers p JOIN oj_practice_record r ON r.user_id=p.owner_id
) merged WHERE occurrence=1;
COMMIT;
