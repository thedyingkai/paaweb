-- Official source snapshot: docs/besti/academic-catalog.json (2026-11-11).
-- Add missing registration options without changing existing IDs or user profiles.
BEGIN;
LOCK TABLE university, school, major IN SHARE ROW EXCLUSIVE MODE;
INSERT INTO university(university_name)
SELECT '北京电子科技学院'
WHERE NOT EXISTS (SELECT 1 FROM university WHERE university_name='北京电子科技学院');
CREATE TEMP TABLE besti_academic_catalog(school_name text,major_name text,schooling_years integer) ON COMMIT DROP;
INSERT INTO besti_academic_catalog VALUES
    ('密码科学与技术系','信息安全',4),
    ('密码科学与技术系','密码科学与技术',4),
    ('网络空间安全系','计算机科学与技术',4),
    ('网络空间安全系','网络空间安全',4),
    ('电子与通信工程系','电子信息工程',4),
    ('电子与通信工程系','通信工程',4),
    ('管理系','行政管理',4),
    ('管理系','保密管理',4),
    ('研究生部','网络空间安全（硕士）',3),
    ('研究生部','新一代电子信息技术（硕士）',3),
    ('研究生部','通信工程（硕士）',3),
    ('研究生部','计算机技术（硕士）',3),
    ('研究生部','密码（硕士）',3),
    ('研究生部','公共管理（MPA）',3),
    ('研究生部','信息与通信工程（联合培养硕士）',3),
    ('研究生部','计算机科学与技术（联合培养硕士）',3),
    ('研究生部','网络空间安全（博士）',4);
INSERT INTO school(university_id,school_name)
SELECT u.university_id,c.school_name
FROM (SELECT min(university_id) university_id FROM university WHERE university_name='北京电子科技学院') u
CROSS JOIN (SELECT DISTINCT school_name FROM besti_academic_catalog) c
WHERE NOT EXISTS (SELECT 1 FROM school s WHERE s.university_id=u.university_id AND s.school_name=c.school_name);
INSERT INTO major(school_id,major_name,schooling_years)
SELECT s.school_id,c.major_name,c.schooling_years
FROM besti_academic_catalog c JOIN school s ON s.school_name=c.school_name
WHERE s.university_id=(SELECT min(university_id) FROM university WHERE university_name='北京电子科技学院')
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id=s.school_id AND m.major_name=c.major_name);
COMMIT;
