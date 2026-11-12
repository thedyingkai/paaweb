BEGIN;
ALTER TABLE besti_member_record DROP CONSTRAINT IF EXISTS besti_member_record_kind_check;
ALTER TABLE besti_member_record ADD CONSTRAINT besti_member_record_kind_check
    CHECK (kind IN ('award','position','registration','organizing'));
COMMIT;
