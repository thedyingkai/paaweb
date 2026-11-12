package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.OjPlatform;
import com.cwnupaa.backend.entity.OjPracticeRecord;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrackerHeatmapTests {
    private OjPracticeRecord solved(LocalDate day, String difficulty) {
        var record = new OjPracticeRecord();
        record.setSourceId(6L);
        record.setAcTime(day.atTime(12, 0));
        record.setProblemDifficulty(difficulty);
        return record;
    }

    private Map<String, Integer> days(List<List<Object>> heatmap) {
        var result = new HashMap<String, Integer>();
        heatmap.forEach(point -> result.put(point.get(0).toString(), ((Number) point.get(1)).intValue()));
        return result;
    }

    @Test void unratedActiveDaysRemainVisibleWhileRestDaysStayAbsentAndRatedMaximaWin() {
        var records = mock(OjPracticeRecordMapper.class);
        var platforms = mock(OjPlatformMapper.class);
        var platform = new OjPlatform(); platform.setOjId(6L); platform.setOjName("QOJ");
        when(platforms.selectList(null)).thenReturn(List.of(platform));
        var unrated = LocalDate.now().minusDays(1);
        var mixed = LocalDate.now().minusDays(2);
        var old = LocalDate.now().minusYears(6);
        when(records.selectEffectiveRecords(6L)).thenReturn(List.of(
                solved(unrated, "N/A"), solved(unrated, null), solved(unrated, ""),
                solved(mixed, "N/A"), solved(mixed, "1900"), solved(mixed, "1400"), solved(old, "N/A")));
        var service = new TrackerService(mock(OjUserAccountMapper.class), records, platforms,
                mock(RatingCompositionMapper.class), mock(RatingLogMapper.class),
                mock(OjRecentContestMapper.class), new RestTemplate());
        var dashboard = service.getDashboardStats(6L);
        assertThat(days(dashboard.getMaxDifficultyHeatmap())).containsExactlyInAnyOrderEntriesOf(
                Map.of(unrated.toString(), 0, mixed.toString(), 1900));
        assertThat(days(dashboard.getDailyHeatmap())).containsExactlyInAnyOrderEntriesOf(
                Map.of(unrated.toString(), 3, mixed.toString(), 3));
        assertThat(dashboard.getTotalSolved()).isEqualTo(7);
    }
}
