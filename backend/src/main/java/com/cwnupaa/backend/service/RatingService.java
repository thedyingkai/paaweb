package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.BoardVO;
import com.cwnupaa.backend.vo.LeaderboardVO;
import com.cwnupaa.backend.vo.RankItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RatingService {
    @Autowired
    private ExternalBoardService externalBoardService;
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private ContestCategoryMapper contestCategoryMapper;
    @Autowired
    private RatingCompositionMapper ratingCompositionMapper;
    @Autowired
    private RatingLogMapper ratingLogMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private RatingTermRecordMapper ratingTermRecordMapper;
    @Autowired
    private RatingMonthLogMapper ratingMonthLogMapper;
    @Autowired
    private OjPracticeRecordMapper ojPracticeRecordMapper;
    @Autowired
    private CertificateGrantMapper certificateGrantMapper;
    @Autowired
    private CertificateLevelMapper certificateLevelMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CertificateTypeMapper certificateTypeMapper;
    @Autowired
    private CertificateVerifyMapper certificateVerifyMapper;
    @Autowired
    private CertificateMemberMapper certificateMemberMapper;
    @Autowired
    private CertificateIssueMapper certificateIssueMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired private CertificateIdentityService certificateIdentityService;
    @Autowired private MemberAwardRatingService memberAwardRatingService;

    @Transactional(rollbackFor = Exception.class)
    public synchronized void refreshAllRatings() {
        List<Term> allTerms = termMapper.selectList(new QueryWrapper<Term>()
                .le("start_time", LocalDate.now())
                .orderByDesc("start_time")
                .last("LIMIT 10"));
        Collections.reverse(allTerms);

        List<Users> allUsers = userMapper.selectList(null);
        List<Long> allUserIds = allUsers.stream().map(Users::getUserId).collect(Collectors.toList());

        // 学校（大学）名称 + 学号 -> userId，用于按“学校+学号”匹配证书获奖人（不同学校学号可重复）
        Map<Long, String> userSchoolNames = new HashMap<>();
        Map<Long, Users> certificateUsers = allUsers.stream().collect(Collectors.toMap(Users::getUserId, Function.identity()));
        {
            Set<Long> majorIds = allUsers.stream().map(Users::getMajorId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Major> majorMap = majorIds.isEmpty() ? Map.of()
                    : majorMapper.selectBatchIds(majorIds).stream().collect(Collectors.toMap(Major::getMajorId, m -> m));
            Set<Long> schoolIds = majorMap.values().stream().map(Major::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, School> schoolMap = schoolIds.isEmpty() ? Map.of()
                    : schoolMapper.selectBatchIds(schoolIds).stream().collect(Collectors.toMap(School::getSchoolId, s -> s));
            Set<Long> universityIds = schoolMap.values().stream().map(School::getUniversityId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, University> universityMap = universityIds.isEmpty() ? Map.of()
                    : universityMapper.selectBatchIds(universityIds).stream().collect(Collectors.toMap(University::getUniversityId, u -> u));
            for (Users u : allUsers) {
                if (u.getNumberId() == null || u.getNumberId().isEmpty()) continue;
                Major major = u.getMajorId() == null ? null : majorMap.get(u.getMajorId());
                School school = major == null || major.getSchoolId() == null ? null : schoolMap.get(major.getSchoolId());
                University uni = school == null || school.getUniversityId() == null ? null : universityMap.get(school.getUniversityId());
                if (uni != null && StringUtils.hasText(uni.getUniversityName())) {
                    userSchoolNames.put(u.getUserId(), uni.getUniversityName());
                }
            }
        }

        Map<Long, ContestCategory> categoryMap = contestCategoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ContestCategory::getCategoryId, Function.identity()));

        List<CertificateLevel> certificateLevels = certificateLevelMapper.selectList(null);
        Map<Long, CertificateLevel> levelMap = certificateLevels.stream()
                .collect(Collectors.toMap(CertificateLevel::getLevelId, Function.identity()));
        Map<Long, Integer> levelScoreMap = certificateLevels.stream()
                .collect(Collectors.toMap(CertificateLevel::getLevelId, level -> level.getBaseRating() == null ? 0 : level.getBaseRating()));

        Map<Long, CertificateType> certTypeMap = certificateTypeMapper.selectList(null).stream()
                .collect(Collectors.toMap(CertificateType::getCertificateTypeId, Function.identity()));

        Map<Long, List<CertificateGrant>> grantsByTerm = new HashMap<>();
        List<CertificateGrant> allGrants = certificateGrantMapper.selectList(null);
        Set<Long> issueIds = allGrants.stream().map(CertificateGrant::getIssueId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CertificateIssue> issueMap = issueIds.isEmpty() ? Map.of()
                : certificateIssueMapper.selectBatchIds(issueIds).stream().collect(Collectors.toMap(CertificateIssue::getIssueId, Function.identity()));
        for (CertificateGrant grant : allGrants) {
            Users owner = certificateUsers.get(grant.getUserId());
            if (!CertificateIdentityService.matches(owner, userSchoolNames.get(grant.getUserId()),
                    grant.getSchoolName(), grant.getUserAccount(), grant.getRealName())) continue;
            CertificateType type = certTypeMap.get(grant.getCertificateTypeId());
            Long scope = allTerms.isEmpty() ? 0L : type == null ? null : type.getTerm();
            if (type != null && scope != null) {
                grantsByTerm.computeIfAbsent(scope, k -> new ArrayList<>()).add(grant);
            }
        }

        Map<Long, List<CertificateVerify>> verifiesByTerm = certificateVerifyMapper.selectList(
                new QueryWrapper<CertificateVerify>().eq("status", "approved")
        ).stream().filter(v -> allTerms.isEmpty() || v.getTerm() != null)
                .collect(Collectors.groupingBy(v -> allTerms.isEmpty() ? 0L : v.getTerm()));

        Map<Long, RatingComposition> runningRatings = new HashMap<>();
        for (Long uid : allUserIds) {
            RatingComposition rc = new RatingComposition();
            rc.setUserId(uid);
            rc.setRatingContest(0);
            rc.setRatingCertificate(0);
            rc.setRatingPractice(0);
            rc.setRatingAll(0);
            runningRatings.put(uid, rc);
        }

        LocalDateTime prevTermEndTime = LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime now = LocalDateTime.now();
        var memberAwards = memberAwardRatingService.approvedAwards();

        if (allTerms.isEmpty()) {
            var credited = new HashSet<String>();
            calculateContestScoreInScope(prevTermEndTime, now, runningRatings, categoryMap);
            calculateCertificateScoreInTerm(0L, runningRatings, grantsByTerm, verifiesByTerm,
                    levelScoreMap, levelMap, certificateLevels, certTypeMap, issueMap, credited);
            calculateMemberAwardScore(prevTermEndTime, now, runningRatings, memberAwards, credited);
            calculatePracticeScoreInScope(prevTermEndTime, now, runningRatings);
        }

        for (Term term : allTerms) {
            LocalDateTime currentTermEndTime = (term.getEndTime() == null) ? LocalDateTime.MAX : term.getEndTime().atTime(23, 59, 59);
            if (currentTermEndTime.isAfter(now)) currentTermEndTime = now;

            for (RatingComposition rc : runningRatings.values()) {
                rc.setRatingContest((int) (rc.getRatingContest() * 0.20));
                rc.setRatingCertificate((int) (rc.getRatingCertificate() * 0.20));
                rc.setRatingPractice((int) (rc.getRatingPractice() * 0.20));
            }

            calculateContestScoreInScope(prevTermEndTime, currentTermEndTime, runningRatings, categoryMap);
            var credited = new HashSet<String>();
            calculateCertificateScoreInTerm(term.getTermId(), runningRatings, grantsByTerm, verifiesByTerm,
                    levelScoreMap, levelMap, certificateLevels, certTypeMap, issueMap, credited);
            calculateMemberAwardScore(prevTermEndTime, currentTermEndTime, runningRatings, memberAwards, credited);
            calculatePracticeScoreInScope(prevTermEndTime, currentTermEndTime, runningRatings);

            saveTermRecord(term.getTermId(), runningRatings);

            prevTermEndTime = currentTermEndTime;
        }

        saveComposition(runningRatings);
    }

    private void calculateMemberAwardScore(LocalDateTime start,LocalDateTime end,Map<Long,RatingComposition> ratings,
                                           List<MemberAwardRatingService.Award> awards,Set<String> credited) {
        for (var award : awards) {
            var rating=ratings.get(award.userId());
            if (rating==null || award.date()==null) continue;
            var date=award.date().atStartOfDay();
            if (!date.isAfter(start) || date.isAfter(end)) continue;
            if (award.key()!=null && !credited.add(award.key())) continue;
            rating.setRatingCertificate(rating.getRatingCertificate()+award.points());
        }
    }

    private void calculateContestScoreInScope(LocalDateTime start, LocalDateTime end, Map<Long, RatingComposition> ratings, Map<Long, ContestCategory> categoryMap) {
        List<Event> events = eventMapper.selectList(new QueryWrapper<Event>().eq("status", 1));
        for (Event parent : events) {
            Long boardId = externalBoardService.ratingBoardId(parent.getEventId());
            if (boardId == null) continue;
            Event event = externalBoardService.boardEvent(boardId);
            LocalDateTime finished = event.getActualEndTime();
            if (finished == null || !finished.isAfter(start) || finished.isAfter(end) || finished.isAfter(LocalDateTime.now())) continue;
            calculateEventScoresAndAdd(event, boardId, ratings, categoryMap);
        }
    }

    private void calculateEventScoresAndAdd(Event event, Long boardId, Map<Long, RatingComposition> ratings, Map<Long, ContestCategory> categoryMap) {
        ContestCategory category = categoryMap.get(event.getCategoryId());
        if (category == null) return;

        boolean isSchoolLevel = false;
        if (category.getLevel() != null) {
            isSchoolLevel = "school".equalsIgnoreCase(category.getLevel()) || "national".equalsIgnoreCase(category.getLevel());
        } else if (category.getName() != null) {
            isSchoolLevel = category.getName().contains("校");
        }
        double baseScore = isSchoolLevel ? 20.0 : 10.0;
        double championBonus = isSchoolLevel ? 680.0 : 290.0;

        String type = event.getType() != null ? event.getType() : "XCPC";
        String scope = "GPLT".equalsIgnoreCase(type) ? "personal" : "all";

        BoardVO board = externalBoardService.getBoardDetail(boardId, null, scope, "all", null);
        if (board == null || board.getRows() == null) return;

        List<RankItemVO> participants = board.getRows().stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (participants.isEmpty()) return;

        Map<String, List<RankItemVO>> groupMap = new HashMap<>();
        if ("GPLT".equalsIgnoreCase(type) || "LANQIAO".equalsIgnoreCase(type)) {
            for (RankItemVO item : participants) {
                String group = item.getGroup() != null ? item.getGroup() : "Default";
                groupMap.computeIfAbsent(group, k -> new ArrayList<>()).add(item);
            }
        } else {
            groupMap.put("Default", participants);
        }

        for (List<RankItemVO> groupParticipants : groupMap.values()) {
            groupParticipants.sort((a, b) -> {
                if ("XCPC".equalsIgnoreCase(type)) {
                    int sA = a.getSolved() != null ? a.getSolved() : 0;
                    int sB = b.getSolved() != null ? b.getSolved() : 0;
                    if (sA != sB) return sB - sA;
                    long pA = a.getPenalty() != null ? a.getPenalty() : 0;
                    long pB = b.getPenalty() != null ? b.getPenalty() : 0;
                    return Long.compare(pA, pB);
                } else {
                    int scA = a.getTotalScore() != null ? a.getTotalScore() : 0;
                    int scB = b.getTotalScore() != null ? b.getTotalScore() : 0;
                    return scB - scA;
                }
            });

            int N = groupParticipants.size();
            int currentRank = 1;

            for (int i = 0; i < N; i++) {
                RankItemVO item = groupParticipants.get(i);
                if (i > 0) {
                    RankItemVO prev = groupParticipants.get(i - 1);
                    boolean isTie;
                    if ("XCPC".equalsIgnoreCase(type)) {
                        isTie = Objects.equals(item.getSolved(), prev.getSolved()) && Objects.equals(item.getPenalty(), prev.getPenalty());
                    } else {
                        isTie = Objects.equals(item.getTotalScore(), prev.getTotalScore());
                    }
                    if (!isTie) currentRank = i + 1;
                } else {
                    currentRank = 1;
                }

                double rankWeight = (double) (N - currentRank + 1) / N;
                int score = (int) Math.round(baseScore + rankWeight * championBonus);

                List<Long> memberIds = new ArrayList<>();
                if ("personal".equals(scope) || event.getIsTeam() == null || event.getIsTeam() == 0) {
                    memberIds.add(item.getItemId());
                } else {
                    memberIds.addAll(item.getMemberIds() != null ? item.getMemberIds() : Collections.emptyList());
                }

                for (Long uid : memberIds) {
                    RatingComposition rc = ratings.get(uid);
                    if (rc != null) {
                        rc.setRatingContest(rc.getRatingContest() + score);
                    }
                }
            }
        }
    }

    private void calculateCertificateScoreInTerm(Long termId, Map<Long, RatingComposition> ratings,
                                                 Map<Long, List<CertificateGrant>> grantsByTerm,
                                                 Map<Long, List<CertificateVerify>> verifiesByTerm,
                                                 Map<Long, Integer> levelScoreMap,
                                                 Map<Long, CertificateLevel> levelMap,
                                                 List<CertificateLevel> certificateLevels,
                                                 Map<Long, CertificateType> certTypeMap,
                                                 Map<Long, CertificateIssue> issueMap) {
        calculateCertificateScoreInTerm(termId,ratings,grantsByTerm,verifiesByTerm,levelScoreMap,levelMap,certificateLevels,certTypeMap,issueMap,new HashSet<>());
    }

    private void calculateCertificateScoreInTerm(Long termId,Map<Long,RatingComposition> ratings,
            Map<Long,List<CertificateGrant>> grantsByTerm,Map<Long,List<CertificateVerify>> verifiesByTerm,
            Map<Long,Integer> levelScoreMap,Map<Long,CertificateLevel> levelMap,List<CertificateLevel> certificateLevels,
            Map<Long,CertificateType> certTypeMap,Map<Long,CertificateIssue> issueMap,Set<String> credited) {

        List<CertificateGrant> grants = grantsByTerm.getOrDefault(termId, Collections.emptyList());
        for (CertificateGrant grant : grants) {
            CertificateType type = certTypeMap.get(grant.getCertificateTypeId());
            if (type!=null && type.getTime()!=null && type.getTime().isAfter(LocalDate.now())) continue;
            if (type != null && !Boolean.FALSE.equals(type.getRatingEnabled())) {
                // For official grants, the user might be registered or unregistered (stored in userAccount)
                // But RatingService only calculates for registered users in 'ratings' map.
                // So we only care if grant.getUserId() is present and exists in ratings map.
                
                if (grant.getUserId() != null) {
                    RatingComposition rc = ratings.get(grant.getUserId());
                    if (rc != null) {
                        CertificateIssue issue = grant.getIssueId() == null ? null : issueMap.get(grant.getIssueId());
                        int points=resolveOfficialCertificateScore(type, issue, levelScoreMap, levelMap, certificateLevels);
                        CertificateLevel level=resolveOfficialCertificateLevel(type,issue,levelMap,certificateLevels);
                        String key=level==null?null:MemberAwardRatingService.key(grant.getUserId(),type.getTypeName(),type.getTime(),level.getLevelType(),level.getRankType());
                        if (points>0 && (key==null || credited.add(key))) rc.setRatingCertificate(rc.getRatingCertificate()+points);
                    }
                }
            }
        }

        List<CertificateVerify> verifies = verifiesByTerm.getOrDefault(termId, Collections.emptyList());
        
        // Batch query all members for these verifies to improve performance
        Map<Long, List<CertificateMember>> membersMap = new HashMap<>();
        if (!verifies.isEmpty()) {
            List<Long> verifyIds = verifies.stream().map(CertificateVerify::getCertificateVerifyId).collect(Collectors.toList());
            if (!verifyIds.isEmpty()) {
                 List<CertificateMember> allMembers = certificateMemberMapper.selectList(new QueryWrapper<CertificateMember>().in("certificate_verify_id", verifyIds));
                 membersMap = allMembers.stream().collect(Collectors.groupingBy(CertificateMember::getCertificateVerifyId));
            }
        }

        for (CertificateVerify verify : verifies) {
            if (verify.getTime()!=null && verify.getTime().isAfter(LocalDate.now())) continue;
            int score = levelScoreMap.getOrDefault(verify.getLevelId(), 0);
            Set<Long> scoredUserIds = new HashSet<>();
            // The applicant is tied to the original authenticated submission, independent of team snapshots.
            if (certificateIdentityService.verifiedUser(verify.getUserId()) != null) scoredUserIds.add(verify.getUserId());
            for (CertificateMember member : membersMap.getOrDefault(verify.getCertificateVerifyId(), List.of())) {
                if (member.getUserId() == null) continue;
                Users owner = certificateIdentityService.verifiedUser(member.getUserId());
                if (certificateIdentityService.ownsMember(owner, member)) scoredUserIds.add(member.getUserId());
            }
            for (Long userId : scoredUserIds) {
                RatingComposition composition = ratings.get(userId);
                CertificateLevel level=levelMap.get(verify.getLevelId());
                String key=level==null?null:MemberAwardRatingService.key(userId,verify.getEventName(),verify.getTime(),level.getLevelType(),level.getRankType());
                if (composition != null && (key==null || credited.add(key))) composition.setRatingCertificate(composition.getRatingCertificate() + score);
            }
        }
    }

    private int resolveOfficialCertificateScore(CertificateType type,
                                                CertificateIssue issue,
                                                Map<Long, Integer> levelScoreMap,
                                                Map<Long, CertificateLevel> levelMap,
                                                List<CertificateLevel> certificateLevels) {
        if (issue != null && "revoked".equals(issue.getStatus())) return 0;
        CertificateLevel level=resolveOfficialCertificateLevel(type,issue,levelMap,certificateLevels);
        return level==null?levelScoreMap.getOrDefault(type.getTypeId(),0):level.getBaseRating()==null?0:level.getBaseRating();
    }

    private CertificateLevel resolveOfficialCertificateLevel(CertificateType type,CertificateIssue issue,
                Map<Long,CertificateLevel> levelMap,List<CertificateLevel> certificateLevels) {
        String levelScope = null;
        if (StringUtils.hasText(type.getLevelScope())) levelScope = type.getLevelScope();
        if (!StringUtils.hasText(levelScope) && issue != null && StringUtils.hasText(issue.getLevelScope())) levelScope = issue.getLevelScope();
        CertificateLevel templateLevel = levelMap.get(type.getTypeId());
        if (!StringUtils.hasText(levelScope) && templateLevel != null) levelScope = templateLevel.getLevelType();

        String awardName = issue == null ? null : issue.getAwardName();
        if (StringUtils.hasText(levelScope) && StringUtils.hasText(awardName)) {
            for (CertificateLevel level : certificateLevels) {
                if (levelScope.equals(level.getLevelType()) && awardMatches(level, awardName)) {
                    return level;
                }
            }
        }
        return levelMap.get(type.getTypeId());
    }

    private boolean awardMatches(CertificateLevel level, String awardName) {
        String award = normalizeAwardText(awardName);
        String rank = normalizeAwardText(level.getRankType());
        String name = normalizeAwardText(level.getName());
        if (!award.isEmpty()
                && ((!rank.isEmpty() && (rank.contains(award) || award.contains(rank)))
                || (!name.isEmpty() && (name.contains(award) || award.contains(name))))) {
            return true;
        }
        return containsAny(award, rank, "特等", "一等", "二等", "三等", "优秀", "其他")
                || (award.contains("金") && rank.contains("金"))
                || (award.contains("银") && rank.contains("银"))
                || (award.contains("铜") && rank.contains("铜"));
    }

    private boolean containsAny(String award, String rank, String... tokens) {
        for (String token : tokens) {
            if (award.contains(token) && rank.contains(token)) return true;
        }
        return false;
    }

    private String normalizeAwardText(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.replaceAll("[\\s（）()【】\\[\\]、,，/\\\\-]", "");
    }

    private void calculatePracticeScoreInScope(LocalDateTime start, LocalDateTime end, Map<Long, RatingComposition> ratings) {
        List<Map<String, Object>> dailyStats = ojPracticeRecordMapper.selectAllUserDailyStatsAfter(start,end);

        for (Map<String, Object> stat : dailyStats) {
            Long uid = ((Number) stat.get("user_id")).longValue();
            Long count = ((Number) stat.get("solved_count")).longValue();
            String dateStr = (String) stat.get("date_str");

            LocalDateTime recordDate = LocalDate.parse(dateStr).atStartOfDay();

            if (recordDate.isAfter(start) && !recordDate.isAfter(end)) {
                RatingComposition rc = ratings.get(uid);
                if (rc != null && count > 0) {
                    int score = (int) Math.round(Math.log10(count + 1) * 10);
                    rc.setRatingPractice(rc.getRatingPractice() + score);
                }
            }
        }
    }

    private void saveTermRecord(Long termId, Map<Long, RatingComposition> ratings) {
        List<RatingTermRecord> existingRecords = ratingTermRecordMapper.selectList(new QueryWrapper<RatingTermRecord>().eq("term_id", termId));
        Map<Long, RatingTermRecord> existingMap = existingRecords.stream()
                .collect(Collectors.toMap(RatingTermRecord::getUserId, Function.identity()));

        for (RatingComposition rc : ratings.values()) {
            int total = rc.getRatingContest() + rc.getRatingCertificate() + rc.getRatingPractice();

            RatingTermRecord record = existingMap.get(rc.getUserId());

            if (record == null) {
                record = new RatingTermRecord();
                record.setTermId(termId);
                record.setUserId(rc.getUserId());
                record.setRatingContest(rc.getRatingContest());
                record.setRatingCertificate(rc.getRatingCertificate());
                record.setRatingPractice(rc.getRatingPractice());
                record.setRatingAll(total);
                ratingTermRecordMapper.insert(record);
            } else {
                record.setRatingContest(rc.getRatingContest());
                record.setRatingCertificate(rc.getRatingCertificate());
                record.setRatingPractice(rc.getRatingPractice());
                record.setRatingAll(total);
                ratingTermRecordMapper.updateById(record);
            }
        }
    }

    private void saveComposition(Map<Long, RatingComposition> ratings) {
        List<RatingComposition> allExist = ratingCompositionMapper.selectList(null);
        Map<Long, RatingComposition> existMap = allExist.stream()
                .collect(Collectors.toMap(RatingComposition::getUserId, Function.identity()));

        for (RatingComposition rc : ratings.values()) {
            rc.setRatingAll(rc.getRatingContest() + rc.getRatingCertificate() + rc.getRatingPractice());

            RatingComposition exist = existMap.get(rc.getUserId());
            if (exist != null) {
                exist.setRatingContest(rc.getRatingContest());
                exist.setRatingCertificate(rc.getRatingCertificate());
                exist.setRatingPractice(rc.getRatingPractice());
                exist.setRatingAll(rc.getRatingAll());
                ratingCompositionMapper.updateById(exist);
            } else {
                ratingCompositionMapper.insert(rc);
            }
        }
    }

    public void settleContestRating(Long eventId) {
        refreshAllRatings();
    }

    public void addCertificateRating(Long userId, CertificateLevel level, String certName) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void snapshotMonthly() {
        LocalDate now = LocalDate.now();
        List<RatingComposition> all = ratingCompositionMapper.selectList(null);
        for (RatingComposition rc : all) {
            RatingMonthLog log = new RatingMonthLog();
            log.setUserId(rc.getUserId());
            log.setMonth(now.atStartOfDay());
            log.setRatingContest(rc.getRatingContest());
            log.setRatingCertificate(rc.getRatingCertificate());
            log.setRatingPractice(rc.getRatingPractice());
            log.setRatingAll(rc.getRatingAll());
            ratingMonthLogMapper.insert(log);
        }
    }

    public void handleTermTransition() {
    }

    public List<LeaderboardVO> getLeaderboard(Long termId) {
        List<LeaderboardVO> result = new ArrayList<>();
        Map<Long, Users> userMap = userMapper.selectList(null).stream().collect(Collectors.toMap(Users::getUserId, u -> u));

        if (termId == null) {
            List<RatingComposition> currentRatings = ratingCompositionMapper.selectList(new QueryWrapper<RatingComposition>().orderByDesc("rating_all"));
            int rank = 1;
            for (RatingComposition rc : currentRatings) {
                Users user = userMap.get(rc.getUserId());
                if (user != null && Integer.valueOf(1).equals(user.getIsEnrolledStu())) {
                    result.add(convert(rank++, rc.getUserId(), rc.getRatingAll(), rc.getRatingContest(), rc.getRatingCertificate(), rc.getRatingPractice(), user));
                }
            }
        } else {
            List<RatingTermRecord> historyRatings = ratingTermRecordMapper.selectList(new QueryWrapper<RatingTermRecord>().eq("term_id", termId).orderByDesc("rating_all"));
            int rank = 1;
            for (RatingTermRecord rr : historyRatings) {
                Users user = userMap.get(rr.getUserId());
                if (user != null && Integer.valueOf(1).equals(user.getIsEnrolledStu())) {
                    result.add(convert(rank++, rr.getUserId(), rr.getRatingAll(), rr.getRatingContest(), rr.getRatingCertificate(), rr.getRatingPractice(), user));
                }
            }
        }
        return result;
    }

    private LeaderboardVO convert(Integer rank, Long userId, Integer total, Integer contest, Integer cert, Integer practice, Users user) {
        LeaderboardVO vo = new LeaderboardVO();
        vo.setRank(rank);
        vo.setUserId(userId);
        vo.setRatingAll(total != null ? total : 0);
        vo.setRatingContest(contest != null ? contest : 0);
        vo.setRatingCertificate(cert != null ? cert : 0);
        vo.setRatingPractice(practice != null ? practice : 0);

        if (user != null) {
            vo.setNickname(user.getUserName());
            vo.setRealName(user.getRealName());
            vo.setRole(user.getRole());
            Long fileId = user.getAvatarFileId();
            vo.setAvatar(fileId != null ? "/api/file/preview/" + fileId : null);
            vo.setBio(user.getIntroduce());
        } else {
            vo.setNickname("未知用户");
        }
        return vo;
    }
}
