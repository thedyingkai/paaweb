package com.cwnupaa.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Approved member awards use the same configured grade scores as verified certificates. */
@Service
public class MemberAwardRatingService {
    private final JdbcTemplate db;
    public MemberAwardRatingService(JdbcTemplate db) { this.db = db; }
    public record Award(long userId, LocalDate date, int points, String key) {}
    public List<Award> approvedAwards() {
        return db.query("""
            SELECT r.user_id,r.title,COALESCE(r.start_date,CAST(r.created_at AS DATE)) AS award_date,
                   r.award_level,r.award_grade,l.base_rating
            FROM besti_member_record r
            JOIN users u ON u.user_id=r.user_id
            JOIN certificate_level l ON l.level_type=r.award_level AND l.rank_type=r.award_grade
            WHERE r.kind='award' AND r.status='verified'
              AND u.account_status='normal'
            AND """ + HistoricalAwardService.visibleRecordSql("r"), (rs,index) -> {
                long user=rs.getLong("user_id");LocalDate date=rs.getObject("award_date",LocalDate.class);
                return new Award(user,date,Math.max(0,rs.getInt("base_rating")),key(user,rs.getString("title"),date,rs.getString("award_level"),rs.getString("award_grade")));
            });
    }
    public static String key(long user,String event,LocalDate date,String level,String grade) {
        if(event==null || event.isBlank() || date==null || level==null || grade==null) return null;
        return user+"|"+event.replaceAll("\\s+","").toLowerCase(Locale.ROOT)+"|"+date+"|"+level+"|"+grade;
    }
}
