package com.cwnupaa.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CrawlerLocalRunner {

    @Autowired
    @Qualifier("trackerRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    @Qualifier("qojRestTemplate")
    private RestTemplate qojRestTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private double clipDifficulty(double difficulty) {
        if (difficulty >= 400) {
            return Math.round(difficulty);
        }
        return Math.round(400.0 / Math.exp(1.0 - difficulty / 400.0));
    }

    private double normalizeDifficulty(String platform, Object raw) {
        if (raw == null) return 0.0;
        double val;
        try {
            val = Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            val = 0.0;
        }

        if ("CodeForces".equalsIgnoreCase(platform) || "AtCoder".equalsIgnoreCase(platform) || "NowCoder".equalsIgnoreCase(platform)) {
            return Double.isNaN(val) ? 0.0 : val;
        } else if ("Luogu".equalsIgnoreCase(platform)) {
            Map<Integer, Double> luoguMap = new HashMap<>();
            luoguMap.put(0, 0.0);
            luoguMap.put(1, 800.0);
            luoguMap.put(2, 1100.0);
            luoguMap.put(3, 1500.0);
            luoguMap.put(4, 1800.0);
            luoguMap.put(5, 2200.0);
            luoguMap.put(6, 2600.0);
            luoguMap.put(7, 3200.0);
            return luoguMap.getOrDefault((int) val, 0.0);
        }
        return 0.0;
    }

    // Fetch every page before exposing a snapshot to the persistence layer.
    public List<Map<String, Object>> crawlCodeforces(String handle) {
        Map<String, Map<String, Object>> solved = new LinkedHashMap<>();
        Set<String> submissionIds = new HashSet<>();
        int from = 1;
        int pageSize = 10000;
        while (true) {
            Map<String, Object> data = readObject(getPage("https://codeforces.com/api/user.status?handle="
                    + encode(handle) + "&from=" + from + "&count=" + pageSize, null, 2100));
            require("OK".equals(data.get("status")), "CodeForces 接口返回失败");
            List<Map<String, Object>> page = list(data.get("result"), "CodeForces 提交记录");
            int added = 0;
            for (Map<String, Object> sub : page) {
                if (!submissionIds.add(requiredText(sub.get("id")))) continue;
                added++;
                if (!"OK".equals(sub.get("verdict"))) continue;
                Map<String, Object> problem = object(sub.get("problem"), "CodeForces 题目");
                String contest = requiredText(problem.get("contestId"));
                String index = requiredText(problem.get("index"));
                String pid = "CF" + contest + index;
                Map<String, Object> record = record("CodeForces", pid,
                        String.valueOf(problem.getOrDefault("name", pid)), timestamp(sub.get("creationTimeSeconds")));
                Number rating = (Number) problem.get("rating");
                record.put("difficulty", rating == null ? 0 : rating.doubleValue());
                record.put("tags", problem.getOrDefault("tags", List.of()));
                keepEarliest(solved, record);
            }
            if (page.size() < pageSize) break;
            require(added > 0, "CodeForces 分页未前进，未保存不完整数据");
            from += page.size();
        }
        return new ArrayList<>(solved.values());
    }

    // AtCoder Crawler
    private final Map<String, String> atcoderProblemsMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> atcoderModelsMap = new ConcurrentHashMap<>();
    private long atcoderLastUpdate = 0;
    private static final long CACHE_DURATION = 1000 * 3600 * 6; // 6 hours

    private synchronized void updateAtcoderMetadata() {
        if (System.currentTimeMillis() - atcoderLastUpdate < CACHE_DURATION && !atcoderProblemsMap.isEmpty()) {
            return;
        }
        try {
            log.info("Updating AtCoder metadata cache...");
            List<Map<String, Object>> problems = readList(getPage("https://kenkoooo.com/atcoder/resources/problems.json", null, 1100));
            for (Map<String, Object> p : problems) {
                String id = (String) p.get("id");
                String name = (String) p.get("name");
                if (id != null && name != null) atcoderProblemsMap.put(id, name);
            }
            Map<String, Map<String, Object>> models = objectMapper.readValue(
                    getPage("https://kenkoooo.com/atcoder/resources/problem-models.json", null, 1100),
                    new TypeReference<Map<String, Map<String, Object>>>() {});
            atcoderModelsMap.clear();
            atcoderModelsMap.putAll(models);

            atcoderLastUpdate = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Failed to update AtCoder metadata: {}", e.getClass().getSimpleName());
        }
    }

    public List<Map<String, Object>> crawlAtcoder(String handle) {
        Map<String, Map<String, Object>> solved = new LinkedHashMap<>();
        long cursor = 0;
        Set<String> seen = new HashSet<>();
        while (true) {
            List<Map<String, Object>> page = readList(getPage(
                    "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user="
                            + encode(handle) + "&from_second=" + cursor, null, 1100));
            if (page.isEmpty()) break;
            long maxTime = cursor;
            int added = 0;
            for (Map<String, Object> sub : page) {
                long time = timestamp(sub.get("epoch_second"));
                require(time >= cursor, "AtCoder 分页时间异常");
                maxTime = Math.max(maxTime, time);
                if (!seen.add(requiredText(sub.get("id")))) continue;
                added++;
                if (!"AC".equals(sub.get("result"))) continue;
                String pid = requiredText(sub.get("problem_id"));
                keepEarliest(solved, record("AtCoder", pid, pid, time));
            }
            if (page.size() < 500) break;
            // Re-read the boundary second so submissions sharing it cannot be skipped.
            // A saturated second that cannot advance is an error, never a partial success.
            require(maxTime > cursor && added > 0, "AtCoder 分页未前进，未保存不完整数据");
            cursor = maxTime;
        }
        Map<String, Object> rank = readObject(getPage(
                "https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=" + encode(handle), null, 1100));
        require(rank.get("count") instanceof Number count && count.longValue() == solved.size(),
                "AtCoder 通过题数与统计接口不一致，保留原数据");
        updateAtcoderMetadata();
        for (Map<String, Object> record : solved.values()) {
            String pid = (String) record.get("problem_id");
            record.put("title", atcoderProblemsMap.getOrDefault(pid, pid));
            Map<String, Object> model = atcoderModelsMap.get(pid);
            if (model != null && model.get("difficulty") instanceof Number difficulty) {
                record.put("difficulty", clipDifficulty(difficulty.doubleValue()));
            }
        }
        return new ArrayList<>(solved.values());
    }

    // CWNU OJ Crawler
    private final Map<String, Double> cwnuDifficultyCache = new ConcurrentHashMap<>();

    private double getCwnuProblemDifficulty(String pid) {
        if (cwnuDifficultyCache.containsKey(pid)) {
            return cwnuDifficultyCache.get(pid);
        }
        try {
            Thread.sleep(100);
            String url = com.cwnupaa.backend.utils.DeploymentUrls.hydro() + "/p/" + pid;
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10000)
                    .get();

            org.jsoup.select.Elements tags = doc.getElementsByClass("problem__tag-item");
            Map<Integer, Double> hydroMap = new HashMap<>();
            hydroMap.put(0, 0.0);
            hydroMap.put(1, 300.0);
            hydroMap.put(2, 400.0);
            hydroMap.put(3, 500.0);
            hydroMap.put(4, 700.0);
            hydroMap.put(5, 800.0);
            hydroMap.put(6, 1000.0);
            hydroMap.put(7, 1200.0);
            hydroMap.put(8, 1400.0);
            hydroMap.put(9, 1600.0);
            hydroMap.put(10, 1800.0);

            for (org.jsoup.nodes.Element tag : tags) {
                String text = tag.text().trim();
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:难度|Difficulty)\\s*[:：]\\s*(\\d+)");
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    int rawDiff = Integer.parseInt(m.group(1));
                    double score = hydroMap.getOrDefault(rawDiff, 0.0);
                    cwnuDifficultyCache.put(pid, score);
                    return score;
                }
            }
            cwnuDifficultyCache.put(pid, 0.0);
            return 0.0;
        } catch (Exception e) {
            log.error("[CWNU] Failed to get difficulty for {}: {}", pid, e.getClass().getSimpleName());
            return 0.0;
        }
    }

    public List<Map<String, Object>> crawlCwnu(String userId) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (com.cwnupaa.backend.utils.DeploymentUrls.hydro().isEmpty()) return results;
        String url = com.cwnupaa.backend.utils.DeploymentUrls.hydro() + "/api/submissions/" + userId;
        try {
            String resp = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            if (data == null || !data.containsKey("data") || !(data.get("data") instanceof List)) {
                return results;
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) data.get("data");
            List<Map<String, Object>> acSubs = new ArrayList<>();
            for (Map<String, Object> sub : dataList) {
                Number statusObj = (Number) sub.get("status");
                if (statusObj != null && statusObj.intValue() == 1) {
                    acSubs.add(sub);
                }
            }

            List<Object[]> parsedSubs = new ArrayList<>();
            for (Map<String, Object> sub : acSubs) {
                String judgeAtStr = (String) sub.get("judgeAt");
                if (judgeAtStr == null) continue;
                try {
                    java.time.Instant instant = java.time.Instant.parse(judgeAtStr);
                    long ts = instant.getEpochSecond();
                    parsedSubs.add(new Object[]{sub, ts});
                } catch (Exception e) {
                    // ignore
                }
            }
            parsedSubs.sort(Comparator.comparingLong(x -> (long) x[1]));

            Set<String> seenPids = new HashSet<>();
            for (Object[] pair : parsedSubs) {
                Map<String, Object> sub = (Map<String, Object>) pair[0];
                long ts = (long) pair[1];
                String displayPid = (String) sub.get("displayPid");
                if (displayPid == null) continue;
                if (seenPids.contains(displayPid)) continue;
                seenPids.add(displayPid);

                double difficulty = getCwnuProblemDifficulty(displayPid);
                String rawDiffStr = difficulty > 0 ? String.valueOf((int) difficulty) : "N/A";

                Map<String, Object> record = new HashMap<>();
                record.put("platform", "CWNU");
                record.put("remote_id", String.valueOf(sub.get("rid")));
                record.put("problem_id", displayPid);
                record.put("title", sub.getOrDefault("title", ""));
                record.put("link", com.cwnupaa.backend.utils.DeploymentUrls.hydro() + "/p/" + displayPid);
                record.put("solve_time", ts);
                record.put("difficulty", difficulty);
                record.put("raw_difficulty", rawDiffStr);
                record.put("tags", new ArrayList<>());
                results.add(record);
            }
        } catch (Exception e) {
            log.error("[CWNU] Error crawling {}: {}", userId, e.getClass().getSimpleName());
        }
        return results;
    }

    // Luogu OJ Crawler
    private String getLuoguUid(String keyword, Map<String, String> headers) {
        if (keyword.matches("\\d+")) return keyword;
        Map<String, Object> data = readObject(getPage("https://www.luogu.com.cn/api/user/search?keyword="
                + encode(keyword), headers, 2100));
        List<Map<String, Object>> users = list(data.get("users"), "洛谷用户搜索");
        // Search results can be fuzzy: never silently choose the first different account.
        List<Map<String, Object>> exact = users.stream()
                .filter(user -> keyword.equals(user.get("name"))).toList();
        require(exact.size() == 1, "未找到唯一匹配的洛谷用户，请绑定数字 UID");
        String uid = requiredText(exact.get(0).get("uid"));
        require(uid.matches("\\d+"), "洛谷返回了无效的用户 UID");
        return uid;
    }

    public List<Map<String, Object>> crawlLuogu(String handle, long lastTime, String clientId) {
        require(clientId != null && !clientId.isBlank(), "洛谷同步需要有效 Cookie");
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Referer", "https://www.luogu.com.cn/");
        headers.put("x-requested-with", "XMLHttpRequest");
        String uid = getLuoguUid(handle, headers);
        headers.put("Cookie", buildLuoguCookie(clientId, uid));
        Map<String, Map<String, Object>> solved = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        Long total = null;
        for (int page = 1; ; page++) {
            Map<String, Object> payload = readLuoguPage(getPage(
                    "https://www.luogu.com.cn/record/list?user=" + uid + "&status=12&page=" + page,
                    headers, 2100));
            Map<String, Object> records = object(payload.get("records"), "洛谷评测记录");
            List<Map<String, Object>> rows = list(records.get("result"), "洛谷评测记录列表");
            if (records.get("count") instanceof Number count) {
                if (total == null) total = count.longValue();
                require(total == count.longValue(), "洛谷记录总数在同步中变化，请重试");
            }
            int added = 0;
            for (Map<String, Object> row : rows) {
                require(row.get("status") instanceof Number status && status.intValue() == 12,
                        "洛谷返回了非通过记录，已中止同步");
                if (row.get("user") instanceof Map<?, ?> user) {
                    require(uid.equals(String.valueOf(user.get("uid"))), "洛谷返回了其他用户的记录");
                }
                if (!seen.add(requiredText(row.get("id")))) continue;
                added++;
                Map<String, Object> problem = object(row.get("problem"), "洛谷题目");
                String pid = requiredText(problem.get("pid"));
                Map<String, Object> record = record("Luogu", pid,
                        Objects.toString(problem.get("name"), Objects.toString(problem.get("title"), pid)),
                        timestamp(row.get("submitTime")));
                record.put("difficulty", normalizeDifficulty("Luogu", problem.get("difficulty")));
                keepEarliest(solved, record);
            }
            if (total != null && seen.size() == total) break;
            require(total == null || seen.size() < total, "洛谷记录总数不匹配");
            if (rows.isEmpty()) {
                require(total == null || seen.size() == total, "洛谷历史记录未抓取完整");
                break;
            }
            require(added > 0, "洛谷分页重复，未保存不完整数据");
        }
        Map<String, Object> practice = readLuoguPage(getPage(
                "https://www.luogu.com.cn/user/" + uid + "/practice", headers, 2100));
        Map<String, Object> user = object(practice.get("user"), "洛谷用户资料");
        require(uid.equals(String.valueOf(user.get("uid"))), "洛谷返回了其他用户的资料");
        Set<String> passed = new HashSet<>();
        for (Map<String, Object> problem : list(practice.get("passed"), "洛谷通过题目清单")) {
            passed.add(requiredText(problem.get("pid")));
        }
        require(user.get("passedProblemCount") instanceof Number count && count.longValue() == passed.size(),
                "洛谷通过题目清单与通过题数不一致，保留原数据");
        // The record list also includes accepted problems that Luogu omits from its practice statistics.
        passed.removeAll(solved.keySet());
        require(passed.isEmpty(), "洛谷可见记录缺少刷题页的 " + passed.size() + " 道通过题，请检查记录可见性，保留原数据");
        return new ArrayList<>(solved.values());
    }

    private Map<String, Object> readLuoguPage(String body) {
        if (body.stripLeading().startsWith("{")) {
            Map<String, Object> json = readObject(body);
            Object data = json.containsKey("currentData") ? json.get("currentData") : json.get("data");
            return object(data, "洛谷页面数据");
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(body);
        org.jsoup.nodes.Element context = doc.getElementById("lentille-context");
        if (context != null) {
            String json = context.data().isBlank() ? context.html() : context.data();
            if (json.stripLeading().startsWith("%")) json = decode(json);
            return object(readObject(json).get("data"), "洛谷页面数据");
        }
        java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("decodeURIComponent\\(\"([^\"]+)\"\\)").matcher(body);
        require(match.find(), "无法解析洛谷记录，Cookie 可能已失效或请求被拦截");
        return object(readObject(decode(match.group(1))).get("currentData"), "洛谷页面数据");
    }

    private String buildLuoguCookie(String rawCookie, String uid) {
        String cookie = rawCookie.trim();
        if (!cookie.contains("=")) {
            cookie = "__client_id=" + cookie;
        }

        if (!cookie.endsWith(";")) cookie += ";";
        // Legacy input was only __client_id from the bound account. A full Cookie
        // already identifies its login account, which may differ from the target.
        boolean hasUid = Arrays.stream(cookie.split(";")).map(String::trim)
                .anyMatch(part -> part.startsWith("_uid="));
        if (!hasUid) cookie += "_uid=" + uid + ";";
        return cookie;
    }

    // NowCoder Crawler
    private final Map<String, Double> nowcoderDifficultyCache = new ConcurrentHashMap<>();

    private double getNowcoderProblemDifficulty(String numericId) {
        if (nowcoderDifficultyCache.containsKey(numericId)) {
            return nowcoderDifficultyCache.get(numericId);
        }
        try {
            String apiStr = "https://ac.nowcoder.com/acm/problem/list/json?keyword=" + numericId + "&asc=true";
            Map<String, Object> data = readObject(getPage(apiStr, null, 1100));
            if (data != null && data.get("code") != null && ((Number) data.get("code")).intValue() == 0) {
                Map<String, Object> innerData = (Map<String, Object>) data.get("data");
                if (innerData != null && innerData.containsKey("problemSets")) {
                    List<Map<String, Object>> problemSets = (List<Map<String, Object>>) innerData.get("problemSets");
                    for (Map<String, Object> p : problemSets) {
                        if (String.valueOf(p.get("problemId")).equals(numericId)) {
                            Number rawDiffObj = (Number) p.get("difficulty");
                            double rawDiff = rawDiffObj != null ? rawDiffObj.doubleValue() : 0.0;
                            double finalDiff = rawDiff;
                            if (rawDiff < 9.0) {
                                finalDiff = normalizeDifficulty("Luogu", rawDiff);
                            }
                            nowcoderDifficultyCache.put(numericId, finalDiff);
                            return finalDiff;
                        }
                    }
                }
            }
            nowcoderDifficultyCache.put(numericId, 0.0);
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public List<Map<String, Object>> crawlNowcoder(String userId, long lastTime) {
        require(userId != null && userId.matches("\\d+"), "牛客账号必须为数字 UID");
        Map<String, Map<String, Object>> solved = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        Integer expected = null;
        for (int page = 1; ; page++) {
            String url = "https://ac.nowcoder.com/acm/contest/profile/" + userId
                    + "/practice-coding?pageSize=200&statusTypeFilter=5&orderType=DESC&page=" + page;
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(getPage(url, null, 1100));
            org.jsoup.nodes.Element passed = doc.select(".my-state-item").stream()
                    .filter(el -> el.text().contains("题已通过")).findFirst().orElse(null);
            require(passed != null, "无法读取牛客通过题数，账号无效或请求被拦截");
            int count;
            try {
                count = Integer.parseInt(passed.select(".state-num").text().replace(",", "").trim());
            } catch (NumberFormatException e) {
                throw new TrackerSyncException("无法解析牛客通过题数");
            }
            if (expected == null) expected = count;
            require(count == expected, "牛客通过题数在同步中变化，请重试");
            org.jsoup.select.Elements rows = doc.select("table.table-hover tbody tr");
            int added = 0;
            for (org.jsoup.nodes.Element row : rows) {
                org.jsoup.select.Elements tds = row.select("td");
                if (tds.size() == 1 && tds.text().matches(".*(?:暂无|没有|无记录).*")) continue;
                require(tds.size() >= 9, "牛客提交表结构变化，未保存不完整数据");
                String status = tds.get(2).text().trim();
                require(status.equals("答案正确") || status.equalsIgnoreCase("Accepted") || status.equals("AC"),
                        "牛客返回了非通过记录，已中止同步");
                org.jsoup.nodes.Element link = tds.get(1).selectFirst("a[href]");
                require(link != null, "牛客题目缺少链接");
                java.util.regex.Matcher problemId = java.util.regex.Pattern
                        .compile("/acm/problem/(\\d+)(?:[/?#]|$)").matcher(link.attr("href"));
                require(problemId.find(), "牛客题目缺少全局题目 ID，未按题名估算数量");
                String pid = "NC" + problemId.group(1);
                String remoteId = requiredText(tds.get(0).text().trim());
                long time;
                try {
                    time = java.time.LocalDateTime.parse(tds.get(8).text().trim(),
                                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
                } catch (RuntimeException e) {
                    throw new TrackerSyncException("牛客提交时间无法解析");
                }
                if (!seen.add(remoteId)) continue;
                added++;
                keepEarliest(solved, record("NowCoder", pid, link.text().trim(), time));
            }
            boolean hasNext = doc.select(".pagination a[href]").stream().anyMatch(link -> {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]page=(\\d+)(?:&|$)")
                        .matcher(link.attr("href"));
                return m.find() && Integer.parseInt(m.group(1)) > pageNumber(url);
            });
            if (added == 0) {
                require(!hasNext, "牛客分页重复，未保存不完整数据");
                break;
            }
            require(solved.size() <= expected, "牛客通过题目数与主页不一致");
            if (!hasNext) break;
        }
        require(expected != null && solved.size() == expected, "牛客历史通过题目未抓取完整，保留原数据");
        // Difficulty is optional metadata and must never affect the solved count.
        for (Map<String, Object> record : solved.values()) {
            record.put("difficulty", getNowcoderProblemDifficulty(((String) record.get("problem_id")).substring(2)));
        }
        return new ArrayList<>(solved.values());
    }

    private static int pageNumber(String url) {
        return Integer.parseInt(url.substring(url.lastIndexOf("&page=") + 6));
    }

    private final Map<String, Long> lastRequest = new HashMap<>();
    // Set to zero only in deterministic HTTP fixture tests.
    private boolean paceRequests = true;

    private String getPage(String url, Map<String, String> extraHeaders, long interval) {
        String host = java.net.URI.create(url).getHost();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        if (extraHeaders != null) extraHeaders.forEach(headers::set);
        for (int attempt = 1; ; attempt++) {
            waitForRequestSlot(host, interval);
            try {
                RestTemplate client = "qoj.ac".equals(host) && qojRestTemplate != null ? qojRestTemplate : restTemplate;
                var response = client.exchange(java.net.URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), String.class);
                require(!response.getStatusCode().is3xxRedirection(), "QOJ 登录已失效，请在同步窗口更新 QOJ Cookie");
                String body = response.getBody();
                require(body != null && !body.isBlank(), "平台返回空响应");
                return body;
            } catch (RestClientException e) {
                // Record diagnostic categories only: exception text can contain URLs, credentials or response HTML.
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                Integer status = e instanceof HttpStatusCodeException http ? http.getStatusCode().value() : null;
                log.warn("OJ request failed host={} status={} cause={} attempt={}", host, status, root.getClass().getSimpleName(), attempt);
                boolean transientFailure = status != null ? status >= 500 && status <= 599
                        : e instanceof org.springframework.web.client.ResourceAccessException
                        && !(root instanceof javax.net.ssl.SSLException)
                        && !(root instanceof java.net.UnknownHostException);
                if (attempt < 2 && transientFailure) continue;
                throw new TrackerSyncException(requestFailureMessage(host, status, root));
            }
        }
    }

    private void waitForRequestSlot(String host, long interval) {
        synchronized (lastRequest) {
            long wait = lastRequest.getOrDefault(host, 0L) + interval - System.currentTimeMillis();
            if (paceRequests && wait > 0) {
                try { Thread.sleep(wait); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TrackerSyncException("同步已中断");
                }
            }
            lastRequest.put(host, System.currentTimeMillis());
        }
    }

    private String requestFailureMessage(String host, Integer status, Throwable cause) {
        String platform = switch (host) {
            case "codeforces.com" -> "Codeforces";
            case "kenkoooo.com" -> "AtCoder";
            case "ac.nowcoder.com" -> "牛客";
            case "www.luogu.com.cn" -> "洛谷";
            case "qoj.ac" -> "QOJ / UCup";
            default -> "OJ";
        };
        if (status != null) {
            if ("www.luogu.com.cn".equals(host) && status == 401)
                return "洛谷未接受登录凭据（HTTP 401），请登录洛谷后重新填写同一会话的 __client_id 和 _uid";
            return switch (status) {
                case 401 -> platform + "接口拒绝访问（HTTP 401），请检查账号信息";
                case 403 -> platform + "拒绝了同步请求（HTTP 403），请稍后重试或检查记录访问权限";
                case 404 -> platform + "未找到账号或接口（HTTP 404），请检查绑定账号";
                case 429 -> platform + "请求过于频繁（HTTP 429），请稍后重试";
                default -> platform + "请求失败（HTTP " + status + "），请稍后重试";
            };
        }
        if (cause instanceof java.net.SocketTimeoutException) return platform + "连接或读取超时，自动重试后仍未成功，请稍后重试";
        if (cause instanceof java.net.UnknownHostException) return "服务器无法解析 " + platform + " 的域名，请联系管理员";
        if (cause instanceof javax.net.ssl.SSLException || cause instanceof java.security.cert.CertificateException)
            return "服务器与 " + platform + " 的安全连接失败，请联系管理员";
        if (cause instanceof java.net.ConnectException) return "服务器无法连接 " + platform + "，请稍后重试";
        return platform + "网络请求失败，请稍后重试或联系管理员";
    }

    private Map<String, Object> readObject(String json) {
        try {
            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            require(result != null, "平台返回了空数据");
            return result;
        }
        catch (Exception e) { throw new TrackerSyncException("平台返回了无法解析的数据"); }
    }

    private List<Map<String, Object>> readList(String json) {
        try {
            List<Map<String, Object>> result = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            require(result != null, "平台返回了空提交列表");
            return result;
        }
        catch (Exception e) { throw new TrackerSyncException("平台返回了无法解析的提交列表"); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String label) {
        require(value instanceof Map, label + "缺失");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value, String label) {
        require(value instanceof List, label + "缺失");
        return (List<Map<String, Object>>) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new TrackerSyncException(message);
    }

    private static String requiredText(Object value) {
        require(value != null && !value.toString().isBlank(), "平台记录缺少唯一 ID");
        return value.toString();
    }

    private static long timestamp(Object value) {
        require(value instanceof Number && ((Number) value).longValue() > 0, "平台记录缺少有效通过时间");
        return ((Number) value).longValue();
    }

    private static String encode(String text) {
        return java.net.URLEncoder.encode(text.trim(), StandardCharsets.UTF_8);
    }

    private static String decode(String text) {
        // URLDecoder treats literal '+' as a space; decodeURIComponent does not.
        return java.net.URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> record(String platform, String pid, String title, long time) {
        Map<String, Object> record = new HashMap<>();
        record.put("platform", platform);
        record.put("problem_id", pid);
        record.put("title", title);
        record.put("solve_time", time);
        record.put("difficulty", 0);
        record.put("tags", List.of());
        return record;
    }

    private static void keepEarliest(Map<String, Map<String, Object>> records, Map<String, Object> record) {
        records.merge((String) record.get("problem_id"), record, (a, b) ->
                ((Number) a.get("solve_time")).longValue() <= ((Number) b.get("solve_time")).longValue() ? a : b);
    }

    // Upcoming Contests Crawlers
    private List<Map<String, Object>> fetchCodeforcesUpcoming() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "https://codeforces.com/api/contest.list?gym=false";
            String resp = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(resp, new TypeReference<Map<String, Object>>() {});
            if (data != null && "OK".equals(data.get("status"))) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("result");
                for (Map<String, Object> c : list) {
                    if (!(c.get("startTimeSeconds") instanceof Number start) || !(c.get("durationSeconds") instanceof Number duration)) continue;
                    long endTime = start.longValue() + duration.longValue();
                    if ("BEFORE".equals(c.get("phase")) || endTime > System.currentTimeMillis() / 1000) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", c.get("name"));
                        map.put("platform", "CodeForces");
                        map.put("link", "https://codeforces.com/contest/" + c.get("id"));
                        map.put("start_time", ((Number) c.get("startTimeSeconds")).longValue());
                        map.put("end_time", endTime);
                        map.put("unique_id", "CF_" + c.get("id"));
                        results.add(map);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Upcoming] CF error: {}", e.getClass().getSimpleName());
        }
        return results;
    }

    private List<Map<String, Object>> fetchAtcoderUpcoming() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "https://atcoder.jp/contests";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10000)
                    .get();

            for (String tableId : List.of("contest-table-active", "contest-table-upcoming")) {
            org.jsoup.nodes.Element table = doc.getElementById(tableId);
            if (table != null) {
                org.jsoup.select.Elements rows = table.select("tbody tr");
                for (org.jsoup.nodes.Element row : rows) {
                    org.jsoup.select.Elements tds = row.select("td");
                    if (tds.size() < 2) continue;

                    String timeStr = tds.get(0).text().trim();
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*(\\d{2}:\\d{2})");
                    java.util.regex.Matcher m = p.matcher(timeStr);
                    if (!m.find()) continue;

                    String dtStr = m.group(1) + " " + m.group(2);
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dtStr, formatter);
                    java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.of("Asia/Tokyo"));
                    long ts = zdt.toEpochSecond();

                    org.jsoup.nodes.Element linkTag = tds.get(1).selectFirst("a");
                    if (linkTag == null) continue;

                    String name = linkTag.text().trim();
                    String path = linkTag.attr("href");
                    String[] parts = path.split("/");
                    String contestId = parts[parts.length - 1];

                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("platform", "AtCoder");
                    map.put("link", "https://atcoder.jp" + path);
                    map.put("start_time", ts);
                    if (tds.size() >= 3) {
                        Long seconds = parseContestDurationSeconds(tds.get(2).text().trim());
                        if (seconds != null) map.put("end_time", ts + seconds);
                    }
                    map.put("unique_id", "AT_" + contestId);
                    results.add(map);
                }
            }
            }
        } catch (Exception e) {
            log.error("[Upcoming] AtCoder error: {}", e.getClass().getSimpleName());
        }
        return results;
    }

    private List<Map<String, Object>> fetchNowcoderUpcoming() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "https://ac.nowcoder.com/acm/contest/vip-index";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://ac.nowcoder.com/")
                    .header("Host", "ac.nowcoder.com")
                    .timeout(30000)
                    .get();

            org.jsoup.select.Elements items = doc.select(".platform-item-cont");
            for (org.jsoup.nodes.Element item : items) {
                org.jsoup.nodes.Element h4_a = item.selectFirst("h4 a");
                if (h4_a == null) continue;

                String name = h4_a.text().trim();
                String link = h4_a.attr("href");

                org.jsoup.nodes.Element timeDiv = item.selectFirst(".match-time-icon");
                if (timeDiv == null) continue;
                String timeText = timeDiv.text();

                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})");
                java.util.regex.Matcher m = p.matcher(timeText);
                if (!m.find()) continue;

                String dtStr = m.group(1) + " " + m.group(2);
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dtStr, formatter);
                java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.of("Asia/Shanghai"));
                long ts = zdt.toEpochSecond();

                Long endTime = parseNowcoderContestEnd(timeText, ts);
                if (ts > System.currentTimeMillis() / 1000 || (endTime != null && endTime > System.currentTimeMillis() / 1000)) {
                    String contestId = link.substring(link.lastIndexOf("/") + 1);
                    java.util.regex.Pattern cp = java.util.regex.Pattern.compile("/contest/(\\d+)");
                    java.util.regex.Matcher cm = cp.matcher(link);
                    if (cm.find()) {
                        contestId = cm.group(1);
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("platform", "NowCoder");
                    map.put("link", link.startsWith("/") ? "https://ac.nowcoder.com" + link : link);
                    map.put("start_time", ts);
                    if (endTime != null) map.put("end_time", endTime);
                    org.jsoup.nodes.Element registration = item.selectFirst(".time-icon");
                    if (registration != null) {
                        List<Long> dates = parseContestDates(registration.text());
                        if (!dates.isEmpty()) map.put("registration_start", dates.get(0));
                        if (dates.size() > 1) map.put("registration_end", dates.get(1));
                    }
                    map.put("unique_id", "NC_" + contestId);
                    results.add(map);
                }
            }
        } catch (Exception e) {
            log.error("[Upcoming] NowCoder error: {}", e.getClass().getSimpleName());
        }
        return results;
    }

    /** Parse only durations actually present in source HTML. Never guess a contest's duration. */
    static Long parseContestDurationSeconds(String value) {
        java.util.regex.Matcher clock = java.util.regex.Pattern.compile("^(\\d{1,4}):([0-5]\\d)$").matcher(value == null ? "" : value.trim());
        if (clock.matches()) return Long.parseLong(clock.group(1)) * 3600 + Long.parseLong(clock.group(2)) * 60;
        java.util.regex.Matcher units = java.util.regex.Pattern.compile("(?:(\\d+)\\s*(?:小时|h))?\\s*(?:(\\d+)\\s*(?:分钟|min))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value == null ? "" : value.trim());
        if (units.matches() && (units.group(1) != null || units.group(2) != null)) {
            return (units.group(1) == null ? 0L : Long.parseLong(units.group(1)) * 3600)
                    + (units.group(2) == null ? 0L : Long.parseLong(units.group(2)) * 60);
        }
        return null;
    }

    static Long parseNowcoderContestEnd(String value, long startEpoch) {
        List<Long> dates = parseContestDates(value);
        if (dates.size() > 1) return dates.get(1);
        java.util.regex.Matcher duration = java.util.regex.Pattern.compile("(?:时长|比赛时长)\\s*[:：]?\\s*([0-9]+\\s*(?:小时|h)(?:\\s*[0-9]+\\s*(?:分钟|min))?|[0-9]+\\s*(?:分钟|min)|[0-9]{1,4}:[0-5][0-9])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
        if (duration.find()) {
            Long seconds = parseContestDurationSeconds(duration.group(1));
            if (seconds != null && seconds > 0) return startEpoch + seconds;
        }
        return null;
    }

    static List<Long> parseContestDates(String value) {
        List<Long> result = new ArrayList<>();
        java.util.regex.Matcher dates = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})").matcher(value == null ? "" : value);
        while (dates.find() && result.size() < 2) {
            try {
                result.add(java.time.LocalDateTime.parse(dates.group(1) + " " + dates.group(2), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond());
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    public List<Map<String, Object>> crawlUpcoming() {
        List<Map<String, Object>> allList = new ArrayList<>();
        allList.addAll(fetchCodeforcesUpcoming());
        allList.addAll(fetchAtcoderUpcoming());
        allList.addAll(fetchNowcoderUpcoming());
        allList.sort(Comparator.comparingLong(x -> ((Number) x.get("start_time")).longValue()));
        return allList;
    }

    // Unified call dispatcher
    public List<Map<String, Object>> crawlQoj(String handle, String cookie) {
        require(handle != null && !handle.isBlank() && com.cwnupaa.backend.utils.OjBindingPolicy.validQojHandle(handle),
                "QOJ / UCup 请填写登录用户名，不要填写队名或网址");
        require(cookie != null && !cookie.isBlank(), "QOJ / UCup 需要登录凭据，请在同步窗口填写 QOJ Cookie");
        require(cookie.length() <= 16000 && !cookie.contains("\r") && !cookie.contains("\n"), "QOJ Cookie 格式不正确");
        Map<String, Map<String, Object>> solved = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        long previousMinimum = Long.MAX_VALUE;
        for (int page = 1; page <= 2000; page++) {
            String html = getPage("https://qoj.ac/submissions?submitter=" + encode(handle) + "&page=" + page,
                    Map.of("Cookie", cookie.trim(), "Accept-Language", "en", "Referer", "https://qoj.ac/"), 2200);
            var parsed = QojSubmissionParser.parse(html, handle.trim(), page);
            long minimum = Long.MAX_VALUE;
            int added = 0;
            for (var submission : parsed.submissions()) {
                if (!seen.add(submission.id())) continue;
                added++;
                minimum = Math.min(minimum, submission.id());
                if (submission.accepted()) keepEarliest(solved,
                        record("QOJ", submission.problemId(), submission.title(), submission.time()));
            }
            require(page == 1 || (added > 0 && minimum < previousMinimum), "QOJ 分页未前进，原统计已保留");
            if (!parsed.hasNext()) return new ArrayList<>(solved.values());
            require(added > 0, "QOJ 分页返回空记录，原统计已保留");
            previousMinimum = minimum;
        }
        throw new TrackerSyncException("QOJ 历史记录超过单次同步上限，原统计已保留");
    }

    public Map<String, Object> crawlSubmissions(String platform, String handle, long lastTime, String clientId) {
        List<Map<String, Object>> list;
        if ("codeforces".equalsIgnoreCase(platform)) {
            list = crawlCodeforces(handle);
        } else if ("atcoder".equalsIgnoreCase(platform)) {
            list = crawlAtcoder(handle);
        } else if ("cwnu".equalsIgnoreCase(platform)) {
            list = crawlCwnu(handle);
        } else if ("luogu".equalsIgnoreCase(platform)) {
            list = crawlLuogu(handle, lastTime, clientId);
        } else if ("nowcoder".equalsIgnoreCase(platform)) {
            list = crawlNowcoder(handle, lastTime);
        } else if ("qoj".equalsIgnoreCase(platform) || "ucup".equalsIgnoreCase(platform)) {
            list = crawlQoj(handle, clientId);
        } else {
            log.error("Unknown crawler platform: {}", platform);
            list = Collections.emptyList();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("submissions", list);
        result.put("count", list.size());
        result.put("complete", true);
        return result;
    }
}
