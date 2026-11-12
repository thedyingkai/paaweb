package com.cwnupaa.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Personal display preferences never change awards, their ownership or rating. */
@Service
public class HonorShowcaseService {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public HonorShowcaseService(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public record Preferences(boolean customized, List<String> selectedKeys) {}

    public Preferences read(Long userId) {
        var rows = db.queryForList("SELECT selected_keys FROM besti_honor_showcase WHERE user_id=?", String.class, userId);
        if (rows.isEmpty()) return new Preferences(false, List.of());
        try {
            return new Preferences(true, json.readValue(rows.getFirst(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            throw new IllegalStateException("荣誉展示设置读取失败", e);
        }
    }

    public Preferences save(Long userId, List<String> keys, Set<String> available) {
        if (keys == null || keys.size() > 1000) throw new IllegalArgumentException("请选择要展示的比赛，最多 1000 项");
        var unique = new HashSet<String>();
        for (String key : keys) {
            if (key == null || !key.matches("record-[1-9][0-9]*|certificate-[VG]-[1-9][0-9]*")
                    || !unique.add(key) || !available.contains(key)) {
                throw new IllegalArgumentException("部分荣誉已失效或不属于本人，请刷新后重新选择");
            }
        }
        final String encoded;
        try { encoded = json.writeValueAsString(keys); }
        catch (Exception e) { throw new IllegalStateException("荣誉展示设置保存失败", e); }
        db.update("INSERT INTO besti_honor_showcase(user_id,selected_keys) VALUES (?,?) "
                + "ON CONFLICT(user_id) DO UPDATE SET selected_keys=EXCLUDED.selected_keys,updated_at=now()", userId, encoded);
        return new Preferences(true, List.copyOf(keys));
    }

    public Preferences reset(Long userId) {
        db.update("DELETE FROM besti_honor_showcase WHERE user_id=?", userId);
        return new Preferences(false, List.of());
    }
}
