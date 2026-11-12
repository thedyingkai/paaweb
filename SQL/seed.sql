-- Minimal first-install data. No production accounts, contacts, schools or business records.
BEGIN;

INSERT INTO oj_platform (oj_name, oj_url) VALUES
    ('CodeForces', 'https://codeforces.com/'),
    ('AtCoder', 'https://atcoder.jp/'),
    ('NowCoder', 'https://ac.nowcoder.com/'),
    ('Luogu', 'https://www.luogu.com.cn/');
INSERT INTO oj_platform (oj_id, oj_name, oj_url) VALUES
    (6, 'QOJ', 'https://qoj.ac/'), (7, 'UCup', 'https://contest.ucup.ac/');
SELECT setval('oj_platform_oj_id_seq', 7);

insert into certificate_level(name, level_type, rank_type, base_rating)
values
    ('国家级特等奖','国家级', '特等奖',1000),
    ('国家级一等奖','国家级', '一等奖',850),
    ('国家级二等奖','国家级', '二等奖',700),
    ('国家级三等奖','国家级', '三等奖',550),
    ('国家级优秀奖','国家级', '优秀奖',400),
    ('省部级特等奖','省部级', '特等奖',600),
    ('省部级一等奖','省部级', '一等奖',500),
    ('省部级二等奖','省部级', '二等奖',400),
    ('省部级三等奖','省部级', '三等奖',300),
    ('省部级优秀奖','省部级', '优秀奖',200),
    ('校级特等奖','校级', '特等奖',300),
    ('校级一等奖','校级', '一等奖',250),
    ('校级二等奖','校级', '二等奖',200),
    ('校级三等奖','校级', '三等奖',150),
    ('校级优秀奖','校级', '优秀奖',100);

-- Neutral organization placeholders required by the administrator's profile.
INSERT INTO university (university_name) VALUES ('默认学校');
INSERT INTO school (university_id, school_name) VALUES (1, '默认学院');
INSERT INTO major (school_id, major_name) VALUES (1, '默认专业');

INSERT INTO sys_intro (category, content) VALUES
    ('rules', ''), ('committee', ''), ('association', ''), ('honors', ''), ('showcase_bg', '[]');

-- Login: admin / admin123456. Change the password after the first sign-in.
-- The browser sends the lowercase MD5 of the password; the server hashes that value again.
INSERT INTO users (user_name, password, email, phone, introduce, real_name, number_id,
                   gender, role, account_status, major_id, grade_year, class_name,
                   is_enrolled_stu, user_account, edu_bg)
VALUES ('管理员', 'D7C6C07A0A04BA4E65921E2F90726384', '', '', '', '管理员', '',
        1, 'super_admin', 'normal', 1, '', '', 0, 'admin', 'undergraduate');

COMMIT;
