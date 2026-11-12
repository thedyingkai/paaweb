package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.OjPracticeRecord;
import com.cwnupaa.backend.entity.OjUserAccount;
import com.cwnupaa.backend.mapper.OjPracticeRecordMapper;
import com.cwnupaa.backend.mapper.OjUserAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Stores only complete, validated external-OJ snapshots. School OJ uses its original path. */
@Service
public class TrackerSnapshotService {
    private final OjPracticeRecordMapper records;
    private final OjUserAccountMapper accounts;

    public TrackerSnapshotService(OjPracticeRecordMapper records, OjUserAccountMapper accounts) {
        this.records = records;
        this.accounts = accounts;
    }

    @Transactional
    public int reconcile(Long userId, Long sourceId, String platform, String handle, List<Map<String, Object>> snapshot) {
        OjUserAccount account = accounts.selectForTrackerUpdate(userId);
        String current = account == null ? null : switch (platform) {
            case "CodeForces" -> account.getCodeforcesId();
            case "AtCoder" -> account.getAtcoderId();
            case "NowCoder" -> account.getNewcoderId();
            case "Luogu" -> account.getLuoguId();
            case "QOJ" -> account.getQojId();
            case "UCup" -> account.getUcupId();
            default -> throw new IllegalArgumentException("不支持的平台");
        };
        if (current == null || !current.trim().equals(handle.trim())) {
            throw new TrackerSyncException("同步期间绑定账号发生变化，请重新同步");
        }
        Map<String, OjPracticeRecord> incoming = new LinkedHashMap<>();
        for (Map<String, Object> sub : snapshot) {
            Object pid = sub.get("problem_id");
            Object time = sub.get("solve_time");
            if (!(pid instanceof String id) || id.isBlank() || id.length() > 100
                    || !(time instanceof Number) || ((Number) time).longValue() <= 0) {
                throw new TrackerSyncException("平台记录缺少有效题目 ID 或首次通过时间");
            }
            OjPracticeRecord record = new OjPracticeRecord();
            record.setUserId(userId);
            record.setSourceId(sourceId);
            record.setProblemId(id);
            record.setProblemName(Objects.toString(sub.get("title"), id));
            record.setAcTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(((Number) time).longValue()), ZoneId.of("Asia/Shanghai")));
            record.setProblemDifficulty(sub.get("difficulty") instanceof Number n && n.doubleValue() > 0
                    ? Long.toString(Math.round(n.doubleValue())) : "N/A");
            record.setProblemTag(sub.get("tags") instanceof List<?> tags
                    ? String.join(",", tags.stream().map(String::valueOf).toList()) : "");
            incoming.merge(id, record, (a, b) -> a.getAcTime().isBefore(b.getAcTime()) ? a : b);
        }
        List<OjPracticeRecord> existing = records.selectList(new QueryWrapper<OjPracticeRecord>()
                .eq("user_id", userId).eq("source_id", sourceId));
        Map<String, OjPracticeRecord> previous = new HashMap<>();
        for (OjPracticeRecord record : existing) previous.put(record.getProblemId(), record);
        int added = 0;
        for (OjPracticeRecord record : incoming.values()) {
            OjPracticeRecord old = previous.get(record.getProblemId());
            if (old == null) {
                records.insert(record);
                added++;
            } else {
                record.setPracticeProblemId(old.getPracticeProblemId());
                // Missing optional metadata does not erase previously known difficulty/tags.
                if ("N/A".equals(record.getProblemDifficulty())) record.setProblemDifficulty(old.getProblemDifficulty());
                if (record.getProblemTag().isBlank()) record.setProblemTag(old.getProblemTag());
                records.updateById(record);
            }
        }
        // Reconcile only this user's selected external platform; removes stale bindings and
        // old title-based NowCoder IDs after the entire authoritative list has been checked.
        for (OjPracticeRecord old : existing) {
            if (!incoming.containsKey(old.getProblemId())) records.deleteById(old.getPracticeProblemId());
        }
        return added;
    }
}
