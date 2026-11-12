package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.OjPlatform;
import com.cwnupaa.backend.entity.OjUserAccount;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrackerSyncFailureTests {
    private final OjUserAccountMapper accounts = mock(OjUserAccountMapper.class);
    private final OjPracticeRecordMapper records = mock(OjPracticeRecordMapper.class);
    private final OjPlatformMapper platforms = mock(OjPlatformMapper.class);
    private final CrawlerLocalRunner crawler = mock(CrawlerLocalRunner.class);
    private final TrackerSnapshotService snapshots = mock(TrackerSnapshotService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private TrackerService tracker;

    @BeforeEach
    void setup() {
        tracker = new TrackerService(accounts, records, platforms, mock(RatingCompositionMapper.class),
                mock(RatingLogMapper.class), mock(OjRecentContestMapper.class), new org.springframework.web.client.RestTemplate());
        ReflectionTestUtils.setField(tracker, "crawlerLocalRunner", crawler);
        ReflectionTestUtils.setField(tracker, "trackerSnapshotService", snapshots);
        ReflectionTestUtils.setField(tracker, "notificationService", notifications);
        var account = new OjUserAccount();
        account.setUserId(1L);
        account.setAtcoderId("test");
        account.setLuoguId("123");
        when(accounts.selectById(1L)).thenReturn(account);
        var platform = new OjPlatform();
        platform.setOjId(2L);
        platform.setOjName("AtCoder");
        when(platforms.selectList(any())).thenReturn(List.of(platform));
    }

    @AfterEach
    void cleanup() { ((ExecutorService) ReflectionTestUtils.getField(tracker, "crawlerExecutor")).shutdownNow(); }

    @Test
    void crawlerFailurePreservesRecordsAndSendsFailureOnly() {
        when(crawler.crawlSubmissions("AtCoder", "test", 0, null)).thenThrow(new TrackerSyncException("历史记录未完整抓取"));
        tracker.refreshUserTracker(1L, null, List.of("AtCoder"));
        verifyNoInteractions(snapshots, records);
        verify(notifications).send(eq(1L), eq(5), eq("CP Tracker数据同步失败"), contains("未完整抓取"));
        verify(notifications, never()).send(anyLong(), anyInt(), eq("CP Tracker数据同步完成"), anyString());
    }

    @Test
    void incompleteSnapshotIsNeverWritten() {
        when(crawler.crawlSubmissions("AtCoder", "test", 0, null)).thenReturn(Map.of("submissions", List.of(), "complete", false));
        tracker.refreshUserTracker(1L, null, List.of("AtCoder"));
        verifyNoInteractions(snapshots, records);
        verify(notifications).send(eq(1L), eq(5), eq("CP Tracker数据同步失败"), contains("完整获取"));
    }

    @Test
    void selectedPlatformDoesNotFetchSchoolOj() {
        when(crawler.crawlSubmissions("AtCoder", "test", 0, null)).thenReturn(Map.of("submissions", List.of(), "complete", true));
        tracker.refreshUserTracker(1L, null, List.of("AtCoder"));
        verify(crawler).crawlSubmissions("AtCoder", "test", 0, null);
        verifyNoMoreInteractions(crawler);
        verify(snapshots).reconcile(1L, 2L, "AtCoder", "test", List.of());
    }

    @Test
    void missingLuoguCookieReportsFailure() {
        tracker.refreshUserTracker(1L, null, List.of("Luogu"));
        verifyNoInteractions(crawler, snapshots, records);
        verify(notifications).send(eq(1L), eq(5), eq("CP Tracker数据同步失败"), contains("缺少 Cookie"));
    }

    @Test
    void websiteChecksQojCredentialsAndRejectsMalformedRequestsBeforeStarting() {
        var account = accounts.selectById(1L);
        account.setQojId("test");
        org.assertj.core.api.Assertions.assertThat(tracker.checkSyncRequest(1L, List.of("QOJ"), null)).contains("QOJ Cookie");
        account.setQojCookie("session=cached");
        org.assertj.core.api.Assertions.assertThat(tracker.checkSyncRequest(1L, List.of("QOJ"), null)).isNull();
        org.assertj.core.api.Assertions.assertThat(tracker.checkSyncRequest(1L, List.of("QOJ"), "bad\r\nheader")).contains("格式不正确");
        org.assertj.core.api.Assertions.assertThat(tracker.checkSyncRequest(1L, List.of("unknown"), null)).contains("支持的 OJ");
        verifyNoInteractions(crawler, snapshots, notifications);
    }

    @Test
    void manualSyncAlsoRefreshesDirectPeersQuietlyWithoutRecursing() {
        var peer = new OjUserAccount(); peer.setUserId(2L); peer.setAtcoderId("peer");
        when(accounts.selectById(2L)).thenReturn(peer);
        when(records.selectTrackerPeers(1L)).thenReturn(List.of(2L));
        when(crawler.crawlSubmissions(eq("AtCoder"), anyString(), eq(0L), isNull()))
                .thenReturn(Map.of("submissions", List.of(), "complete", true));
        tracker.refreshUserTracker(1L, null, List.of("AtCoder"), (String)null);
        verify(snapshots).reconcile(1L, 2L, "AtCoder", "test", List.of());
        verify(snapshots).reconcile(2L, 2L, "AtCoder", "peer", List.of());
        verify(records, never()).selectTrackerPeers(2L);
        verify(notifications, never()).send(eq(2L), anyInt(), anyString(), anyString());
    }
}
