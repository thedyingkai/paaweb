package com.cwnupaa.backend.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Parses QOJ's authenticated submission table; UCup accounts use the same IDs. */
final class QojSubmissionParser {
    record Submission(long id, String problemId, String title, long time, boolean accepted) {}
    record Page(List<Submission> submissions, boolean hasNext) {}
    private static final Pattern PROBLEM = Pattern.compile("(?:/contest/\\d+)?/problem/(\\d+)/?");
    private static final Pattern TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

    static Page parse(String html, String handle, int page) {
        var doc = Jsoup.parse(html, "https://qoj.ac");
        check(doc.select("#form-login, input[type=password]").isEmpty(), "QOJ 登录已失效，请在同步窗口更新 QOJ Cookie");
        check(!doc.title().contains("Just a moment") && !html.contains("cf-chl-")
                && !html.contains("cf-turnstile"), "QOJ 要求浏览器验证，请正常登录后再同步");
        List<Submission> rows = new ArrayList<>();
        for (Element row : doc.select("table tr")) {
            var cells = row.children().select("td");
            if (cells.isEmpty()) continue;
            Element submission = cells.getFirst().select("a[href]").stream()
                    .filter(a -> path(a).matches("/submission/\\d+/?")).findFirst().orElse(null);
            if (submission == null) continue;
            check(cells.size() >= 9, "QOJ 提交表结构变化，原统计已保留");
            String owner = cells.get(2).select(".uoj-username").text().trim();
            if (owner.isEmpty()) {
                var link = cells.get(2).select("a[href]").stream()
                        .filter(a -> path(a).startsWith("/user/profile/")).findFirst().orElse(null);
                owner = link == null ? cells.get(2).text().trim() : path(link).substring("/user/profile/".length());
            }
            check(owner.equals(handle), "QOJ 返回的提交账号不匹配，原统计已保留");
            Element problem = cells.get(1).select("a[href]").stream()
                    .filter(a -> PROBLEM.matcher(path(a)).matches()).findFirst().orElse(null);
            check(problem != null, "QOJ 记录缺少全局题号，原统计已保留");
            var pid = PROBLEM.matcher(path(problem));
            check(pid.matches(), "QOJ 题号无法解析");
            var time = TIME.matcher(cells.get(8).text());
            check(time.find(), "QOJ 记录缺少通过时间，原统计已保留");
            long timestamp;
            try {
                timestamp = LocalDateTime.parse(time.group(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
            } catch (RuntimeException e) { throw new TrackerSyncException("QOJ 提交时间无法解析"); }
            long id = Long.parseLong(path(submission).replaceAll("\\D", ""));
            rows.add(new Submission(id, "QOJ" + pid.group(1), problem.text(), timestamp, accepted(cells.get(3))));
        }
        check(!rows.isEmpty() || doc.text().matches("(?is).*(?:no submissions?|no records?|暂无提交|无提交|无记录).*"),
                "无法确认 QOJ 提交列表，原统计已保留");
        var pagers = doc.select(".pagination");
        if (pagers.isEmpty()) {
            check(rows.size() < 10 && page == 1, "QOJ 分页信息缺失，原统计已保留");
            return new Page(rows, false);
        }
        Boolean more = null;
        for (Element pager : pagers) {
            var active = pager.select("li.active");
            check(active.size() == 1 && active.text().equals(Integer.toString(page)), "QOJ 当前页不匹配，原统计已保留");
            Set<Integer> pages = new HashSet<>();
            for (Element link : pager.select("li:not(.disabled) a[href]")) {
                URI uri = URI.create(link.absUrl("href"));
                check("https".equals(uri.getScheme()) && "qoj.ac".equals(uri.getHost())
                        && "/submissions".equals(uri.getPath()), "QOJ 分页链接异常");
                Map<String, String> query = new HashMap<>();
                for (String part : Objects.toString(uri.getRawQuery(), "").split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2) check(query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) == null,
                            "QOJ 分页参数异常");
                }
                check(handle.equals(query.get("submitter")), "QOJ 分页账号发生变化");
                String number = query.getOrDefault("page", "1");
                check(number.matches("[1-9]\\d{0,5}"), "QOJ 分页页码异常");
                pages.add(Integer.parseInt(number));
            }
            boolean next = pages.contains(page + 1);
            check(pages.stream().noneMatch(n -> n > page) || next, "QOJ 分页跳过了记录");
            check(more == null || more == next, "QOJ 分页信息不一致");
            more = next;
        }
        return new Page(rows, Boolean.TRUE.equals(more));
    }

    private static boolean accepted(Element cell) {
        if (cell.text().trim().matches("(?i)(?:AC|Accepted)\\s*[✓✔]?")) return true;
        Element score = cell.selectFirst(".uoj-score, [data-score]");
        if (score == null) return false;
        String full = score.hasAttr("data-full") ? score.attr("data-full")
                : score.hasAttr("data-max") ? score.attr("data-max") : "100";
        try {
            BigDecimal maximum = new BigDecimal(full);
            return maximum.signum() > 0 && new BigDecimal(score.hasAttr("data-score") ? score.attr("data-score") : score.text().trim()).compareTo(maximum) == 0;
        } catch (NumberFormatException e) { throw new TrackerSyncException("QOJ 提交分数无法解析"); }
    }

    private static String path(Element link) {
        try {
            URI uri = URI.create(link.absUrl("href"));
            return "qoj.ac".equals(uri.getHost()) ? uri.getPath() : "";
        } catch (RuntimeException e) { return ""; }
    }

    private static void check(boolean ok, String error) {
        if (!ok) throw new TrackerSyncException(error);
    }
}
