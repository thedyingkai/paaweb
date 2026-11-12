package com.cwnupaa.backend.task;

import com.cwnupaa.backend.entity.OjUserAccount;
import com.cwnupaa.backend.mapper.OjUserAccountMapper;
import com.cwnupaa.backend.service.OjRecentContestService;
import com.cwnupaa.backend.service.TrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class TrackerTask {

    private static final Logger log = LoggerFactory.getLogger(TrackerTask.class);

    private final OjUserAccountMapper ojUserAccountMapper;
    private final TrackerService trackerService;
    private final OjRecentContestService ojRecentContestService;

    @Autowired
    public TrackerTask(OjUserAccountMapper ojUserAccountMapper, TrackerService trackerService, OjRecentContestService ojRecentContestService) {
        this.ojUserAccountMapper = ojUserAccountMapper;
        this.trackerService = trackerService;
        this.ojRecentContestService = ojRecentContestService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initUpcomingContests() {
        new Thread(() -> {
            log.info("========== [系统启动] 开始初始化近期赛事数据... ==========");
            try {
                Thread.sleep(3000);
                ojRecentContestService.syncContests();
                log.info("========== [系统启动] 近期赛事数据初始化完成 ==========");
            } catch (Exception e) {
                log.error("========== [系统启动] 近期赛事初始化失败 ========== ({})", e.getClass().getSimpleName());
            }
        }).start();
    }

    @Scheduled(cron = "0 0 */4 * * ?")
    public void syncUpcomingContests() {
        log.info("开始定时同步近期赛事...");
        try {
            ojRecentContestService.syncContests();
            log.info("近期赛事定时同步成功");
        } catch (Exception e) {
            log.error("近期赛事定时同步失败 ({})", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void syncAllUserTrackerData() {
        log.info("开始每日全站 CP Tracker 数据同步...");
        List<OjUserAccount> accounts = ojUserAccountMapper.selectList(null);
        int total = 0;
        int success = 0;
        int failed = 0;

        for (OjUserAccount account : accounts) {
            if (account == null || !hasAnyTrackerAccount(account)) {
                continue;
            }

            total++;
            try {
                trackerService.refreshUserTrackerQuietly(account.getUserId(), null);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("用户 {} CP Tracker 每日同步失败 ({})", account.getUserId(), e.getClass().getSimpleName());
            }
        }

        log.info("每日全站 CP Tracker 数据同步完成: total={}, success={}, failed={}", total, success, failed);
    }

    private boolean hasAnyTrackerAccount(OjUserAccount account) {
        return StringUtils.hasText(account.getCodeforcesId())
                || StringUtils.hasText(account.getAtcoderId())
                || StringUtils.hasText(account.getNewcoderId())
                || StringUtils.hasText(account.getLuoguId())
                || StringUtils.hasText(account.getQojId())
                || StringUtils.hasText(account.getUcupId())
                || StringUtils.hasText(account.getCwnuojId());
    }
}
