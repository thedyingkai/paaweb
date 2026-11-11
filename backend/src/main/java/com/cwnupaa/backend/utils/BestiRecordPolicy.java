package com.cwnupaa.backend.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Factual positions and awards; these values never grant account permissions. */
public final class BestiRecordPolicy {
    private BestiRecordPolicy() {}
    public static final Map<String, Set<String>> ROLES = Map.of(
        "科学之光技术联盟", Set.of("社团负责人", "主要负责人"),
        "计算机工程师联盟", Set.of("负责人"),
        "ACM 校队", Set.of("校队队长", "队员"));
    public static final Set<String> AWARD_LEVELS = Set.of("省部级", "国家级", "校级");
    public static final Set<String> AWARD_GRADES = Set.of("一等奖", "二等奖", "三等奖", "特等奖", "优秀奖");
    public static String recognizedLevel(String family, String stage) {
        if ("ICPC".equals(family) || "CCPC".equals(family)) {
            if ("regional".equals(stage) || "final".equals(stage)) return "国家级";
            if ("invitational".equals(stage)) return "省部级";
            throw new IllegalArgumentException("请选择区域赛、总决赛或邀请赛");
        }
        if ("蓝桥杯".equals(family)) {
            if ("national".equals(stage)) return "省部级";
            if ("provincial".equals(stage)) return "校级";
            throw new IllegalArgumentException("请选择蓝桥杯国赛或省赛");
        }
        return null;
    }
    private static final Pattern TERM = Pattern.compile("^(\\d{4})-(\\d{4})学年$");

    public static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    public static boolean validRole(String organization, String title) {
        return organization != null && title != null && ROLES.getOrDefault(organization, Set.of()).contains(title);
    }
    public static LocalDate[] termDates(String term) {
        var match = TERM.matcher(term == null ? "" : term);
        if (!match.matches()) return new LocalDate[]{null, null};
        int start = Integer.parseInt(match.group(1)), end = Integer.parseInt(match.group(2));
        if (start < 1900 || end != start + 1) return new LocalDate[]{null, null};
        return new LocalDate[]{LocalDate.of(start, 9, 1), LocalDate.of(end, 8, 31)};
    }
    public static String positionStatus(LocalDate start, LocalDate end, LocalDate today) {
        if (end != null && end.isBefore(today)) return "former";
        if (start != null && start.isAfter(today)) return "upcoming";
        return start == null || end == null ? "unknown" : "current";
    }
    public static String positionLabel(String organization, String title, String status) {
        String name = title == null ? "" : title;
        if ("former".equals(status) && !name.startsWith("前")) name = "前" + name;
        return organization == null || organization.isBlank() || "ACM 校队".equals(organization)
            ? name : "（" + organization + "）" + name;
    }
}
