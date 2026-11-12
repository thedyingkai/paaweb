package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.dto.SubmissionDTO;
import com.cwnupaa.backend.dto.SubmissionQueryDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.ChartScopeVO;
import com.cwnupaa.backend.vo.PlatformStatVO;
import com.cwnupaa.backend.vo.TrackerDashboardVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class TrackerService {
    @Autowired private ApplicationEventPublisher ratingEvents;

    private static final Logger log = LoggerFactory.getLogger(TrackerService.class);

    private final OjUserAccountMapper ojUserAccountMapper;
    private final OjPracticeRecordMapper practiceRecordMapper;
    private final OjPlatformMapper ojPlatformMapper;
    private final RatingCompositionMapper ratingCompositionMapper;
    private final RatingLogMapper ratingLogMapper;
    private final OjRecentContestMapper ojRecentContestMapper;
    private final RestTemplate restTemplate;

    private final ExecutorService crawlerExecutor = Executors.newCachedThreadPool();

    @Value("${crawler.service.url:http://localhost:8000}")
    private String crawlerServiceUrl;

    private final Map<String, Long> platformIdMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private TrackerSnapshotService trackerSnapshotService;

    private final Object[] externalSyncLocks = java.util.stream.IntStream.range(0, 256)
            .mapToObj(i -> new Object()).toArray();

    private static final Set<String> EXTERNAL_PLATFORMS = Set.of("CodeForces", "AtCoder", "NowCoder", "Luogu");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CrawlerLocalRunner crawlerLocalRunner;

    @Autowired
    public TrackerService(OjUserAccountMapper ojUserAccountMapper, OjPracticeRecordMapper practiceRecordMapper, OjPlatformMapper ojPlatformMapper, RatingCompositionMapper ratingCompositionMapper, RatingLogMapper ratingLogMapper, OjRecentContestMapper ojRecentContestMapper, RestTemplate restTemplate) {
        this.ojUserAccountMapper = ojUserAccountMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.ojPlatformMapper = ojPlatformMapper;
        this.ratingCompositionMapper = ratingCompositionMapper;
        this.ratingLogMapper = ratingLogMapper;
        this.ojRecentContestMapper = ojRecentContestMapper;
        this.restTemplate = restTemplate;
    }

    public TrackerDashboardVO getDashboardStats(Long userId) {
        initPlatformMap();
        List<OjPracticeRecord> allRecords = practiceRecordMapper.selectList(new QueryWrapper<OjPracticeRecord>().eq("user_id", userId));

        TrackerDashboardVO vo = new TrackerDashboardVO();
        LocalDateTime now = LocalDateTime.now();

        vo.setTotalSolved((long) allRecords.size());
        vo.setLast7DaysSolved(countAfter(allRecords, now.minusDays(7)));
        vo.setLast30DaysSolved(countAfter(allRecords, now.minusDays(30)));
        vo.setLastYearSolved(countAfter(allRecords, now.minusYears(1)));

        vo.setPlatformStats(calculatePlatformStats(allRecords));

        vo.setScope7Days(calculateChartScope(allRecords, 7, now));
        vo.setScope30Days(calculateChartScope(allRecords, 30, now));
        vo.setScope1Year(calculateChartScope(allRecords, 365, now));

        vo.setDailyHeatmap(calculateHeatmap(allRecords, now.minusYears(5), false));
        vo.setMaxDifficultyHeatmap(calculateHeatmap(allRecords, now.minusYears(5), true));

        return vo;
    }

    public List<OjRecentContest> getUpcomingContests() {
        QueryWrapper<OjRecentContest> query = new QueryWrapper<>();
        query.ge("contests_starttime", LocalDateTime.now()).orderByAsc("contests_starttime").last("LIMIT 10");
        return ojRecentContestMapper.selectList(query);
    }

    public IPage<OjPracticeRecord> searchSubmissions(SubmissionQueryDTO queryDTO) {
        Page<OjPracticeRecord> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        QueryWrapper<OjPracticeRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", queryDTO.getUserId());
        if (queryDTO.getSourceId() != null) wrapper.eq("source_id", queryDTO.getSourceId());
        if (queryDTO.getProblemId() != null && !queryDTO.getProblemId().isEmpty())
            wrapper.eq("problem_id", queryDTO.getProblemId());
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty())
            wrapper.like("problem_name", queryDTO.getKeyword());
        if (queryDTO.getStartTime() != null) wrapper.ge("ac_time", queryDTO.getStartTime().atStartOfDay());
        if (queryDTO.getEndTime() != null) wrapper.le("ac_time", queryDTO.getEndTime().atTime(23, 59, 59));
        wrapper.orderByDesc("ac_time");
        return practiceRecordMapper.selectPage(page, wrapper);
    }

    private long countAfter(List<OjPracticeRecord> records, LocalDateTime time) {
        return records.stream().filter(r -> r.getAcTime().isAfter(time)).count();
    }

    private List<PlatformStatVO> calculatePlatformStats(List<OjPracticeRecord> records) {
        Map<Long, String> idToName = platformIdMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        Map<Long, Long> counts = records.stream().collect(Collectors.groupingBy(OjPracticeRecord::getSourceId, Collectors.counting()));
        List<PlatformStatVO> stats = new ArrayList<>();
        platformIdMap.forEach((name, id) -> {
            PlatformStatVO stat = new PlatformStatVO();
            stat.setName(name);
            stat.setCount(counts.getOrDefault(id, 0L).intValue());
            stat.setColor(getPlatformColor(name));
            stats.add(stat);
        });
        return stats;
    }

    private ChartScopeVO calculateChartScope(List<OjPracticeRecord> allRecords, int days, LocalDateTime now) {
        LocalDateTime start = now.minusDays(days);
        List<OjPracticeRecord> scopeRecords = allRecords.stream().filter(r -> r.getAcTime().isAfter(start)).collect(Collectors.toList());
        ChartScopeVO vo = new ChartScopeVO();
        int[] diffCounts = new int[10];
        for (OjPracticeRecord r : scopeRecords) {
            diffCounts[getDifficultyIndex(parseDifficulty(r.getProblemDifficulty()))]++;
        }
        List<Integer> diffList = new ArrayList<>();
        for (int c : diffCounts) diffList.add(c);
        vo.setDifficultyDistribution(diffList);
        Map<String, Integer> timeMap = new LinkedHashMap<>();
        DateTimeFormatter fmt;
        boolean byMonth = days > 31;
        if (byMonth) {
            fmt = DateTimeFormatter.ofPattern("yyyy-MM");
            for (int i = 11; i >= 0; i--) timeMap.put(now.minusMonths(i).format(fmt), 0);
        } else {
            fmt = DateTimeFormatter.ofPattern("MM-dd");
            for (int i = days - 1; i >= 0; i--) timeMap.put(now.minusDays(i).format(fmt), 0);
        }
        for (OjPracticeRecord r : scopeRecords) {
            String key = r.getAcTime().format(fmt);
            if (!byMonth && days > 31) key = r.getAcTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (timeMap.containsKey(key)) timeMap.put(key, timeMap.get(key) + 1);
        }
        vo.setTimeLabels(new ArrayList<>(timeMap.keySet()));
        vo.setTimeValues(new ArrayList<>(timeMap.values()));
        return vo;
    }

    private List<List<Object>> calculateHeatmap(List<OjPracticeRecord> records, LocalDateTime start, boolean isMaxDiff) {
        Map<String, Integer> map = new HashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (OjPracticeRecord r : records) {
            if (r.getAcTime().isBefore(start)) continue;
            String date = r.getAcTime().format(fmt);
            if (isMaxDiff) {
                int diff = parseDifficulty(r.getProblemDifficulty());
                if (diff > 0) {
                    int currentMax = map.getOrDefault(date, 0);
                    if (diff > currentMax) map.put(date, diff);
                }
            } else {
                map.put(date, map.getOrDefault(date, 0) + 1);
            }
        }
        List<List<Object>> result = new ArrayList<>();
        map.forEach((k, v) -> {
            List<Object> item = new ArrayList<>();
            item.add(k);
            item.add(v);
            result.add(item);
        });
        return result;
    }

    private int parseDifficulty(String diffStr) {
        if (diffStr == null || diffStr.trim().isEmpty() || "N/A".equalsIgnoreCase(diffStr)) return -1;
        try {
            return Integer.parseInt(diffStr);
        } catch (NumberFormatException e) {
            try {
                return (int) Double.parseDouble(diffStr);
            } catch (NumberFormatException e2) {
                String numeric = diffStr.replaceAll("[^0-9]", "");
                if (numeric.isEmpty()) return -1;
                try {
                    return Integer.parseInt(numeric);
                } catch (NumberFormatException e3) {
                    return -1;
                }
            }
        }
    }

    private int getDifficultyIndex(int diff) {
        if (diff < 0) return 0;
        if (diff < 1200) return 1;
        if (diff < 1400) return 2;
        if (diff < 1600) return 3;
        if (diff < 1900) return 4;
        if (diff < 2100) return 5;
        if (diff < 2400) return 6;
        if (diff < 2600) return 7;
        if (diff < 3000) return 8;
        return 9;
    }

    @Async
    public void refreshUserTracker(Long userId, String luoguClientId, List<String> targetPlatforms) {
        refreshUserTracker(userId, luoguClientId, targetPlatforms, true);
    }

    public void refreshUserTrackerQuietly(Long userId, List<String> targetPlatforms) {
        refreshUserTracker(userId, null, targetPlatforms, false);
    }

    private void refreshUserTracker(Long userId, String luoguClientId, List<String> targetPlatforms, boolean notifyUser) {
        OjUserAccount account = ojUserAccountMapper.selectById(userId);
        if (account == null) {
            log.error("用户 {} 未绑定OJ账号", userId);
            if (notifyUser) {
                notificationService.send(userId, 5, "CP Tracker同步失败", "未找到绑定的OJ账号信息，请先去设置页绑定。");
            }
            return;
        }

        String luoguCookie = resolveAndCacheLuoguCookie(account, luoguClientId);

        initPlatformMap();

        boolean syncAll = targetPlatforms == null || targetPlatforms.isEmpty();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (syncAll || targetPlatforms.contains("CodeForces")) {
            futures.add(CompletableFuture.runAsync(() ->
                    processPlatformSync(userId, account.getCodeforcesId(), "CodeForces", null, notifyUser), crawlerExecutor));
        }
        if (syncAll || targetPlatforms.contains("AtCoder")) {
            futures.add(CompletableFuture.runAsync(() ->
                    processPlatformSync(userId, account.getAtcoderId(), "AtCoder", null, notifyUser), crawlerExecutor));
        }
        if (syncAll || targetPlatforms.contains("NowCoder")) {
            futures.add(CompletableFuture.runAsync(() ->
                    processPlatformSync(userId, account.getNewcoderId(), "NowCoder", null, notifyUser), crawlerExecutor));
        }
        if (syncAll || targetPlatforms.contains("Luogu")) {
            if (StringUtils.hasText(luoguCookie)) {
                futures.add(CompletableFuture.runAsync(() ->
                        processPlatformSync(userId, account.getLuoguId(), "Luogu", luoguCookie, notifyUser), crawlerExecutor));
            } else {
                log.info("用户 {} 未缓存洛谷 Cookie，跳过洛谷同步", userId);
                if (notifyUser && StringUtils.hasText(account.getLuoguId())) {
                    notificationService.send(userId, 5, "CP Tracker数据同步失败", "平台 [Luogu] 缺少 Cookie，请填写有效 Cookie 后重试，原统计已保留。");
                }
            }
        }
        if (syncAll || targetPlatforms.contains("CWNU")) {
            futures.add(CompletableFuture.runAsync(() ->
                    processPlatformSync(userId, account.getCwnuojId(), "CWNU", null, notifyUser), crawlerExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        RatingRefreshRequested.publish(ratingEvents);
    }

    private String resolveAndCacheLuoguCookie(OjUserAccount account, String luoguClientId) {
        if (StringUtils.hasText(luoguClientId)) {
            String cookie = luoguClientId.trim();
            account.setLuoguClientId(cookie);
            ojUserAccountMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OjUserAccount>()
                    .eq("user_id", account.getUserId()).set("luogu_client_id", cookie));
            return cookie;
        }
        return account.getLuoguClientId();
    }

    private void processPlatformSync(Long userId, String handle, String platformName, String clientId) {
        processPlatformSync(userId, handle, platformName, clientId, true);
    }

    private void processPlatformSync(Long userId, String handle, String platformName, String clientId, boolean notifyUser) {
        if (handle == null || handle.isEmpty()) {
            return;
        }

        try {
            int added;
            if (EXTERNAL_PLATFORMS.contains(platformName)) {
                // Serialize fetch and save, so an older concurrent crawl cannot replace a newer one.
                synchronized (externalSyncLocks[Math.floorMod(Objects.hash(userId, platformName), externalSyncLocks.length)]) {
                    added = fetchAndSave(userId, handle.trim(), platformName, clientId);
                }
            } else {
                added = fetchAndSave(userId, handle, platformName, clientId);
            }
            if (notifyUser && added >= 0) {
                notificationService.send(userId, 5, "CP Tracker数据同步完成", "平台 [" + platformName + "] 数据同步完成，新增 " + added + " 题。");
            }
        } catch (Exception e) {
            log.error("用户 {} 同步 {} 失败: {}", userId, platformName,
                    e instanceof TrackerSyncException ? e.getMessage() : e.getClass().getSimpleName());
            if (notifyUser) {
                String detail = e instanceof TrackerSyncException ? e.getMessage() : "请稍后重试或联系管理员";
                notificationService.send(userId, 5, "CP Tracker数据同步失败", "平台 [" + platformName + "] 数据同步遇到问题: " + detail);
            }
        }
    }

    private int fetchAndSave(Long userId, String handle, String platformName, String clientId) {
        if (handle == null || handle.isEmpty()) return 0;

        Long sourceId = platformIdMap.get(platformName);
        if (sourceId == null) {
            log.warn("平台 {} 未在数据库 oj_platform 表中配置", platformName);
            if (EXTERNAL_PLATFORMS.contains(platformName)) throw new TrackerSyncException("未配置该平台，原统计已保留");
            return 0;
        }

        try {
            long lastTime = 0;

            Map<String, Object> body = crawlerLocalRunner.crawlSubmissions(platformName, handle, lastTime, clientId);

            if (body == null || !body.containsKey("submissions")) {
                if (EXTERNAL_PLATFORMS.contains(platformName)) throw new TrackerSyncException("平台返回了无效数据，原统计已保留");
                return 0;
            }

            List<Map<String, Object>> rawList = (List<Map<String, Object>>) body.get("submissions");

            if (EXTERNAL_PLATFORMS.contains(platformName)) {
                if (rawList == null || !Boolean.TRUE.equals(body.get("complete"))) {
                    throw new TrackerSyncException("平台记录尚未完整获取，原统计已保留");
                }
                return trackerSnapshotService.reconcile(userId, sourceId, platformName, handle, rawList);
            }

            List<OjPracticeRecord> existingList = practiceRecordMapper.selectSolvedProblems(userId, sourceId);
            Map<String, OjPracticeRecord> existingMap = existingList != null
                    ? existingList.stream().collect(Collectors.toMap(OjPracticeRecord::getProblemId, r -> r, (oldVal, newVal) -> oldVal))
                    : new HashMap<>();

            List<OjPracticeRecord> newRecords = new ArrayList<>();
            List<OjPracticeRecord> updateRecords = new ArrayList<>();
            Set<String> processedProblemIds = new HashSet<>();

            DecimalFormat df = new DecimalFormat("#.##");

            for (Map<String, Object> subMap : rawList) {
                String problemId = (String) subMap.get("problem_id");

                if (processedProblemIds.contains(problemId)) {
                    continue;
                }

                String difficultyStr = "N/A";
                Object diffObj = subMap.get("difficulty");
                if (diffObj instanceof Number) {
                    double dVal = ((Number) diffObj).doubleValue();
                    if (dVal > 0) {
                        difficultyStr = df.format(dVal);
                    }
                } else {
                    Object rawDiffObj = subMap.get("raw_difficulty");
                    if (rawDiffObj != null) {
                        difficultyStr = String.valueOf(rawDiffObj);
                    }
                }

                if (existingMap.containsKey(problemId)) {
                    OjPracticeRecord existing = existingMap.get(problemId);

                    existing.setProblemDifficulty(difficultyStr);
                    existing.setProblemName((String) subMap.get("title"));

                    if (subMap.containsKey("tags") && subMap.get("tags") instanceof List) {
                        List<String> tags = (List<String>) subMap.get("tags");
                        existing.setProblemTag(String.join(",", tags));
                    }

                    updateRecords.add(existing);
                    processedProblemIds.add(problemId);
                    continue;
                }

                OjPracticeRecord record = new OjPracticeRecord();
                record.setUserId(userId);
                record.setSourceId(sourceId);
                record.setProblemId(problemId);
                record.setProblemName((String) subMap.get("title"));
                record.setProblemDifficulty(difficultyStr);

                Object timeObj = subMap.get("solve_time");
                long solveTime = (timeObj instanceof Number) ? ((Number) timeObj).longValue() : 0L;
                record.setAcTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(solveTime), ZoneId.systemDefault()));

                if (subMap.containsKey("tags") && subMap.get("tags") instanceof List) {
                    record.setProblemTag(String.join(",", (List<String>) subMap.get("tags")));
                }

                newRecords.add(record);
                existingMap.put(problemId, record);
                processedProblemIds.add(problemId);
            }

            if (!newRecords.isEmpty()) {
                for (OjPracticeRecord record : newRecords) {
                    practiceRecordMapper.insert(record);
                }
                log.info("用户 {} 在 {} 平台新增 {} 题", userId, platformName, newRecords.size());
            }

            if (!updateRecords.isEmpty()) {
                for (OjPracticeRecord record : updateRecords) {
                    practiceRecordMapper.updateById(record);
                }
                log.info("用户 {} 在 {} 平台更新 {} 题难度/标签信息", userId, platformName, updateRecords.size());
            }

            return newRecords.size();

        } catch (Exception e) {
            log.error("爬取失败: User={}, Platform={}, Error={}", userId, platformName, e.getClass().getSimpleName());
            if (EXTERNAL_PLATFORMS.contains(platformName)) {
                if (e instanceof TrackerSyncException failure) throw failure;
                throw new TrackerSyncException("请稍后重试或联系管理员");
            }
            return 0;
        }
    }

    private boolean isDifficultyInvalid(String diff) {
        if (diff == null || "N/A".equals(diff) || "null".equals(diff) || diff.trim().isEmpty()) return true;
        try {
            double d = Double.parseDouble(diff);
            return d <= 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private void initPlatformMap() {
        if (platformIdMap.isEmpty()) {
            List<OjPlatform> platforms = ojPlatformMapper.selectList(null);
            if (platforms != null) {
                for (OjPlatform p : platforms) {
                    platformIdMap.put(p.getOjName(), p.getOjId());
                }
            }
        }
    }

    private void updateUserPracticeRating(Long userId) {
        Long totalCount = practiceRecordMapper.selectCount(new QueryWrapper<OjPracticeRecord>().eq("user_id", userId));
        int newPracticeRating = totalCount.intValue() * 2;
        RatingComposition composition = ratingCompositionMapper.selectById(userId);
        if (composition == null) {
            composition = new RatingComposition();
            composition.setUserId(userId);
            composition.setRatingPractice(newPracticeRating);
            composition.setRatingAll(newPracticeRating);
            ratingCompositionMapper.insert(composition);
        } else {
            int diff = newPracticeRating - composition.getRatingPractice();
            if (diff != 0) {
                composition.setRatingPractice(newPracticeRating);
                composition.setRatingAll(composition.getRatingContest() + composition.getRatingCertificate() + newPracticeRating);
                ratingCompositionMapper.updateById(composition);
                RatingLog logEntry = new RatingLog();
                logEntry.setUserId(userId);
                logEntry.setChangeValue(diff);
                logEntry.setSourceType(3);
                logEntry.setDescription("刷题数量更新，当前总题数: " + totalCount);
                logEntry.setCreateTime(LocalDateTime.now());
                ratingLogMapper.insert(logEntry);
            }
        }
    }

    private String getPlatformColor(String platform) {
        switch (platform) {
            case "CodeForces":
                return "#FFD700";
            case "AtCoder":
                return "#000000";
            case "Luogu":
                return "#E74C3C";
            case "NowCoder":
                return "#2ECC71";
            case "CWNU":
                return "#3498DB";
            default:
                return "#95A5A6";
        }
    }
}
