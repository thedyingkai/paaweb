-- Keep the competition's award separate from the school's recognized grade.
BEGIN;
ALTER TABLE besti_member_record ADD COLUMN IF NOT EXISTS contest_award varchar(20);
UPDATE besti_member_record
SET contest_award = CASE award_grade
 WHEN '一等奖' THEN '金牌' WHEN '二等奖' THEN '银牌' WHEN '三等奖' THEN '铜牌'
 WHEN '优胜奖' THEN '优胜奖' WHEN '优秀奖' THEN '优胜奖' END
WHERE kind='award' AND family IN ('ICPC','CCPC') AND contest_award IS NULL;
UPDATE besti_member_record SET award_grade='优秀奖', result=award_level || '优秀奖'
WHERE kind='award' AND family IN ('ICPC','CCPC') AND award_grade='优胜奖';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='besti_contest_award_values') THEN
  ALTER TABLE besti_member_record ADD CONSTRAINT besti_contest_award_values
   CHECK(contest_award IS NULL OR contest_award IN ('金牌','银牌','铜牌','优胜奖'));
 END IF;
END $$;
COMMIT;
