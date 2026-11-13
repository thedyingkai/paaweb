package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.mapper.EventMapper;
import com.cwnupaa.backend.vo.EventSignupVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** Confirmed entrants exist independently of website accounts. Never invent or activate accounts. */
@Service
public class EventRosterService {
    public static final String LOCKED_MESSAGE = "本场比赛按确定名单参赛，不开放自主报名；如需调整请联系赛事管理员";
    public record Entry(String universityName, String numberId, String realName, String groupLabel, String seatNumber, Integer isRanking) {}
    public record Row(long rosterId, long eventId, String universityName, String numberId, String realName,
                      String groupLabel, String seatNumber, int isRanking, Long userId) {}
    private final JdbcTemplate db;
    private final EventMapper events;
    private final TransactionTemplate transactions;

    public EventRosterService(JdbcTemplate db, EventMapper events, TransactionTemplate transactions) {
        this.db = db; this.events = events; this.transactions = transactions;
    }
    public static boolean isRoster(Event event) { return "roster".equals(event.getSignupMode()); }

    // Both identity and normal (approved) account state must match; ambiguous accounts never bind.
    private static final String MATCHED = """
        WITH matched AS (
          SELECT r.roster_id, MIN(u.user_id) AS user_id FROM event_roster r
          JOIN university un ON un.university_name = r.university_name
          JOIN school sc ON sc.university_id = un.university_id
          JOIN major m ON m.school_id = sc.school_id
          JOIN users u ON u.major_id = m.major_id AND u.number_id = r.number_id
            AND u.real_name = r.real_name AND u.account_status = 'normal'
          WHERE r.event_id = ? GROUP BY r.roster_id HAVING COUNT(*) = 1
        )
        """;

    public long count(long eventId) { return db.queryForObject("SELECT COUNT(*) FROM event_roster WHERE event_id = ?", Long.class, eventId); }

    public boolean eligible(long eventId, long userId) {
        return Boolean.TRUE.equals(db.queryForObject(MATCHED + "SELECT EXISTS(SELECT 1 FROM matched WHERE user_id = ?)", Boolean.class, eventId, userId));
    }

    public List<Row> rows(long eventId) {
        return db.query(MATCHED + """
            SELECT r.*, m.user_id FROM event_roster r LEFT JOIN matched m USING(roster_id)
            WHERE r.event_id = ? ORDER BY r.roster_id
            """, (rs, i) -> new Row(rs.getLong("roster_id"), rs.getLong("event_id"), rs.getString("university_name"),
                rs.getString("number_id"), rs.getString("real_name"), rs.getString("group_label"),
                rs.getString("seat_number"), rs.getInt("is_ranking"), rs.getObject("user_id", Long.class)), eventId, eventId);
    }

    public Page<EventSignupVO> publicPage(long eventId, int current, int size, String keyword, String group) {
        // Public search deliberately excludes student numbers, accounts and seating.
        String search = keyword == null ? "" : keyword.trim();
        List<Row> filtered = rows(eventId).stream().filter(r -> search.isEmpty() || r.realName().contains(search) || r.universityName().contains(search))
            .filter(r -> group == null || group.isBlank() || "all".equals(group)
                || "official".equals(group) && r.isRanking() == 1 || "star".equals(group) && r.isRanking() == 0
                || r.groupLabel().equals(group)).toList();
        Page<EventSignupVO> page = new Page<>(current, size, filtered.size());
        page.setRecords(filtered.stream().skip((long)(current - 1) * size).limit(size).map(r -> {
            EventSignupVO vo = new EventSignupVO();
            vo.setRealName(r.realName()); vo.setUniversityName(r.universityName()); vo.setSchoolName(r.universityName());
            vo.setOrganization(r.universityName()); vo.setTeamType(r.groupLabel()); vo.setIsRanking(r.isRanking());
            return vo;
        }).toList());
        return page;
    }

