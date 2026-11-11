-- Keep IDs, ratings and custom weights of retained levels; reject changes to referenced obsolete levels.
BEGIN;
LOCK TABLE certificate_level IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM certificate_level l
        WHERE (l.level_type IN ('院系级', '院级') OR l.rank_type LIKE '其他%')
          AND (EXISTS (SELECT 1 FROM certificate_verify v WHERE v.level_id=l.level_id)
            OR EXISTS (SELECT 1 FROM certificate_type t WHERE t.type_id=l.level_id))
    ) THEN
        RAISE EXCEPTION 'Obsolete award levels are referenced; review them before changing the catalogue';
    END IF;
END $$;
DELETE FROM certificate_level WHERE level_type IN ('院系级', '院级') OR rank_type LIKE '其他%';
UPDATE certificate_level SET
    level_type=CASE level_type WHEN '省级' THEN '省部级' WHEN '校（市）级' THEN '校级' WHEN '校(市)级' THEN '校级' ELSE level_type END,
    rank_type=CASE WHEN rank_type LIKE '一等奖%' THEN '一等奖' WHEN rank_type LIKE '二等奖%' THEN '二等奖' WHEN rank_type LIKE '三等奖%' THEN '三等奖' ELSE rank_type END;
UPDATE certificate_level SET name=level_type || rank_type;
UPDATE certificate_type SET level_scope=CASE level_scope WHEN '省级' THEN '省部级' WHEN '校（市）级' THEN '校级' WHEN '校(市)级' THEN '校级' ELSE level_scope END;
UPDATE certificate_issue SET level_scope=CASE level_scope WHEN '省级' THEN '省部级' WHEN '校（市）级' THEN '校级' WHEN '校(市)级' THEN '校级' ELSE level_scope END;
DO $$
BEGIN
    IF (SELECT count(*) FROM certificate_level) <> 15
       OR (SELECT count(DISTINCT (level_type,rank_type)) FROM certificate_level
           WHERE level_type IN ('国家级','省部级','校级') AND rank_type IN ('特等奖','一等奖','二等奖','三等奖','优秀奖')) <> 15 THEN
        RAISE EXCEPTION 'Expected exactly three award levels and five grades';
    END IF;
END $$;
COMMIT;
