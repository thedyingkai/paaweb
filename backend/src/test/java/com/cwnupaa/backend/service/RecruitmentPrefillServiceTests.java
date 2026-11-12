package com.cwnupaa.backend.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentPrefillServiceTests {
    @Test void prefillUsesOnlyApplicantsVerifiedRecordsAndMergedStatsWithoutCredentials() {
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(startsWith("SELECT r.title"),eq(6L))).thenReturn(List.of(Map.of("title","测试邀请赛","contest_award","银牌","is_starred",true,"start_date","2026-05-01")));
        when(db.queryForList(startsWith("SELECT newcoder_id"),eq(6L))).thenReturn(List.of(Map.of("qoj_id","testqoj","ucup_id","team-test","codeforces_id","testcf")));
        when(db.queryForList(startsWith("SELECT p.oj_name"),eq(6L))).thenReturn(List.of(Map.of("oj_name","QOJ","solved",78L),Map.of("oj_name","CodeForces","solved",100L)));
        var result=RecruitmentPrefillService.load(db,6L);
        assertThat(result.get("experience")).contains("测试邀请赛 · 打星（等效银牌）");
        assertThat(result.get("platformAccounts")).contains("QOJ / UCup：testqoj / team-test；累计通过 78 题","CodeForces：testcf；累计通过 100 题");
        assertThat(result.get("studentWork")).isEqualTo("无");
        verify(db).queryForList(argThat((String sql)->sql.contains("r.user_id=?") && sql.contains("r.status='verified'")),eq(6L));
        verify(db,never()).queryForList(contains("qoj_cookie"),any(Object[].class));
    }
}
