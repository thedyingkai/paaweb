package com.cwnupaa.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TrackerCrawlerTests {
    private final CrawlerLocalRunner crawler = new CrawlerLocalRunner();
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        RestTemplate http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        ReflectionTestUtils.setField(crawler, "restTemplate", http);
        ReflectionTestUtils.setField(crawler, "paceRequests", false);
        // Optional metadata is already cached; these tests exercise submission completeness.
        ReflectionTestUtils.setField(crawler, "atcoderLastUpdate", System.currentTimeMillis());
        ReflectionTestUtils.setField(crawler, "atcoderProblemsMap", new java.util.concurrent.ConcurrentHashMap<>(Map.of("abc_a", "A")));
        ReflectionTestUtils.setField(crawler, "nowcoderDifficultyCache", new java.util.concurrent.ConcurrentHashMap<>(Map.of("10", 800.0, "11", 900.0)));
    }

    private void reply(String url, Object body) throws Exception {
        server.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body instanceof String str ? str : json.writeValueAsString(body), MediaType.APPLICATION_JSON));
    }

    private Map<String, Object> at(int id, long time, String pid, String verdict) {
        return Map.of("id", id, "epoch_second", time, "problem_id", pid, "result", verdict);
    }

    @Test
    void codeforcesRecoversFromOneUpstreamServerFailure() throws Exception {
        String url = "https://codeforces.com/api/user.status?handle=test&from=1&count=10000";
        server.expect(requestTo(url)).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));
        reply(url, Map.of("status", "OK", "result", List.of()));
        assertThat(crawler.crawlCodeforces("test")).isEmpty();
        server.verify();
    }

    @Test
    void requestTimeoutRetriesOnceAndDoesNotExposeSensitiveExceptionText() {
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                requestTo("https://codeforces.com/api/user.status?handle=test&from=1&count=10000"))
                .andRespond(request -> { throw new java.net.SocketTimeoutException("private-cookie-marker"); });
        assertThatThrownBy(() -> crawler.crawlCodeforces("test"))
                .hasMessageContaining("超时").hasMessageContaining("自动重试")
                .hasMessageNotContaining("private-cookie-marker");
        server.verify();
    }

    @Test
    void forbiddenPublicApiDoesNotAskForLoginCredentialsOrLeakResponse() {
        server.expect(requestTo("https://codeforces.com/api/user.status?handle=test&from=1&count=10000"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN).body("private-response-marker"));
        assertThatThrownBy(() -> crawler.crawlCodeforces("test"))
                .hasMessageContaining("403").hasMessageNotContaining("登录凭据")
                .hasMessageNotContaining("private-response-marker");
        server.verify();
    }

    @Test
    void dnsFailureIdentifiesThePlatformAndDoesNotRetry() {
        server.expect(requestTo("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=0"))
                .andRespond(request -> { throw new java.net.UnknownHostException("private-host-marker"); });
        assertThatThrownBy(() -> crawler.crawlAtcoder("test"))
                .hasMessageContaining("AtCoder").hasMessageContaining("域名")
                .hasMessageNotContaining("private-host-marker");
        server.verify();
    }

    @Test
    void atcoderLoadsBeyond500UsesAllVerdictsForCursorAndKeepsFirstAc() throws Exception {
        List<Map<String, Object>> first = new ArrayList<>();
        for (int i = 1; i <= 500; i++) first.add(at(i, i, "abc_a", i == 2 ? "AC" : "WA"));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=0", first);
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=500",
                List.of(at(500, 500, "abc_a", "WA"), at(501, 500, "abc_b", "AC"), at(502, 501, "abc_a", "AC")));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=test", Map.of("count", 2));
        var result = crawler.crawlAtcoder("test");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("problem_id", "abc_a").containsEntry("solve_time", 2L);
        assertThat(result.get(1)).containsEntry("problem_id", "abc_b");
        server.verify();
    }

    @Test
    void atcoderDoesNotStopWhenTheFirstPageHasNoAc() throws Exception {
        var first = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= 500; i++) first.add(at(i, i, "abc_a", "WA"));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=0", first);
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=500", List.of(at(501, 501, "abc_b", "AC")));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=test", Map.of("count", 1));
        assertThat(crawler.crawlAtcoder("test")).hasSize(1);
    }

    @Test
    void atcoderRankMismatchIsNotAnAccurateSnapshot() throws Exception {
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=0", List.of(at(1, 1, "abc_a", "AC")));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=test", Map.of("count", 2));
        assertThatThrownBy(() -> crawler.crawlAtcoder("test")).hasMessageContaining("不一致");
    }

    @Test
    void atcoderSecondPageFailureIsNotPartialSuccess() throws Exception {
        var first = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= 500; i++) first.add(at(i, i, "abc_a", "AC"));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=0", first);
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                requestTo("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=500"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> crawler.crawlAtcoder("test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 500");
        server.verify();
    }

    @Test
    void atcoderSaturatedTimestampCannotSilentlySkipSubmissions() throws Exception {
        var page = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= 500; i++) page.add(at(i, 500, "abc_a", "AC"));
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=0", page);
        reply("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=test&from_second=500", page);
        assertThatThrownBy(() -> crawler.crawlAtcoder("test")).hasMessageContaining("分页未前进");
    }

    @Test
    void codeforcesCountsOnlyOkAndDistinguishesContests() throws Exception {
        reply("https://codeforces.com/api/user.status?handle=test&from=1&count=10000", Map.of("status", "OK", "result", List.of(
                cf(3, 1800, "A", 30, "OK"), cf(1, 1800, "A", 10, "OK"),
                cf(2, 1800, "B", 20, "WRONG_ANSWER"), cf(4, 1801, "A", 40, "OK"))));
        var result = crawler.crawlCodeforces("test");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("problem_id", "CF1800A").containsEntry("solve_time", 10L);
        assertThat(result.get(1)).containsEntry("problem_id", "CF1801A");
    }

    private Map<String, Object> cf(int id, int contest, String index, long time, String verdict) {
        return Map.of("id", id, "creationTimeSeconds", time, "verdict", verdict,
                "problem", Map.of("contestId", contest, "index", index, "name", "Same title"));
    }

    @Test
    void codeforcesDoesNotTruncateAt10000Submissions() throws Exception {
        var first = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= 10000; i++) first.add(cf(i, 1800, "A", i, "OK"));
        reply("https://codeforces.com/api/user.status?handle=test&from=1&count=10000", Map.of("status", "OK", "result", first));
        reply("https://codeforces.com/api/user.status?handle=test&from=10001&count=10000", Map.of("status", "OK", "result", List.of(cf(10001, 1800, "B", 10001, "OK"))));
        assertThat(crawler.crawlCodeforces("test")).hasSize(2);
        server.verify();
    }

    @Test
    void codeforcesApiFailureIsNotZeroSolved() throws Exception {
        reply("https://codeforces.com/api/user.status?handle=test&from=1&count=10000", Map.of("status", "FAILED"));
        assertThatThrownBy(() -> crawler.crawlCodeforces("test")).hasMessageContaining("接口返回失败");
    }

    private String ncPage(int count, String rows, boolean next) {
        return "<div class='my-state-item'><div class='state-num'>" + count + "</div><span>题已通过</span></div>"
                + "<table class='table-hover'><tbody>" + rows + "</tbody></table>"
                + (next ? "<div class='pagination'><a href='?page=2'>下一页</a></div>" : "");
    }

    private String ncRow(int rid, String pid, String title, String status, String date) {
        return "<tr><td>" + rid + "</td><td><a href='/acm/problem/" + pid + "'>" + title + "</a></td><td>" + status
                + "</td><td>100</td><td>0</td><td>0</td><td>0</td><td>C++</td><td>" + date + "</td></tr>";
    }

    private String ncUrl(int page) {
        return "https://ac.nowcoder.com/acm/contest/profile/123/practice-coding?pageSize=200&statusTypeFilter=5&orderType=DESC&page=" + page;
    }

    @Test
    void nowcoderPaginatesDeduplicatesByIdAndChecksProfileCount() throws Exception {
        reply(ncUrl(1), ncPage(2, ncRow(3, "10", "同名题", "答案正确", "2026-01-03 10:00:00"), true));
        reply(ncUrl(2), ncPage(2, ncRow(2, "11", "同名题", "答案正确", "2026-01-02 10:00:00")
                + ncRow(1, "10", "改过题名", "答案正确", "2026-01-01 10:00:00"), false));
        var result = crawler.crawlNowcoder("123", 0);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("title", "改过题名")
                .containsEntry("solve_time", java.time.Instant.parse("2026-01-01T02:00:00Z").getEpochSecond());
        server.verify();
    }

    @Test
    void nowcoderMissingPageIsNotPartialSuccess() throws Exception {
        reply(ncUrl(1), ncPage(2, ncRow(1, "10", "题目", "答案正确", "2026-01-01 10:00:00"), false));
        assertThatThrownBy(() -> crawler.crawlNowcoder("123", 0)).hasMessageContaining("未抓取完整");
    }

    @Test
    void nowcoderInvalidUserOrChallengeIsNotZeroSolved() throws Exception {
        reply(ncUrl(1), "<html>用户不存在</html>");
        assertThatThrownBy(() -> crawler.crawlNowcoder("123", 0)).hasMessageContaining("无法读取");
    }

    @Test
    void nowcoderNeverCountsWrongAnswersEvenWhenTheFilterIsIgnored() throws Exception {
        reply(ncUrl(1), ncPage(1, ncRow(1, "10", "题目", "答案错误", "2026-01-01 10:00:00"), false));
        assertThatThrownBy(() -> crawler.crawlNowcoder("123", 0)).hasMessageContaining("非通过记录");
    }

    @Test
    void nowcoderRepeatedPageCannotBeAccepted() throws Exception {
        var page = ncPage(2, ncRow(1, "10", "题目", "答案正确", "2026-01-01 10:00:00"), true);
        reply(ncUrl(1), page);
        reply(ncUrl(2), page);
        assertThatThrownBy(() -> crawler.crawlNowcoder("123", 0)).hasMessageContaining("未抓取完整");
    }

    @Test
    void nowcoderReadsPastTheOld50PageLimit() throws Exception {
        Map<String, Double> difficulties = new java.util.concurrent.ConcurrentHashMap<>();
        for (int i = 1; i <= 51; i++) {
            difficulties.put(Integer.toString(i), 800.0);
            String html = ncPage(51, ncRow(i, Integer.toString(i), "题目", "答案正确", "2026-01-01 10:00:00"), false);
            if (i < 51) html += "<div class='pagination'><a href='?page=" + (i + 1) + "'>下一页</a></div>";
            reply(ncUrl(i), html);
        }
        ReflectionTestUtils.setField(crawler, "nowcoderDifficultyCache", difficulties);
        assertThat(crawler.crawlNowcoder("123", 0)).hasSize(51);
        server.verify();
    }

    @Test
    void nowcoderDoesNotInventIdsFromProblemTitles() throws Exception {
        reply(ncUrl(1), ncPage(1, ncRow(1, "10", "题目", "答案正确", "2026-01-01 10:00:00")
                .replace("/acm/problem/10", "/acm/contest/999/A"), false));
        assertThatThrownBy(() -> crawler.crawlNowcoder("123", 0)).hasMessageContaining("全局题目 ID");
    }

    @Test
    void nowcoderVerifiedZeroIsValid() throws Exception {
        reply(ncUrl(1), ncPage(0, "", false));
        assertThat(crawler.crawlNowcoder("123", 0)).isEmpty();
    }

    private Map<String, Object> lgRow(int id, String pid, int time, int status) {
        return Map.of("id", id, "submitTime", time, "status", status, "user", Map.of("uid", 123),
                "problem", Map.of("pid", pid, "title", "C++ + test", "difficulty", 3));
    }

    private String lgUrl(int page) {
        return "https://www.luogu.com.cn/record/list?user=123&status=12&page=" + page;
    }

    private void replyLuoguPractice(int count, String... pids) throws Exception {
        reply("https://www.luogu.com.cn/user/123/practice", Map.of("data", Map.of(
                "user", Map.of("uid", 123, "passedProblemCount", count),
                "passed", Arrays.stream(pids).map(pid -> Map.of("pid", pid)).toList())));
    }

    @Test
    void luoguNumericUidSkipsFuzzySearchAndSupportsBothPageFormats() throws Exception {
        String first = json.writeValueAsString(Map.of("currentData", Map.of("records", Map.of("count", 3,
                "result", List.of(lgRow(3, "P1001", 30, 12), lgRow(2, "P1002", 20, 12))))));
        reply(lgUrl(1), "<script>window._feInjection=JSON.parse(decodeURIComponent(\""
                + java.net.URLEncoder.encode(first, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20") + "\"));</script>");
        String second = json.writeValueAsString(Map.of("data", Map.of("records", Map.of("count", 3, "result", List.of(lgRow(1, "P1001", 10, 12))))));
        server.expect(requestTo(lgUrl(2))).andExpect(header("Cookie", "__client_id=abc;_uid=999;"))
                .andRespond(withSuccess("<script id='lentille-context' type='application/json'>" + second + "</script>", MediaType.TEXT_HTML));
        replyLuoguPractice(2, "P1001", "P1002");
        var result = crawler.crawlLuogu("123", 0, "__client_id=abc;_uid=999");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("title", "C++ + test").containsEntry("solve_time", 10L);
        server.verify();
    }

    @Test
    void luoguLegacyClientIdUsesTheResolvedUidForRecordRequests() throws Exception {
        reply("https://www.luogu.com.cn/api/user/search?keyword=test",
                Map.of("users", List.of(Map.of("uid", 456, "name", "test2"), Map.of("uid", 123, "name", "test"))));
        server.expect(requestTo(lgUrl(1)))
                .andExpect(header("Cookie", "__client_id=abc;_uid=123;"))
                .andRespond(withSuccess(json.writeValueAsString(Map.of("data", Map.of("records",
                        Map.of("count", 1, "result", List.of(lgRow(1, "P1", 10, 12)))))), MediaType.APPLICATION_JSON));
        replyLuoguPractice(1, "P1");
        assertThat(crawler.crawlLuogu("test", 0, "abc")).hasSize(1);
        server.verify();
    }

    @Test
    void luoguUnauthorizedResponseAsksForFreshCredentialsWithoutExposingThem() {
        server.expect(requestTo(lgUrl(1)))
                .andExpect(header("Cookie", "__client_id=expired-token;_uid=123;"))
                .andRespond(withUnauthorizedRequest().body("expired-token"));
        assertThatThrownBy(() -> crawler.crawlLuogu("123", 0, "__client_id=expired-token"))
                .hasMessageContaining("401")
                .hasMessageContaining("__client_id")
                .hasMessageContaining("_uid")
                .hasMessageNotContaining("expired-token");
        server.verify();
    }

    @Test
    void luoguRetainsVerifiedAcceptedProblemsOutsideThePracticeList() throws Exception {
        reply(lgUrl(1), Map.of("data", Map.of("records", Map.of("count", 2, "result",
                List.of(lgRow(2, "U1", 20, 12), Map.of("id", 1, "submitTime", 10, "status", 12,
                        "user", Map.of("uid", 123), "problem", Map.of("pid", "P1", "name", "洛谷题目")))))));
        replyLuoguPractice(1, "P1");
        var result = crawler.crawlLuogu("123", 0, "abc");
        assertThat(result).extracting(row -> row.get("problem_id")).containsExactly("U1", "P1");
        assertThat(result.get(1)).containsEntry("title", "洛谷题目");
        server.verify();
    }

    @Test
    void luoguDoesNotSaveWhenAnExtraRecordMasksAMissingProblem() throws Exception {
        reply(lgUrl(1), Map.of("currentData", Map.of("records", Map.of("count", 2, "result",
                List.of(lgRow(2, "U1", 20, 12), lgRow(1, "P1", 10, 12))))));
        replyLuoguPractice(2, "P1", "P2");
        assertThatThrownBy(() -> crawler.crawlLuogu("123", 0, "abc")).hasMessageContaining("可见记录");
    }

    @Test
    void luoguRejectsAnIncompletePracticeListEvenIfAllItsProblemsAreVisible() throws Exception {
        reply(lgUrl(1), Map.of("data", Map.of("records", Map.of("count", 1, "result", List.of(lgRow(1, "P1", 10, 12))))));
        replyLuoguPractice(2, "P1");
        assertThatThrownBy(() -> crawler.crawlLuogu("123", 0, "abc")).hasMessageContaining("清单与通过题数不一致");
    }

    @Test
    void luoguLoginPageAfterFirstPageIsNotPartialSuccess() throws Exception {
        reply(lgUrl(1), Map.of("currentData", Map.of("records", Map.of("count", 2, "result", List.of(lgRow(1, "P1", 10, 12))))));
        reply(lgUrl(2), "<html>请登录</html>");
        assertThatThrownBy(() -> crawler.crawlLuogu("123", 0, "abc")).hasMessageContaining("无法解析洛谷记录");
    }

    @Test
    void luoguDoesNotChooseTheFirstFuzzyMatch() throws Exception {
        reply("https://www.luogu.com.cn/api/user/search?keyword=test", Map.of("users", List.of(Map.of("uid", 111, "name", "test2"))));
        assertThatThrownBy(() -> crawler.crawlLuogu("test", 0, "abc")).hasMessageContaining("唯一匹配");
    }

    @Test
    void luoguRejectsWrongVerdict() throws Exception {
        reply(lgUrl(1), Map.of("currentData", Map.of("records", Map.of("count", 1, "result", List.of(lgRow(1, "P1", 10, 7))))));
        assertThatThrownBy(() -> crawler.crawlLuogu("123", 0, "abc")).hasMessageContaining("非通过记录");
    }
}
