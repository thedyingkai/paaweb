-- Starred results retain their separate display/count, but earn the equivalent award's points.
-- Do not change ownership, verification, original result text, or unclassified participation.
UPDATE besti_member_record
SET award_level = CASE contest_stage WHEN 'invitational' THEN '省部级' ELSE '国家级' END,
    award_grade = CASE contest_award
        WHEN '金牌' THEN '一等奖'
        WHEN '银牌' THEN '二等奖'
        WHEN '铜牌' THEN '三等奖'
        WHEN '优胜奖' THEN '优秀奖'
    END,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE is_starred = true AND kind = 'award' AND family IN ('ICPC','CCPC')
  AND contest_stage IN ('regional','final','invitational')
  AND contest_award IN ('金牌','银牌','铜牌','优胜奖')
  AND (award_level IS DISTINCT FROM CASE contest_stage WHEN 'invitational' THEN '省部级' ELSE '国家级' END
    OR award_grade IS DISTINCT FROM CASE contest_award
        WHEN '金牌' THEN '一等奖' WHEN '银牌' THEN '二等奖'
        WHEN '铜牌' THEN '三等奖' WHEN '优胜奖' THEN '优秀奖' END);
