package com.cwnupaa.backend.utils;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class BestiRecordPolicyTests {
    @Test void maintainerRecognitionRulesCoverIcpcCcpcAndLanqiao() {
        for (String family : new String[]{"ICPC", "CCPC"}) {
            assertEquals("国家级", BestiRecordPolicy.recognizedLevel(family,"regional"));
            assertEquals("国家级", BestiRecordPolicy.recognizedLevel(family,"final"));
            assertEquals("省部级", BestiRecordPolicy.recognizedLevel(family,"invitational"));
            assertThrows(IllegalArgumentException.class, () -> BestiRecordPolicy.recognizedLevel(family,null));
        }
        assertEquals("省部级", BestiRecordPolicy.recognizedLevel("蓝桥杯","national"));
        assertEquals("校级", BestiRecordPolicy.recognizedLevel("蓝桥杯","provincial"));
        assertThrows(IllegalArgumentException.class, () -> BestiRecordPolicy.recognizedLevel("蓝桥杯","regional"));
    }
    @Test void endingDayStillCurrentAndNextDayFormer() {
        var start = LocalDate.of(2025,9,1);
        var end = LocalDate.of(2026,8,31);
        assertEquals("current", BestiRecordPolicy.positionStatus(start,end,end));
        assertEquals("former", BestiRecordPolicy.positionStatus(start,end,end.plusDays(1)));
        assertEquals("upcoming", BestiRecordPolicy.positionStatus(start,end,start.minusDays(1)));
        assertEquals("current", BestiRecordPolicy.positionStatus(start,end,start));
    }
    @Test void allFiveFormerPositionsKeepTheirOrganizationAndHistory() {
        BestiRecordPolicy.ROLES.forEach((organization, roles) -> roles.forEach(role -> {
            assertTrue(BestiRecordPolicy.validRole(organization,role));
            String label = BestiRecordPolicy.positionLabel(organization,role,"former");
            assertTrue(label.contains("前"+role));
            if (!"ACM 校队".equals(organization)) assertTrue(label.contains(organization));
            assertEquals(label,BestiRecordPolicy.positionLabel(organization,"前"+role,"former"));
        }));
        assertFalse(BestiRecordPolicy.validRole("理事团","理事长"));
    }
    @Test void academicYearHasExplicitBoundariesAndDoesNotGuessInvalidTerms() {
        assertArrayEquals(new LocalDate[]{LocalDate.of(2025,9,1),LocalDate.of(2026,8,31)}, BestiRecordPolicy.termDates("2025-2026学年"));
        assertArrayEquals(new LocalDate[]{null,null}, BestiRecordPolicy.termDates("2025-2028学年"));
        assertArrayEquals(new LocalDate[]{null,null}, BestiRecordPolicy.termDates("未指定"));
        assertEquals("unknown",BestiRecordPolicy.positionStatus(null,null,LocalDate.of(2026,11,11)));
    }
}
