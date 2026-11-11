-- Preserve historical records while adding explicit tenure and award fields.
BEGIN;
ALTER TABLE association_member ADD COLUMN IF NOT EXISTS start_date date;
ALTER TABLE association_member ADD COLUMN IF NOT EXISTS end_date date;
ALTER TABLE besti_member_record ADD COLUMN IF NOT EXISTS award_level varchar(20);
ALTER TABLE besti_member_record ADD COLUMN IF NOT EXISTS award_grade varchar(20);
ALTER TABLE besti_member_record ADD COLUMN IF NOT EXISTS contest_stage varchar(30);
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='besti_member_tenure_dates') THEN
  ALTER TABLE association_member ADD CONSTRAINT besti_member_tenure_dates
   CHECK(start_date IS NULL OR end_date IS NULL OR end_date >= start_date);
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='besti_award_level_values') THEN
  ALTER TABLE besti_member_record ADD CONSTRAINT besti_award_level_values
   CHECK(award_level IS NULL OR award_level IN ('省部级','国家级','校级'));
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='besti_award_grade_values') THEN
  ALTER TABLE besti_member_record ADD CONSTRAINT besti_award_grade_values
   CHECK(award_grade IS NULL OR award_grade IN ('一等奖','二等奖','三等奖','特等奖','优秀奖'));
 END IF;
END $$;
COMMIT;
