package com.cwnupaa.backend.service;

import com.cwnupaa.backend.utils.BestiRecordPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Team eligibility comes only from the administrator-maintained roster, never self-reported records. */
@Service
public class TeamMembershipService {
    private final JdbcTemplate db;

    public TeamMembershipService(JdbcTemplate db) { this.db = db; }

    public boolean isCurrentMember(Long userId) {
        return isCurrentMember(userId, BestiRecordPolicy.today());
    }

    boolean isCurrentMember(Long userId, LocalDate today) {
        if (userId == null) return false;
        return db.query("""
            SELECT m.term,m.start_date,m.end_date
            FROM association_member m JOIN users u ON u.user_id=m.user_id
            WHERE m.user_id=? AND u.account_status='normal'
              AND m.department='ACM 校队' AND m.position IN ('校队队长','队员')
            """, (rs, index) -> {
                var term = BestiRecordPolicy.termDates(rs.getString("term"));
                LocalDate start = rs.getObject("start_date", LocalDate.class);
                LocalDate end = rs.getObject("end_date", LocalDate.class);
                if (start == null) start = term[0];
                if (end == null) end = term[1];
                return "current".equals(BestiRecordPolicy.positionStatus(start, end, today));
            }, userId).stream().anyMatch(Boolean.TRUE::equals);
    }
}
