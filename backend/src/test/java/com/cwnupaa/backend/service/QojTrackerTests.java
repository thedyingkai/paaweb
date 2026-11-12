package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.OjUserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QojTrackerTests {
    private CrawlerLocalRunner crawler;
    private MockRestServiceServer http;
    @BeforeEach void setup() {
        crawler = new CrawlerLocalRunner();
        RestTemplate client = new RestTemplate();
        http = MockRestServiceServer.bindTo(client).build();
        ReflectionTestUtils.setField(crawler, "restTemplate", client);
        ReflectionTestUtils.setField(crawler, "qojRestTemplate", client);
        ReflectionTestUtils.setField(crawler, "paceRequests", false);
    }
    private String row(long id, String problem, String verdict, String date) {
        return "<tr><td><a href='/submission/"+id+"'>"+id+"</a></td><td><a href='"+problem+"'>Task</a></td>"
                +"<td><span class='uoj-username'>tester</span><small>virtual</small></td><td>"+verdict+"</td>"
                +"<td>1ms</td><td>8KB</td><td>C++</td><td>100B</td><td>"+date+"</td></tr>";
    }
    private String pager(int page, int end) {
        var b = new StringBuilder("<ul class='pagination'>");
        for (int i=1;i<=end;i++) b.append("<li class='").append(i==page?"active":"").append("'><a href='/submissions?submitter=tester&amp;page=")
                .append(i).append("'>").append(i).append("</a></li>");
        return b.append("</ul>").toString();
    }
    private void reply(int page, String body) {
        http.expect(requestTo("https://qoj.ac/submissions?submitter=tester&page="+page))
                .andExpect(header("Cookie", "session=private-marker"))
                .andRespond(withSuccess(body, MediaType.TEXT_HTML));
    }
    @Test void loadsEveryPageAndKeepsEarliestGlobalProblemAcrossContests() {
        reply(1, "<table>"+row(200,"/contest/12/problem/42","Accepted","2026-11-12 15:00:00")
                +row(199,"/problem/43","<span class='uoj-score'>100</span>","2026-11-11 15:00:00")+"</table>"+pager(1,2));
        reply(2, "<table>"+row(100,"/problem/42","<span data-score='100' data-full='100'>AC</span>","2026-10-12 10:00:00")
                +row(99,"/problem/44","<span class='uoj-score'>60</span>","2026-10-11 10:00:00")+"</table>"+pager(2,2));
        var result = crawler.crawlSubmissions("UCup", "tester", 0, "session=private-marker");
        var solved = (java.util.List<java.util.Map<String,Object>>) result.get("submissions");
        assertThat(solved).hasSize(2);
        assertThat(solved.getFirst().get("problem_id")).isEqualTo("QOJ42");
        assertThat(solved.getFirst().get("solve_time")).isEqualTo(java.time.LocalDateTime.of(2026,10,12,10,0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond());
        assertThat(result.get("complete")).isEqualTo(true);
        http.verify();
    }
    @Test void wrongAccountCannotBeCredited() {
        reply(1, "<table>"+row(200,"/problem/42","AC","2026-11-12 15:00:00").replace("tester", "someone_else")+"</table>");
        assertThatThrownBy(() -> crawler.crawlQoj("tester", "session=private-marker")).hasMessageContaining("账号不匹配");
    }
    @Test void aLoginPageIsNotAnEmptyHistory() {
        reply(1, "<form id='form-login'><input type='password'></form>");
        assertThatThrownBy(() -> crawler.crawlQoj("tester", "session=private-marker"))
                .hasMessageContaining("登录已失效").hasMessageNotContaining("private-marker");
    }
    @Test void loginRedirectDoesNotForwardCredentials() {
        http.expect(requestTo("https://qoj.ac/submissions?submitter=tester&page=1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FOUND).header("Location", "https://other.example/login"));
        assertThatThrownBy(() -> crawler.crawlQoj("tester", "session=private-marker")).hasMessageContaining("登录已失效");
        http.verify();
    }
    @Test void missingCookieFailsBeforeAnyRequest() {
        assertThatThrownBy(() -> crawler.crawlQoj("tester", null)).hasMessageContaining("填写 QOJ Cookie");
        http.verify();
    }
    @Test void missingPaginationCannotEraseTheRestOfHistory() {
        String rows = row(200,"/problem/42","AC","2026-11-12 15:00:00").repeat(10);
        reply(1,"<table>"+rows+"</table>");
        assertThatThrownBy(() -> crawler.crawlQoj("tester","session=private-marker")).hasMessageContaining("分页信息缺失");
    }
    @Test void repeatedSecondPageIsNotReportedAsSuccessful() {
        String rows = "<table>"+row(200,"/problem/42","AC","2026-11-12 15:00:00")+"</table>";
        reply(1, rows+pager(1,2)); reply(2,rows+pager(2,2));
        assertThatThrownBy(() -> crawler.crawlQoj("tester","session=private-marker")).hasMessageContaining("分页未前进");
    }
    @Test void malformedPageCannotBeSavedAsZero() {
        reply(1,"<h1>Service unavailable</h1>");
        assertThatThrownBy(() -> crawler.crawlQoj("tester","session=private-marker")).hasMessageContaining("无法确认");
    }
    @Test void cookieNeverAppearsInSerializedAccount() throws Exception {
        var account = new OjUserAccount(); account.setQojId("tester"); account.setQojCookie("private-marker");
        assertThat(new ObjectMapper().writeValueAsString(account)).contains("tester").doesNotContain("private-marker", "qojCookie");
    }
}
