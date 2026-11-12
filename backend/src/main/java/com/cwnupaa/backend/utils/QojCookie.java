package com.cwnupaa.backend.utils;

import java.util.ArrayList;
import java.util.List;

/** Adapted from Whalica/OJ_Insight (MIT); see META-INF/THIRD_PARTY_NOTICES/OJ_Insight.txt. */
public final class QojCookie {
    private QojCookie() {}
    public static String normalize(String raw) {
        if (raw == null || raw.length() > 16000 || raw.chars().anyMatch(c -> c < 32 || c == 127))
            throw invalid();
        String cookie = raw.trim();
        if (cookie.regionMatches(true, 0, "Cookie:", 0, 7)) cookie = cookie.substring(7).trim();
        List<String> pairs = new ArrayList<>();
        for (String part : cookie.split(";")) {
            if (part.isBlank()) continue;
            int equal = part.indexOf('=');
            if (equal < 1) throw invalid();
            String name = part.substring(0, equal).trim(), value = part.substring(equal + 1).trim();
            if (!name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+")
                    || value.chars().anyMatch(c -> c < 33 || c >= 127)) throw invalid();
            pairs.add(name + "=" + value);
        }
        if (pairs.isEmpty()) throw invalid();
        return String.join("; ", pairs);
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("QOJ Cookie 格式不正确，请填写完整的 cookie_name=cookie_value，多项用分号分隔");
    }
}