    static String clean(String value, int max, boolean required) {
        String text = value == null ? "" : value.trim();
        if (required && text.isEmpty() || text.length() > max || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("名单字段缺失、过长或包含控制字符");
        return text;
    }

    public List<Row> importRoster(long eventId, List<Entry> entries) {
        if (entries == null || entries.isEmpty() || entries.size() > 5000) throw new IllegalArgumentException("每次导入须为 1 至 5000 人");
        List<Entry> normalized = new ArrayList<>(); Set<List<String>> keys = new HashSet<>();
        for (Entry entry : entries) {
            if (entry == null) throw new IllegalArgumentException("名单存在空行");
            Entry row = new Entry(clean(entry.universityName(),100,true),clean(entry.numberId(),64,true),clean(entry.realName(),100,true),
                clean(entry.groupLabel(),100,false),clean(entry.seatNumber(),100,false),entry.isRanking() == null ? 1 : entry.isRanking());
            if (!List.of(0,1).contains(row.isRanking())) throw new IllegalArgumentException("排名标记只能为 0 或 1");
            if (!keys.add(List.of(row.universityName(),row.numberId()))) throw new IllegalArgumentException("同一批名单中存在重复学校和学号");
            normalized.add(row);
        }
        return transactions.execute(status -> {
            Event event = events.selectForSignupUpdate(eventId);
            if (event == null || !isRoster(event) || !Integer.valueOf(0).equals(event.getIsTeam())) throw new IllegalArgumentException("仅固定名单个人赛支持此导入");
            // Re-import is idempotent. A changed identity needs an explicit correction, never silently reassign it.
            for (Entry row : normalized) {
                List<String> names = db.queryForList("SELECT real_name FROM event_roster WHERE event_id=? AND university_name=? AND number_id=?", String.class, eventId,row.universityName(),row.numberId());
                if (!names.isEmpty() && !names.getFirst().equals(row.realName())) throw new IllegalArgumentException("已有名单的学号与本次姓名不符，请先核对");
                db.update("""
                    INSERT INTO event_roster(event_id,university_name,number_id,real_name,group_label,seat_number,is_ranking)
                    VALUES (?,?,?,?,?,?,?) ON CONFLICT(event_id,university_name,number_id) DO UPDATE
                    SET group_label=EXCLUDED.group_label, seat_number=EXCLUDED.seat_number,is_ranking=EXCLUDED.is_ranking
                    """,eventId,row.universityName(),row.numberId(),row.realName(),row.groupLabel(),row.seatNumber(),row.isRanking());
            }
            if (event.getMaxSignup() != null && count(eventId) > event.getMaxSignup()) throw new IllegalArgumentException("名单超过赛事人数上限，整批未导入");
            reconcileLocked(eventId);
            return rows(eventId);
        });
    }

    private void reconcileLocked(long eventId) {
        // Only verified identity matches gain a signup. No notification is sent by a roster import.
        db.update(MATCHED + """
            INSERT INTO signup_personal(event_id,user_id,status,signup_time,is_ranking,type,seat_number,signup_source)
            SELECT r.event_id,m.user_id,'approved',r.created_at,r.is_ranking,
              CASE WHEN r.is_ranking=0 THEN 'star' ELSE 'official' END,r.seat_number,'fixed_roster'
            FROM event_roster r JOIN matched m USING(roster_id) WHERE r.event_id=?
            ON CONFLICT(event_id,user_id) DO UPDATE SET is_ranking=EXCLUDED.is_ranking,
              type=EXCLUDED.type,seat_number=EXCLUDED.seat_number
            WHERE signup_personal.signup_source='fixed_roster'
            """,eventId,eventId);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void reconcileVerifiedAccounts() {
        for (Long id : db.queryForList("SELECT DISTINCT r.event_id FROM event_roster r JOIN event e USING(event_id) WHERE e.signup_mode='roster'",Long.class)) {
            transactions.executeWithoutResult(status -> {
                Event event = events.selectForSignupUpdate(id);
                if (event != null && isRoster(event)) reconcileLocked(id);
            });
        }
    }
}
