package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.dto.ExternalBoardConfigDTO;
import com.cwnupaa.backend.dto.ResolverSettingsDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.BoardStatsVO;
import com.cwnupaa.backend.vo.BoardSubmissionVO;
import com.cwnupaa.backend.vo.BoardVO;
import com.cwnupaa.backend.vo.ExternalBoardSummaryVO;
import com.cwnupaa.backend.vo.EventSignupVO;
import com.cwnupaa.backend.vo.RankItemVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class ExternalBoardService {
    private static final Set<String> LANQIAO_ZERO_VERDICTS = Set.of("CE", "COMPILE ERROR", "COMPILATION ERROR", "WAITING", "QUEUEING", "JUDGING", "COMPILING", "PENDING");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private ExternalBoardMapper externalBoardMapper;
    @Autowired
    private EventProblemMapper eventProblemMapper;
    @Autowired
    private SubmitRecordMapper submitRecordMapper;
    @Autowired
    private SignupTeamMapper signupTeamMapper;
    @Autowired
    private SignupTeamDetailMapper signupTeamDetailMapper;
    @Autowired
    private SignupPersonalMapper signupPersonalMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ResolverPhotoService resolverPhotoService;
    @Autowired
    private ResolverEngineService resolverEngineService;
    @Autowired private UniversityMapper universityMapper;

    private ExternalBoard requireBoard(Long boardId) {
        ExternalBoard board = boardId == null ? null : externalBoardMapper.selectById(boardId);
        if (board == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "外榜不存在或已删除");
        return board;
    }

    private void lockBoard(Long boardId) {
        if (boardId == null || externalBoardMapper.lockById(boardId) == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "外榜不存在或已删除");
        }
    }

    public Event boardEvent(Long boardId) {
        ExternalBoard board = requireBoard(boardId);
        Event parent = eventMapper.selectById(board.getEventId());
        if (parent == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "赛事不存在");
        Event event = new Event();
        BeanUtils.copyProperties(parent, event);
        event.setExternalOpen(board.getExternalOpen());
        event.setUnfrozen(board.getUnfrozen());
        event.setActualStartTime(board.getActualStartTime());
        event.setActualEndTime(board.getActualEndTime());
        event.setFreezeTime(board.getFreezeTime());
        event.setMedalConfig(board.getMedalConfig());
        return event;
    }

    private String validateBoardName(String name) {
        if (!StringUtils.hasText(name) || name.trim().length() > 200) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "外榜场次须为 1-200 个字符");
        }
        return name.trim();
    }

    @Transactional
    public ExternalBoardConfigDTO createBoard(Long eventId, String name) {
        Event event = eventId == null ? null : eventMapper.selectById(eventId);
        if (event == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "赛事不存在");
        ExternalBoard board = new ExternalBoard();
        board.setEventId(eventId);
        board.setName(validateBoardName(name));
        board.setExternalOpen(false);
        board.setUnfrozen("GPLT".equals(event.getType()) || "LANQIAO".equals(event.getType()));
        board.setMedalConfig("{}");
        externalBoardMapper.insert(board);
        return getConfig(board.getBoardId());
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        lockBoard(boardId);
        // Foreign keys cascade only this board's problems and submissions.
        externalBoardMapper.deleteById(boardId);
    }

    public ExternalBoardSummaryVO boardInfo(Long boardId) {
        return listBoards(null, true, null, boardId).stream().findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "外榜不存在或未开放"));
    }

    public List<ExternalBoardSummaryVO> listBoards(Long eventId, boolean publicOnly, String keyword) {
        return listBoards(eventId, publicOnly, keyword, null);
    }

    private List<ExternalBoardSummaryVO> listBoards(Long eventId, boolean publicOnly, String keyword, Long boardId) {
        QueryWrapper<ExternalBoard> query = new QueryWrapper<>();
        if (eventId != null) query.eq("event_id", eventId);
        if (boardId != null) query.eq("board_id", boardId);
        if (publicOnly) query.eq("is_external_open", true);
        query.orderByDesc("board_id");
        List<ExternalBoard> boards = externalBoardMapper.selectList(query);
        if (boards.isEmpty()) return List.of();
        Map<Long, Event> events = eventMapper.selectBatchIds(boards.stream().map(ExternalBoard::getEventId).distinct().toList())
                .stream().collect(Collectors.toMap(Event::getEventId, e -> e));
        Map<Long, Long> counts = new HashMap<>();
        List<ExternalBoardSummaryVO> result = new ArrayList<>();
        for (ExternalBoard board : boards) {
            Event event = events.get(board.getEventId());
            if (event == null || (publicOnly && !Integer.valueOf(1).equals(event.getStatus()))) continue;
            if (StringUtils.hasText(keyword) && !(board.getName().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))
                    || event.getName().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))) continue;
            ExternalBoardSummaryVO vo = new ExternalBoardSummaryVO();
            BeanUtils.copyProperties(event, vo);
            BeanUtils.copyProperties(board, vo);
            vo.setEventName(event.getName());
            vo.setSignupCount(counts.computeIfAbsent(event.getEventId(), key -> Integer.valueOf(1).equals(event.getIsTeam())
                    ? signupTeamMapper.countApproved(key) : signupPersonalMapper.countApproved(key)));
            result.add(vo);
        }
        return result;
    }

    // A competition contributes to rating once, using its earliest open board.
    public Long ratingBoardId(Long eventId) {
        ExternalBoard board = externalBoardMapper.selectOne(new QueryWrapper<ExternalBoard>()
                .eq("event_id", eventId).eq("is_external_open", true).orderByAsc("board_id").last("LIMIT 1"));
        return board == null ? null : board.getBoardId();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> resolverParticipants(Long boardId, UserProfileVO currentUser) {
        if (currentUser == null)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        if ("school_admin".equals(currentUser.getRole()) || !com.cwnupaa.backend.utils.SecurityUtils.hasPermission(currentUser, "manage_contest"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "权限不足，无法查看比赛结果");
        Event event=boardEvent(boardId);
        LocalDateTime end=event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        boolean rankUnavailable=end == null || LocalDateTime.now().isBefore(end);
        List<RankItemVO> rows;
        if (rankUnavailable) {
            Map<Long,RankItemVO> roster=new LinkedHashMap<>();
            prepareParticipantInfo(event,roster,false);
            rows=new ArrayList<>(roster.values());
            enrichOrganizations(rows,getConfig(boardId).getOrganizationDisplay());
            rows.sort(Comparator.comparing(row -> row.getName()==null ? "" : row.getName()));
        } else {
            // This method is reachable only through the contest-management route.
            // It can access a closed board, and computes the final unfrozen result.
            rows=buildBoardDetail(boardId,null,"team",null,null,true,true).getRows();
        }
        List<Map<String,Object>> participants = rows.stream().map(row -> {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("itemId",row.getItemId()); item.put("name",row.getName()); item.put("organization",row.getOrganization());
            item.put("rank",rankUnavailable ? null : row.getRank()); item.put("isGirl",row.getIsGirl()); item.put("isStar",row.getIsStar());
            item.put("solved",rankUnavailable ? null : row.getSolved()); item.put("penalty",rankUnavailable ? null : row.getPenalty());
            return item;
        }).toList();
        return Map.of("rankUnavailable", rankUnavailable, "participants", participants);
    }

    public ExternalBoardConfigDTO getConfig(Long boardId) {
        ExternalBoard board = requireBoard(boardId);
        ExternalBoardConfigDTO dto = new ExternalBoardConfigDTO();
        BeanUtils.copyProperties(board, dto);
        dto.setExternalOpen(board.getExternalOpen());
        dto.setUnfrozen(board.getUnfrozen());
        dto.setFreezeTime(board.getFreezeTime());
        dto.setOrganizationDisplay("university".equals(board.getOrganizationDisplay()) ? "university" : "school");
        try {
            dto.setResolverSettings(StringUtils.hasText(board.getResolverSettings())
                    ? jsonMapper.readValue(board.getResolverSettings(), ResolverSettingsDTO.class) : new ResolverSettingsDTO());
        } catch (JsonProcessingException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "滚榜设置无法读取", e);
        }
        dto.setResolverSingleStep(!Boolean.FALSE.equals(board.getResolverSingleStep()));
        dto.setResolverSpeed(board.getResolverSpeed() == null ? 1.0 : board.getResolverSpeed());
        dto.setResolverEnabled(Boolean.TRUE.equals(board.getResolverEnabled()));
        dto.setResolverPasswordSet(StringUtils.hasText(board.getResolverPasswordHash()));
        try {
            if (board.getMedalConfig() != null && !board.getMedalConfig().trim().isEmpty()) {
                dto.setMedalConfig(jsonMapper.readValue(board.getMedalConfig(), ExternalBoardConfigDTO.MedalConfig.class));
            } else {
                dto.setMedalConfig(new ExternalBoardConfigDTO.MedalConfig());
            }
        } catch (JsonProcessingException e) {
            dto.setMedalConfig(new ExternalBoardConfigDTO.MedalConfig());
        }
        List<EventProblem> problems = eventProblemMapper.selectList(new QueryWrapper<EventProblem>().eq("board_id", boardId).orderByAsc("display_id"));
        dto.setProblems(problems.stream().map(p -> {
            ExternalBoardConfigDTO.ProblemConfig pc = new ExternalBoardConfigDTO.ProblemConfig();
            pc.setDisplayId(p.getDisplayId());
            pc.setName(p.getProblem());
            pc.setScore(p.getScore());
            pc.setTimeLimit(p.getTimeLimit());
            pc.setMemoryLimit(p.getMemoryLimit());
            return pc;
        }).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public void updateConfig(ExternalBoardConfigDTO dto) {
        lockBoard(dto.getBoardId());
        ExternalBoard board = requireBoard(dto.getBoardId());
        String section = dto.getConfigSection() == null ? "all" : dto.getConfigSection();
        if (!Set.of("all", "board", "resolver").contains(section))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "配置类型无效");
        boolean updateBoard = !"resolver".equals(section);
        boolean updateResolver = !"board".equals(section);
        // Separate editors update disjoint fields under the same board lock.
        if (updateBoard) {
            board.setName(validateBoardName(dto.getName()));
            if (dto.getOrganizationDisplay() != null) {
                if (!Set.of("school", "university").contains(dto.getOrganizationDisplay()))
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "组织显示方式无效");
                board.setOrganizationDisplay(dto.getOrganizationDisplay());
            }
            board.setExternalOpen(Boolean.TRUE.equals(dto.getExternalOpen()));
            board.setUnfrozen(Boolean.TRUE.equals(dto.getUnfrozen()));
            board.setActualStartTime(dto.getActualStartTime());
            board.setActualEndTime(dto.getActualEndTime());
            board.setFreezeTime(dto.getFreezeTime());
        }
        Event event = eventMapper.selectById(board.getEventId());
        if ("resolver".equals(section) && !"XCPC".equals(event.getType()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "仅 XCPC 外榜支持滚榜配置");
        if (updateResolver) {
            if (dto.getResolverEnabled() != null) board.setResolverEnabled(Boolean.TRUE.equals(dto.getResolverEnabled()));
            if (dto.getResolverSettings() != null) {
                ResolverSettingsDTO settings = dto.getResolverSettings();
                settings.validate();
                if (!settings.getAwards().getCustomAwards().isEmpty()) {
                    Set<Long> approved = new HashSet<>();
                    if (Integer.valueOf(1).equals(event.getIsTeam())) {
                        signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", event.getEventId()).eq("status", "approved"))
                                .forEach(s -> approved.add(s.getSignupId()));
                    } else {
                        signupPersonalMapper.selectList(new QueryWrapper<SignupPersonal>().eq("event_id", event.getEventId()).eq("status", "approved"))
                                .forEach(s -> approved.add(s.getUserId()));
                    }
                    if (settings.getAwards().getCustomAwards().stream().flatMap(a -> a.getParticipantIds().stream()).anyMatch(id -> !approved.contains(id)))
                        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "自定义奖项只能选择当前赛事已通过报名的参赛者");
                }
                try { board.setResolverSettings(jsonMapper.writeValueAsString(settings)); }
                catch (JsonProcessingException e) { throw new IllegalArgumentException("滚榜设置无效", e); }
            }
            if (dto.getResolverSingleStep() != null) board.setResolverSingleStep(dto.getResolverSingleStep());
            if (dto.getResolverSpeed() != null) {
                if (!Double.isFinite(dto.getResolverSpeed()) || dto.getResolverSpeed() < 0.25 || dto.getResolverSpeed() > 4)
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "滚榜播放速度必须在 0.25 至 4 之间");
                board.setResolverSpeed(dto.getResolverSpeed());
            }
            if (StringUtils.hasText(dto.getResolverPassword())) {
                if (dto.getResolverPassword().length() < 12 || dto.getResolverPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "滚榜密码须至少 12 个字符，UTF-8 编码不得超过 72 字节");
                board.setResolverPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(dto.getResolverPassword()));
            }
        }
        if (Boolean.TRUE.equals(board.getResolverEnabled())) {
            if (!"XCPC".equals(event.getType()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "仅 XCPC 外榜可启用滚榜");
            if (!Boolean.TRUE.equals(board.getUnfrozen()) && !StringUtils.hasText(board.getResolverPasswordHash()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "封榜时启用滚榜必须设置密码");
        }
        LocalDateTime start = board.getActualStartTime() != null ? board.getActualStartTime() : event.getEventStart();
        LocalDateTime end = board.getActualEndTime() != null ? board.getActualEndTime() : event.getEventEnd();
        if (start == null || end == null || !start.isBefore(end)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        if (board.getFreezeTime() != null && (board.getFreezeTime().isBefore(start) || board.getFreezeTime().isAfter(end))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "封榜时间须在比赛时间内");
        }
        if (updateResolver && (!"resolver".equals(section) || dto.getMedalConfig() != null)) {
            try {
                board.setMedalConfig(dto.getMedalConfig() == null ? "{}" : jsonMapper.writeValueAsString(dto.getMedalConfig()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("奖牌配置无效", e);
            }
        }
        externalBoardMapper.updateById(board);
        if (updateBoard && dto.getProblems() != null) {
            for (ExternalBoardConfigDTO.ProblemConfig pc : dto.getProblems()) {
                if (pc.getDisplayId() == null || pc.getDisplayId().trim().isEmpty()) continue;
                EventProblem ep = eventProblemMapper.selectOne(new QueryWrapper<EventProblem>().eq("board_id", board.getBoardId()).eq("display_id", pc.getDisplayId()));
                if (ep != null) {
                    ep.setProblem(pc.getName());
                    ep.setScore(pc.getScore());
                    ep.setTimeLimit(pc.getTimeLimit());
                    ep.setMemoryLimit(pc.getMemoryLimit());
                    eventProblemMapper.updateById(ep);
                } else {
                    ep = new EventProblem();
                    ep.setEventId(board.getEventId());
                    ep.setBoardId(board.getBoardId());
                    ep.setDisplayId(pc.getDisplayId());
                    ep.setProblem(pc.getName());
                    ep.setScore(pc.getScore());
                    ep.setTimeLimit(pc.getTimeLimit());
                    ep.setMemoryLimit(pc.getMemoryLimit());
                    eventProblemMapper.insert(ep);
                }
            }
        }
    }

    // （保留原有的单条提交方法，以防其他内部接口仍在使用）
    @Transactional
    public boolean processExternalSubmission(Long boardId, String contestAccount, String problemCode, String status, Integer score, String code, String lang, LocalDateTime submitTime, Integer timeUsage, Integer memoryUsage, String rid) {
        lockBoard(boardId);
        Event event = boardEvent(boardId);
        Long eventId = event.getEventId();

        if (StringUtils.hasText(rid)) {
            Long count = submitRecordMapper.selectCount(new QueryWrapper<SubmitRecord>().eq("board_id", boardId).eq("rid", rid));
            if (count > 0) return false;
        }

        Long userId = null;
        Long signupTeamId = null;
        String cleanAccount = contestAccount != null ? contestAccount.trim() : "";
        if (cleanAccount.isEmpty()) return false;

        if (event.getIsTeam() == 1) {
            boolean matched = false;
            List<SignupTeamDetail> details = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>().eq("contest_account", cleanAccount));
            for (SignupTeamDetail detail : details) {
                SignupTeam signupTeam = signupTeamMapper.selectById(detail.getSignupTeamId());
                if (signupTeam != null && signupTeam.getEventId().equals(eventId) && "approved".equals(signupTeam.getStatus())) {
                    userId = detail.getUserId();
                    signupTeamId = signupTeam.getSignupId();
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                SignupTeam team = signupTeamMapper.selectOne(new QueryWrapper<SignupTeam>().eq("event_id", eventId).eq("contest_account", cleanAccount).eq("status", "approved"));
                if (team != null) {
                    signupTeamId = team.getSignupId();
                    matched = true;
                }
            }
            if (!matched) return false;
        } else {
            SignupPersonal personal = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("contest_account", cleanAccount).eq("status", "approved"));
            if (personal != null) userId = personal.getUserId();
            else return false;
        }

        EventProblem ep = eventProblemMapper.selectOne(new QueryWrapper<EventProblem>().eq("board_id", boardId).and(w -> w.eq("problem", problemCode).or().eq("display_id", problemCode)));
        if (ep == null) return false;

        SubmitRecord record = new SubmitRecord();
        record.setEventId(eventId);
        record.setBoardId(boardId);
        record.setUserId(userId);
        record.setSignupTeamId(signupTeamId);
        record.setProblemId(ep.getDisplayId());
        record.setProblemName(ep.getProblem());
        record.setResult(status);
        record.setScore(score != null ? score : 0);
        record.setCodeContent(code);
        record.setLanguage(lang);
        record.setSubmitTime(submitTime != null ? submitTime : LocalDateTime.now());
        record.setTimeUsage(timeUsage);
        record.setMemoryUsage(memoryUsage);
        record.setCodeLength(code != null ? code.length() : 0);
        record.setRid(rid);

        submitRecordMapper.insert(record);
        return true;
    }

    private Event requirePublicEvent(Long boardId) {
        Event event = boardEvent(boardId);
        if (!Boolean.TRUE.equals(event.getExternalOpen())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "榜单未开放");
        }
        return event;
    }

    public Map<String, Object> getBoardSubmissions(Long boardId, int page, int size, String problemId, String status, String keyword) {
        return getBoardSubmissions(boardId, page, size, problemId, status, keyword, null, null, null);
    }

    public Map<String, Object> getBoardSubmissions(Long boardId, int page, int size, String problemId,
                                                  String status, String keyword, Long signupTeamId,
                                                  Long userId, Long timePoint) {
        if (page < 1 || page > 1000000 || size < 1 || size > 200)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "分页参数无效，每页最多200条");
        Event event = requirePublicEvent(boardId);
        Long eventId = event.getEventId();

        LocalDateTime freezeTimeConf = event.getFreezeTime();
        LocalDateTime end = event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        final LocalDateTime effectiveFreezeTime = (freezeTimeConf == null) ? end.minusHours(1) : freezeTimeConf;
        final boolean isUnfrozen = Boolean.TRUE.equals(event.getUnfrozen());

        Map<String, Integer> problemMaxScores = new HashMap<>();
        eventProblemMapper.selectList(new QueryWrapper<EventProblem>().eq("board_id", boardId)).forEach(ep -> problemMaxScores.put(ep.getDisplayId(), ep.getScore()));

        QueryWrapper<SubmitRecord> query = new QueryWrapper<>();
        query.eq("board_id", boardId);
        constrainBoardRecords(query, event);
        if (signupTeamId != null) query.eq("signup_team_id", signupTeamId);
        if (userId != null) query.eq("user_id", userId);
        if (timePoint != null) {
            query.le("submit_time", LocalDateTime.ofInstant(Instant.ofEpochMilli(timePoint), ZoneId.systemDefault()));
        }
        if (StringUtils.hasText(problemId)) query.eq("problem_id", problemId);
        if (StringUtils.hasText(status)) {
            if ("PENDING".equalsIgnoreCase(status)) {
                query.and(w -> {
                    w.eq("result", status);
                    if (!isUnfrozen) w.or().gt("submit_time", effectiveFreezeTime);
                });
            } else {
                query.eq("result", status);
                if (!isUnfrozen) query.le("submit_time", effectiveFreezeTime);
            }
        }

        if (StringUtils.hasText(keyword)) {
            List<Long> userIds = userMapper.selectList(new QueryWrapper<Users>().like("real_name", keyword).select("user_id")).stream().map(Users::getUserId).collect(Collectors.toList());
            List<Long> signupTeamIds = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", eventId).like("team_name", keyword).select("signup_id")).stream().map(SignupTeam::getSignupId).collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                List<SignupTeamDetail> details = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>().in("user_id", userIds));
                if (!details.isEmpty()) {
                    signupTeamIds.addAll(details.stream().map(SignupTeamDetail::getSignupTeamId).collect(Collectors.toList()));
                }
            }
            final List<Long> finalSignupTeamIds = signupTeamIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (userIds.isEmpty() && finalSignupTeamIds.isEmpty()) query.apply("1=0");
            else query.and(w -> {
                if (!userIds.isEmpty()) w.in("user_id", userIds);
                if (!finalSignupTeamIds.isEmpty()) {
                    if (!userIds.isEmpty()) w.or();
                    w.in("signup_team_id", finalSignupTeamIds);
                }
            });
        }
        query.orderByDesc("submit_time");

        Page<SubmitRecord> pageParam = new Page<>(page, size);
        Page<SubmitRecord> recordPage = submitRecordMapper.selectPage(pageParam, query);

        Set<Long> uids = new HashSet<>();
        Set<Long> signupTeamIds = new HashSet<>();
        recordPage.getRecords().forEach(r -> {
            if (r.getUserId() != null) uids.add(r.getUserId());
            if (r.getSignupTeamId() != null) signupTeamIds.add(r.getSignupTeamId());
        });

        Map<Long, String> userNames = new HashMap<>();
        Map<Long, String> teamNames = new HashMap<>();
        if (!uids.isEmpty())
            userMapper.selectBatchIds(uids).forEach(u -> userNames.put(u.getUserId(), u.getRealName()));
        if (!signupTeamIds.isEmpty())
            signupTeamMapper.selectBatchIds(signupTeamIds).forEach(t -> teamNames.put(t.getSignupId(), t.getTeamName()));

        List<BoardSubmissionVO> vos = recordPage.getRecords().stream().map(r -> {
            BoardSubmissionVO vo = new BoardSubmissionVO();
            vo.setSubmissionId(r.getSubmitRecordId());
            vo.setDisplayId(r.getProblemId());
            vo.setProblemName(r.getProblemName());
            vo.setUserName(r.getUserId() != null ? userNames.getOrDefault(r.getUserId(), "Unknown") : "");
            vo.setTeamName(r.getSignupTeamId() != null ? teamNames.getOrDefault(r.getSignupTeamId(), "") : "");
            if (StringUtils.isEmpty(vo.getUserName()) && !StringUtils.isEmpty(vo.getTeamName()))
                vo.setUserName(vo.getTeamName());
            vo.setLanguage(r.getLanguage());
            vo.setSubmitTime(r.getSubmitTime());
            vo.setLength(r.getCodeLength());

            boolean isFrozen = !isUnfrozen && r.getSubmitTime().isAfter(effectiveFreezeTime);
            if (isFrozen) {
                vo.setStatus("PENDING");
                vo.setScore(null);
                vo.setScoreDisplay("-");
                vo.setTime("-");
                vo.setMemory("-");
            } else {
                vo.setStatus(r.getResult());
                vo.setScore(r.getScore());
                int max = problemMaxScores.getOrDefault(r.getProblemId(), 100);
                int real = "LANQIAO".equals(event.getType()) ? lanqiaoScore(r, max)
                        : (int) Math.ceil((r.getScore() != null ? r.getScore() : 0) * max / 100.0);
                vo.setScoreDisplay(real + "/" + max);
                vo.setTime(r.getTimeUsage() != null ? r.getTimeUsage() + " ms" : "-");
                vo.setMemory(r.getMemoryUsage() != null ? r.getMemoryUsage() + " KB" : "-");
            }
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("records", vos);
        res.put("total", recordPage.getTotal());
        return res;
    }

    public Map<String, String> getSubmissionCode(Long submissionId) {
        SubmitRecord r = submitRecordMapper.selectById(submissionId);
        if (r == null) throw new RuntimeException("Record not found");
        Event event = requirePublicEvent(r.getBoardId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        if (now.isBefore(endTime)) throw new RuntimeException("Competition is running. Code details are hidden.");
        if (!Boolean.TRUE.equals(event.getUnfrozen()))
            throw new RuntimeException("Code details are not available until the contest is unfrozen.");
        LocalDateTime startTime = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        if (isXcpc(event) && (r.getSubmitTime() == null || (startTime != null && r.getSubmitTime().isBefore(startTime)) || r.getSubmitTime().isAfter(endTime)))
            throw new RuntimeException("Submission is outside the competition period.");
        Map<String, String> map = new HashMap<>();
        map.put("content", r.getCodeContent());
        map.put("language", r.getLanguage());
        return map;
    }

    public BoardVO getBoardDetail(Long boardId, Long timePoint, String scope, String groupFilter, UserProfileVO currentUser) {
        return buildBoardDetail(boardId, timePoint, scope, groupFilter, currentUser, null);
    }

    private BoardVO buildBoardDetail(Long boardId, Long timePoint, String scope, String groupFilter, UserProfileVO currentUser, Boolean unfreezeOverride) {
        return buildBoardDetail(boardId, timePoint, scope, groupFilter, currentUser, unfreezeOverride, false);
    }

    private BoardVO buildBoardDetail(Long boardId, Long timePoint, String scope, String groupFilter, UserProfileVO currentUser, Boolean unfreezeOverride, boolean allowClosedBoard) {
        Event event = allowClosedBoard ? boardEvent(boardId) : requirePublicEvent(boardId);
        if (unfreezeOverride != null) event.setUnfrozen(unfreezeOverride);
        Long eventId = event.getEventId();

        boolean isAdmin = currentUser != null && ("supper_admin".equals(currentUser.getRole()) || "admin".equals(currentUser.getRole()));

        ExternalBoardConfigDTO config = getConfig(boardId);
        BoardVO vo = new BoardVO();
        vo.setEventId(eventId);
        vo.setBoardId(boardId);
        vo.setTitle(event.getName());
        vo.setSessionName(config.getName());
        vo.setIsTeam(event.getIsTeam());
        vo.setUnfrozen(Boolean.TRUE.equals(event.getUnfrozen()));
        vo.setResolverEnabled(Boolean.TRUE.equals(config.getResolverEnabled()));
        String type = event.getType();
        if (type == null || type.isEmpty()) type = "XCPC";
        vo.setType(type);
        LocalDateTime now = LocalDateTime.now();
        if (timePoint != null) {
            now = LocalDateTime.ofInstant(Instant.ofEpochMilli(timePoint), ZoneId.systemDefault());
        }
        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        LocalDateTime end = event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        LocalDateTime freezeTime = event.getFreezeTime();

        vo.setStartTime(start);
        vo.setEndTime(end);
        if (freezeTime != null) vo.setFreezeTime(freezeTime.toString());

        if (now.isBefore(start)) vo.setStatus("PENDING");
        else if (now.isAfter(end)) vo.setStatus("ENDED");
        else if (!Boolean.TRUE.equals(event.getUnfrozen()) && isFrozenTime(now, end, freezeTime))
            vo.setStatus("FROZEN");
        else vo.setStatus("RUNNING");

        List<BoardVO.ProblemInfo> problemInfos = config.getProblems().stream().map(p -> {
            BoardVO.ProblemInfo pi = new BoardVO.ProblemInfo();
            pi.setId(p.getDisplayId());
            pi.setName(p.getName());
            pi.setScore(p.getScore());
            pi.setTimeLimit(p.getTimeLimit());
            pi.setMemoryLimit(p.getMemoryLimit());
            return pi;
        }).collect(Collectors.toList());
        vo.setProblems(problemInfos);

        QueryWrapper<SubmitRecord> queryWrapper = new QueryWrapper<SubmitRecord>().eq("board_id", boardId).orderByAsc("submit_time", "submit_record_id");
        constrainBoardRecords(queryWrapper, event);
        if (timePoint != null) {
            queryWrapper.le("submit_time", now);
        }
        List<SubmitRecord> records = submitRecordMapper.selectList(queryWrapper);

        List<RankItemVO> rows = new ArrayList<>();

        if ("GPLT".equals(type)) {
            if ("personal".equalsIgnoreCase(scope)) {
                rows = calculateGPLTPersonalRank(event, records, config, now, isAdmin);
                calculateGPLTPersonalStats(vo, records, config, now, rows);
            } else {
                rows = calculateGPLTTeamRank(event, records, config, now, isAdmin);
            }
        } else if ("LANQIAO".equals(type)) {
            rows = calculateLanqiaoRank(event, records, config, isAdmin);
            calculateGPLTPersonalStats(vo, records, config, now, rows);
        } else {
            List<RankItemVO> allRows = calculateXCPCRank(event, records, config, isAdmin);
            // Problem aggregates must obey the same freeze boundary as the rank.
            LocalDateTime visibleUntil = freezeTime != null ? freezeTime : end.minusHours(1);
            List<SubmitRecord> visibleRecords = Boolean.TRUE.equals(event.getUnfrozen()) ? records
                    : records.stream().filter(r -> !r.getSubmitTime().isAfter(visibleUntil)).toList();
            calculateProblemStatsXCPC(event, vo, visibleRecords);
            rows = allRows;
        }

        enrichOrganizations(rows, config.getOrganizationDisplay());

        if (StringUtils.hasText(groupFilter) && !"all".equals(groupFilter)) {
            rows = rows.stream().filter(r -> groupFilter.equals(r.getGroup())).collect(Collectors.toList());
        }

        vo.setRows(rows);
        return vo;
    }

    private final java.util.concurrent.Semaphore resolverRequests = new java.util.concurrent.Semaphore(2);
    private final java.util.concurrent.Semaphore resolverPasswordChecks = new java.util.concurrent.Semaphore(4);
    private final com.github.benmanes.caffeine.cache.Cache<String, Deque<Long>> resolverAttempts = com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(20000).expireAfterAccess(java.time.Duration.ofMinutes(2)).build();

    public Map<String, Object> resolveBoard(Long boardId, String password) {
        return resolveBoard(boardId,password,"internal");
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> resolveBoard(Long boardId, String password, String source) {
        if (!resolverRequests.tryAcquire())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "正在准备其他滚榜数据，请稍后重试");
        try { return prepareResolver(boardId, password, source); }
        finally { resolverRequests.release(); }
    }

    private Map<String, Object> prepareResolver(Long boardId, String password, String source) {
        Event event = requirePublicEvent(boardId);
        ExternalBoard board = requireBoard(boardId);
        if (!"XCPC".equals(event.getType()) || !Boolean.TRUE.equals(board.getResolverEnabled()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "滚榜未启用");
        LocalDateTime end = event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        if (end == null || LocalDateTime.now().isBefore(end))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "比赛结束后才能滚榜");
        if (!Boolean.TRUE.equals(board.getUnfrozen())) {
            Deque<Long> attempts = resolverAttempts.get(boardId+":"+source, ignored -> new ArrayDeque<>());
            synchronized (attempts) {
                long now = System.currentTimeMillis();
                while (!attempts.isEmpty() && attempts.peekFirst() < now - 60_000) attempts.removeFirst();
                if (attempts.size() >= 10)
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "尝试过于频繁，请稍后重试");
                attempts.addLast(now);
            }
            String hash = board.getResolverPasswordHash();
            boolean matches = false;
            if (StringUtils.hasText(hash) && password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72) {
                if (!resolverPasswordChecks.tryAcquire())
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "正在验证其他滚榜请求，请稍后重试");
                try { matches = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(password, hash); }
                finally { resolverPasswordChecks.release(); }
            }
            if (!matches)
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "滚榜密码错误");
            synchronized (attempts) { attempts.clear(); }
        }
        BoardVO frozen = buildBoardDetail(boardId, null, "team", null, null, false);
        BoardVO full = buildBoardDetail(boardId, null, "team", null, null, true);
        resolverPhotoService.attach(boardId, frozen, full);
        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        LocalDateTime freeze = event.getFreezeTime() != null ? event.getFreezeTime() : end.minusHours(1);
        Set<Long> participantIds=full.getRows().stream().map(RankItemVO::getItemId).collect(Collectors.toSet());
        Set<String> problemIds=full.getProblems().stream().map(BoardVO.ProblemInfo::getId).collect(Collectors.toSet());
        List<Map<String,Object>> submissions = new ArrayList<>();
        for (SubmitRecord record : submitRecordMapper.selectList(new QueryWrapper<SubmitRecord>().eq("board_id",boardId).ge("submit_time",start).le("submit_time",end).orderByAsc("submit_time","submit_record_id"))) {
            Long id=getParticipantId(event,record);
            if(id==null || !participantIds.contains(id) || !problemIds.contains(record.getProblemId())) continue;
            Map<String,Object> submission=new LinkedHashMap<>();
            submission.put("submissionId",record.getSubmitRecordId()); submission.put("participantId",id);
            submission.put("problemId",record.getProblemId()); submission.put("timeMillis",Duration.between(start,record.getSubmitTime()).toMillis());
            submission.put("result",record.getResult()); submission.put("frozen",record.getSubmitTime().isAfter(freeze));
            submissions.add(submission);
        }
        ExternalBoardConfigDTO resolverConfig = getConfig(boardId);
        return Map.of("frozenBoard", frozen, "finalBoard", full, "submissions",submissions,
                "medalConfig",resolverConfig.getMedalConfig(),
                "resolutionSteps", resolverEngineService.generate(frozen, full, submissions, !Boolean.FALSE.equals(board.getResolverSingleStep()), resolverConfig.getResolverSettings()),
                "resolverSettings", resolverConfig.getResolverSettings(),
                "coverUrl", resolverPhotoService.coverUrl(boardId),
                "resolverSingleStep", !Boolean.FALSE.equals(board.getResolverSingleStep()),
                "resolverSpeed", board.getResolverSpeed() == null ? 1.0 : board.getResolverSpeed());
    }

    public BoardStatsVO getStatistics(Long boardId, Long timePoint) {
        Event event = requirePublicEvent(boardId);
        Long eventId = event.getEventId();

        ExternalBoardConfigDTO config = getConfig(boardId);
        BoardStatsVO stats = new BoardStatsVO();

        List<ExternalBoardConfigDTO.ProblemConfig> problems = config.getProblems();
        stats.setProblemCount(problems.size());

        LocalDateTime now = LocalDateTime.now();
        if (timePoint != null) {
            now = LocalDateTime.ofInstant(Instant.ofEpochMilli(timePoint), ZoneId.systemDefault());
        }

        LocalDateTime freezeTimeConf = event.getFreezeTime();
        LocalDateTime end = event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        final LocalDateTime effectiveFreezeTime = (freezeTimeConf == null) ? end.minusHours(1) : freezeTimeConf;
        final boolean isUnfrozen = Boolean.TRUE.equals(event.getUnfrozen());

        QueryWrapper<SubmitRecord> queryWrapper = new QueryWrapper<SubmitRecord>().eq("board_id", boardId);
        constrainBoardRecords(queryWrapper, event);
        LocalDateTime queryTimeLimit = null;
        if (timePoint != null) {
            queryTimeLimit = now;
            if (!isUnfrozen && now.isAfter(effectiveFreezeTime)) {
                queryTimeLimit = effectiveFreezeTime;
            }
            queryWrapper.le("submit_time", queryTimeLimit);
        } else if (!isUnfrozen) {
            queryTimeLimit = effectiveFreezeTime;
            queryWrapper.le("submit_time", queryTimeLimit);
        }

        List<SubmitRecord> records = submitRecordMapper.selectList(queryWrapper);
        stats.setTotalSubmissions(records.size());

        List<RankItemVO> rankList;
        String type = event.getType() != null ? event.getType() : "XCPC";

        if ("GPLT".equals(type)) rankList = calculateGPLTPersonalRank(event, records, config, queryTimeLimit, false);
        else if ("LANQIAO".equals(type)) rankList = calculateLanqiaoRank(event, records, config, false);
        else rankList = calculateXCPCRank(event, records, config, false);

        enrichOrganizations(rankList, config.getOrganizationDisplay());
        stats.setParticipantCount(rankList.size());
        long orgCount = rankList.stream().map(RankItemVO::getOrganization).filter(Objects::nonNull).filter(s -> !s.isEmpty() && !"北京电子科技学院".equals(s)).distinct().count();
        stats.setOrganizationCount((int) orgCount);

        Map<String, int[]> problemSubStats = new HashMap<>();
        for (ExternalBoardConfigDTO.ProblemConfig p : problems) problemSubStats.put(p.getDisplayId(), new int[]{0, 0});

        for (SubmitRecord r : records) {
            if (!problemSubStats.containsKey(r.getProblemId())) continue;
            int[] counts = problemSubStats.get(r.getProblemId());

            boolean isAccepted;
            if ("GPLT".equals(type) || "LANQIAO".equals(type)) {
                Integer maxScore = 0;
                for (ExternalBoardConfigDTO.ProblemConfig p : problems)
                    if (p.getDisplayId().equals(r.getProblemId())) maxScore = p.getScore();
                int real = "LANQIAO".equals(type) ? lanqiaoScore(r, maxScore)
                        : (int) Math.ceil((r.getScore() != null ? r.getScore() : 0) * maxScore / 100.0);
                isAccepted = (real >= maxScore);
            } else {
                isAccepted = isAc(r.getResult());
            }

            if (isAccepted) counts[0]++;
            else counts[1]++;
        }

        List<BoardStatsVO.SubmissionChartItem> subChart = new ArrayList<>();
        for (ExternalBoardConfigDTO.ProblemConfig p : problems) {
            int[] counts = problemSubStats.get(p.getDisplayId());
            subChart.add(new BoardStatsVO.SubmissionChartItem(p.getDisplayId(), counts[0], counts[1]));
        }
        stats.setSubmissionChart(subChart);

        List<BoardStatsVO.ProblemPassItem> probPassChart = subChart.stream().sorted((a, b) -> b.getAcCount().compareTo(a.getAcCount())).map(item -> new BoardStatsVO.ProblemPassItem(item.getProblemId(), item.getAcCount())).collect(Collectors.toList());
        stats.setProblemPassChart(probPassChart);

        Map<Integer, Integer> solvedDist = new HashMap<>();
        for (int i = 0; i <= problems.size(); i++) solvedDist.put(i, 0);

        for (RankItemVO r : rankList) {
            int solved = 0;
            if ("GPLT".equals(type) || "LANQIAO".equals(type)) {
                if (r.getProblemStatus() != null) {
                    for (Map.Entry<String, RankItemVO.ProblemStatus> en : r.getProblemStatus().entrySet()) {
                        Integer s = en.getValue().getScore();
                        Integer max = 0;
                        for (ExternalBoardConfigDTO.ProblemConfig p : problems)
                            if (p.getDisplayId().equals(en.getKey())) max = p.getScore();
                        if (s != null && s >= max) solved++;
                    }
                }
            } else {
                solved = r.getSolved() != null ? r.getSolved() : 0;
            }
            if (solved > problems.size()) solved = problems.size();
            solvedDist.put(solved, solvedDist.getOrDefault(solved, 0) + 1);
        }

        List<BoardStatsVO.TeamPassItem> teamPassChart = new ArrayList<>();
        for (int i = 0; i <= problems.size(); i++) {
            teamPassChart.add(new BoardStatsVO.TeamPassItem(i, solvedDist.get(i)));
        }
        stats.setTeamPassChart(teamPassChart);

        return stats;
    }

    private int lanqiaoScore(SubmitRecord record, int maximum) {
        String verdict = record.getResult() == null ? "" : record.getResult().trim().toUpperCase(Locale.ROOT).replace('_', ' ');
        if (LANQIAO_ZERO_VERDICTS.contains(verdict)) return 0;
        return (int) Math.round((record.getScore() == null ? 0 : record.getScore()) * (maximum / 100.0));
    }

    private List<RankItemVO> calculateLanqiaoRank(Event event, List<SubmitRecord> records, ExternalBoardConfigDTO config, boolean isAdmin) {
        Map<String, Integer> maxScores = new HashMap<>();
        if (config.getProblems() != null) {
            for (ExternalBoardConfigDTO.ProblemConfig p : config.getProblems())
                maxScores.put(p.getDisplayId(), p.getScore());
        }
        Map<Long, RankItemVO> rankMap = new HashMap<>();
        prepareParticipantInfo(event, rankMap, isAdmin);
        Map<Long, Map<String, Integer>> finalScores = new HashMap<>();
        Map<Long, Map<String, Long>> finalTimes = new HashMap<>();

        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        LocalDateTime freezeTime = event.getFreezeTime();
        if (freezeTime == null)
            freezeTime = (event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd()).minusHours(1);

        // LANQIAO uses the latest submission, including CE and pending runs.
        // Proctor timestamps tie on upstream IDs rather than network arrival order.
        for (SubmitRecord r : records.stream().sorted(Comparator.comparing(SubmitRecord::getSubmitTime)
                .thenComparing(r -> r.getRid() != null && r.getRid().startsWith("proctor:") ? r.getRid() : "")
                .thenComparing(SubmitRecord::getSubmitRecordId, Comparator.nullsFirst(Long::compareTo))).toList()) {
            if ("Cancelled".equalsIgnoreCase(r.getResult())) continue;
            Long id = getParticipantId(event, r);
            if (id == null || !rankMap.containsKey(id)) continue;
            rankMap.get(id).setHasSubmission(true);
            if (!Boolean.TRUE.equals(event.getUnfrozen()) && r.getSubmitTime().isAfter(freezeTime)) continue;

            finalScores.putIfAbsent(id, new HashMap<>());
            finalTimes.putIfAbsent(id, new HashMap<>());
            Map<String, Integer> pScores = finalScores.get(id);
            Map<String, Long> pTimes = finalTimes.get(id);
            
            int max = maxScores.getOrDefault(r.getProblemId(), 100);
            int realScore = lanqiaoScore(r, max);
            
            long currentMillis = Duration.between(start, r.getSubmitTime()).toMillis();
            if (currentMillis < 0) currentMillis = 0;

            pScores.put(r.getProblemId(), realScore);
            pTimes.put(r.getProblemId(), currentMillis);
        }

        for (Long id : rankMap.keySet()) {
            RankItemVO item = rankMap.get(id);
            Map<String, Integer> scores = finalScores.getOrDefault(id, new HashMap<>());
            Map<String, Long> times = finalTimes.getOrDefault(id, new HashMap<>());
            int total = 0;
            long lastTime = 0;
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                RankItemVO.ProblemStatus ps = new RankItemVO.ProblemStatus();
                ps.setScore(entry.getValue());
                ps.setStatus("Scored");
                item.getProblemStatus().put(entry.getKey(), ps);
                total += entry.getValue();
                if (entry.getValue() > 0) {
                    lastTime = Math.max(lastTime, times.get(entry.getKey()));
                }
            }
            item.setTotalScore(total);
            item.setPenaltyMillis(lastTime);
        }
        List<RankItemVO> list = new ArrayList<>(rankMap.values());

        return assignGroupMedals(list);
    }

    private List<RankItemVO> calculateGPLTPersonalRank(Event event, List<SubmitRecord> records, ExternalBoardConfigDTO config, LocalDateTime nowLimit, boolean isAdmin) {
        Map<String, Integer> maxScores = new HashMap<>();
        config.getProblems().forEach(p -> maxScores.put(p.getDisplayId(), p.getScore()));

        Map<Long, RankItemVO> rankMap = new HashMap<>();
        prepareGPLTParticipants(event, rankMap, isAdmin);

        Map<Long, Map<String, Integer>> userBestScores = new HashMap<>();
        Map<Long, Map<String, Long>> userBestTimes = new HashMap<>();
        
        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        LocalDateTime freezeTime = event.getFreezeTime();
        if (freezeTime == null)
            freezeTime = (event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd()).minusHours(1);
        boolean isUnfrozen = Boolean.TRUE.equals(event.getUnfrozen());

        for (SubmitRecord r : records) {
            Long uid = r.getUserId();
            if (uid == null || !rankMap.containsKey(uid)) continue;
            rankMap.get(uid).setHasSubmission(true);
            if (!isUnfrozen && r.getSubmitTime().isAfter(freezeTime)) continue;
            if ("Compile Error".equals(r.getResult()) || "CE".equals(r.getResult())) continue;

            userBestScores.putIfAbsent(uid, new HashMap<>());
            userBestTimes.putIfAbsent(uid, new HashMap<>());
            Map<String, Integer> pMap = userBestScores.get(uid);
            Map<String, Long> tMap = userBestTimes.get(uid);

            int problemMax = maxScores.getOrDefault(r.getProblemId(), 100);
            int raw = r.getScore() != null ? r.getScore() : 0;
            int real = (int) Math.ceil(raw * problemMax / 100.0);
            
            long currentMillis = Duration.between(start, r.getSubmitTime()).toMillis();
            if (currentMillis < 0) currentMillis = 0;

            Integer existingScore = pMap.get(r.getProblemId());
            if (existingScore == null || real > existingScore) {
                pMap.put(r.getProblemId(), real);
                tMap.put(r.getProblemId(), currentMillis);
            } else if (real == existingScore) {
                Long existingTime = tMap.get(r.getProblemId());
                if (existingTime == null || currentMillis < existingTime) {
                    tMap.put(r.getProblemId(), currentMillis);
                }
            }
        }

        for (RankItemVO item : rankMap.values()) {
            Map<String, Integer> scores = userBestScores.getOrDefault(item.getItemId(), new HashMap<>());
            Map<String, Long> times = userBestTimes.getOrDefault(item.getItemId(), new HashMap<>());
            int total = 0;
            long lastTime = 0;
            for (Map.Entry<String, Integer> en : scores.entrySet()) {
                RankItemVO.ProblemStatus ps = new RankItemVO.ProblemStatus();
                ps.setScore(en.getValue());
                ps.setStatus("Scored");
                item.getProblemStatus().put(en.getKey(), ps);
                total += en.getValue();
                if (en.getValue() > 0) {
                    lastTime = Math.max(lastTime, times.get(en.getKey()));
                }
            }
            item.setTotalScore(total);
            item.setPenaltyMillis(lastTime);
        }

        List<RankItemVO> allList = new ArrayList<>(rankMap.values());

        return assignGroupMedals(allList);
    }

    private List<RankItemVO> calculateGPLTTeamRank(Event event, List<SubmitRecord> records, ExternalBoardConfigDTO config, LocalDateTime nowLimit, boolean isAdmin) {
        List<RankItemVO> personalRanks = calculateGPLTPersonalRank(event, records, config, nowLimit, isAdmin);
        Map<Long, RankItemVO> teamMap = new HashMap<>();
        List<SignupTeam> teams = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", event.getEventId()).eq("status", "approved"));

        List<Long> signupIds = teams.stream().map(SignupTeam::getSignupId).collect(Collectors.toList());
        List<SignupTeamDetail> allDetails = new ArrayList<>();
        if (!signupIds.isEmpty()) {
            allDetails = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>()
                    .in("signup_team_id", signupIds)
                    .orderByAsc("signup_team_id", "member_order", "detail_id"));
        }

        Set<Long> allPersonIds = new HashSet<>();
        allDetails.forEach(d -> allPersonIds.add(d.getUserId()));
        Map<Long, Users> personMap = new HashMap<>();
        if (!allPersonIds.isEmpty()) {
            userMapper.selectBatchIds(allPersonIds).forEach(u -> personMap.put(u.getUserId(), u));
        }

        Map<Long, List<SignupTeamDetail>> signupDetailsMap = allDetails.stream().collect(Collectors.groupingBy(SignupTeamDetail::getSignupTeamId));
        Set<Long> submittedTeamIds = records.stream().map(SubmitRecord::getSignupTeamId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (SignupTeam s : teams) {
            List<String> names = new ArrayList<>();
            List<RankItemVO.MemberInfo> infos = new ArrayList<>();
            List<Long> memberIds = new ArrayList<>();
            List<SignupTeamDetail> ds = signupDetailsMap.getOrDefault(s.getSignupId(), new ArrayList<>());
            for (SignupTeamDetail d : ds) {
                Users u = personMap.get(d.getUserId());
                if (u != null) {
                    memberIds.add(u.getUserId());
                    names.add(u.getRealName());
                    infos.add(new RankItemVO.MemberInfo(u.getRealName(), u.getNumberId()));
                }
            }

            RankItemVO tVo = new RankItemVO();
            tVo.setItemId(s.getSignupId());
            tVo.setHasSubmission(submittedTeamIds.contains(s.getSignupId()));
            tVo.setName(StringUtils.hasText(s.getTeamName()) ? s.getTeamName() : "Unknown");
            tVo.setGroup(s.getType());
            tVo.setAdvisor(s.getAdvisor());
            tVo.setIsStar("star".equals(s.getType()) || (s.getIsRanking() != null && s.getIsRanking() == 0));
            tVo.setMembers(String.join(", ", names));
            tVo.setMemberIds(memberIds);
            if (isAdmin) {
                tVo.setMemberInfos(infos);
            }

            String org = "北京电子科技学院";
            Users leader = ds.isEmpty() ? null : personMap.get(ds.get(0).getUserId());
            if (leader != null) {
                if (leader.getAvatarFileId() != null) tVo.setAvatar("/api/file/download/" + leader.getAvatarFileId());
            }
            tVo.setOrganization(org);
            tVo.setTotalScore(0);
            tVo.setProblemStatus(new HashMap<>());
            teamMap.put(s.getSignupId(), tVo);
        }

        Map<Long, Long> userSignupMap = new HashMap<>();
        for (SignupTeamDetail std : allDetails) {
            userSignupMap.put(std.getUserId(), std.getSignupTeamId());
        }

        for (RankItemVO p : personalRanks) {
            Long tid = userSignupMap.get(p.getItemId());
            if (tid != null && teamMap.containsKey(tid)) {
                RankItemVO tVo = teamMap.get(tid);
                if (p.getHasSubmission()) tVo.setHasSubmission(true);
                int current = tVo.getTotalScore() != null ? tVo.getTotalScore() : 0;
                int score = p.getTotalScore() != null ? p.getTotalScore() : 0;
                tVo.setTotalScore(current + score);
                // Display the sum of members' existing best scores without changing team ranking.
                for (Map.Entry<String, RankItemVO.ProblemStatus> entry : p.getProblemStatus().entrySet()) {
                    if (!"Scored".equals(entry.getValue().getStatus())) continue;
                    RankItemVO.ProblemStatus teamProblem = tVo.getProblemStatus().computeIfAbsent(entry.getKey(), key -> {
                        RankItemVO.ProblemStatus cell = new RankItemVO.ProblemStatus();
                        cell.setStatus("Scored");
                        cell.setScore(0);
                        int maximum = config.getProblems().stream().filter(problem -> key.equals(problem.getDisplayId()))
                                .map(problem -> problem.getScore() == null ? 100 : problem.getScore()).findFirst().orElse(100);
                        cell.setMaxScore(maximum * tVo.getMemberIds().size());
                        return cell;
                    });
                    teamProblem.setScore(teamProblem.getScore() + entry.getValue().getScore());
                }
                if (score > 0 && p.getPenaltyMillis() != null) {
                    if (tVo.getPenaltyMillis() == null || p.getPenaltyMillis() > tVo.getPenaltyMillis()) {
                        tVo.setPenaltyMillis(p.getPenaltyMillis());
                    }
                }
            }
        }

        List<RankItemVO> teamList = new ArrayList<>(teamMap.values());

        return assignGroupMedals(teamList);
    }

    private List<RankItemVO> calculateXCPCRank(Event event, List<SubmitRecord> records, ExternalBoardConfigDTO config, boolean isAdmin) {
        Map<Long, RankItemVO> rankMap = new HashMap<>();
        prepareParticipantInfo(event, rankMap, isAdmin);

        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        LocalDateTime freezeTime = event.getFreezeTime();
        if (freezeTime == null)
            freezeTime = (event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd()).minusHours(1);
        boolean isUnfrozen = Boolean.TRUE.equals(event.getUnfrozen());
        Map<Long, Map<String, ProblemState>> userStates = new HashMap<>();

        for (SubmitRecord r : records) {
            Long id = getParticipantId(event, r);
            if (id == null || !rankMap.containsKey(id)) continue;
            // Activity is independent of verdict, score, and freeze visibility.
            rankMap.get(id).setHasSubmission(true);

            userStates.putIfAbsent(id, new HashMap<>());
            Map<String, ProblemState> pMap = userStates.get(id);
            pMap.putIfAbsent(r.getProblemId(), new ProblemState());
            ProblemState state = pMap.get(r.getProblemId());

            if (state.solved) continue;

            long currentMinutes = Duration.between(start, r.getSubmitTime()).toMinutes();
            long currentMillis = Duration.between(start, r.getSubmitTime()).toMillis();
            if (currentMinutes < 0) currentMinutes = 0;
            if (currentMillis < 0) currentMillis = 0;
            state.lastSubmitTime = currentMinutes;

            boolean isRecordFrozen = !isUnfrozen && r.getSubmitTime().isAfter(freezeTime);

            if (isRecordFrozen) {
                state.pendingCount++;
            } else {
                if (isAc(r.getResult())) {
                    if (!state.solved) { // 只记录第一次AC的数据
                        state.solved = true;
                        state.solveTime = currentMinutes;
                        state.solveTimeMillis = currentMillis;
                    }
                } else if (!"Compile Error".equals(r.getResult()) && !"CE".equals(r.getResult())) {
                    // CE 不计入罚时和错误次数
                    if (!state.solved) {
                        state.preFreezeErrors++;
                    }
                }
            }
        }

        calculateFirstAcXCPC(userStates);

        // 1. Calculate Stats
        Map<String, Long> pAttempted = new HashMap<>();
        Map<String, Long> pAccepted = new HashMap<>();
        Map<String, Long> pFirst = new HashMap<>();
        Map<String, Long> pLast = new HashMap<>();

        for (Map<String, ProblemState> pMap : userStates.values()) {
            for (Map.Entry<String, ProblemState> entry : pMap.entrySet()) {
                String pid = entry.getKey();
                ProblemState ps = entry.getValue();

                // Attempted = (1 if solved) + preFreezeErrors
                long attempts = (ps.solved ? 1 : 0) + ps.preFreezeErrors;
                if (attempts > 0) {
                    pAttempted.merge(pid, attempts, Long::sum);
                }

                if (ps.solved) {
                    pAccepted.merge(pid, 1L, Long::sum);
                    
                    if (!pFirst.containsKey(pid) || ps.solveTime < pFirst.get(pid)) {
                        pFirst.put(pid, ps.solveTime);
                    }
                    if (!pLast.containsKey(pid) || ps.solveTime > pLast.get(pid)) {
                        pLast.put(pid, ps.solveTime);
                    }
                }
            }
        }
        
        // 2. Attach Stats to "system" item or similar mechanism? 
        // Actually the ExternalBoardController returns "list". 
        // The stats are usually requested via /stats endpoint.
        // But if user says "外榜下方的数据面板有问题", it implies the data is already being displayed but wrong.
        // If it is ContestRankDetail.tsx, it might be fetching /stats.
        // Let's assume we need to fix the logic in getExternalBoardStatistics which likely reuses logic or we need to put it somewhere.
        // Wait, I am editing calculateXCPCRank which returns List<RankItemVO>.
        // Does RankItemVO have a place for global stats? No.
        // The stats are likely calculated in a separate method or the frontend calculates them?
        // If frontend calculates them from the rank list, then my changes to ProblemState (excluding CE) should already propagate if detailed submit data was sent.
        // But RankItemVO only contains "tryCount" (preFreezeErrors + 1 if solved, else preFreezeErrors).
        // If CE is excluded from preFreezeErrors (which I did in previous step), then frontend calculation might be correct IF it sums up tryCounts.
        // BUT the user specifically mentioned "Attempted is sum of ...".
        // Let's look at getExternalBoardStatistics method in this file.

        for (Long uid : rankMap.keySet()) {
            RankItemVO item = rankMap.get(uid);
            Map<String, ProblemState> pMap = userStates.getOrDefault(uid, new HashMap<>());
            for (Map.Entry<String, ProblemState> entry : pMap.entrySet()) {
                String pid = entry.getKey();
                ProblemState ps = entry.getValue();
                RankItemVO.ProblemStatus voPs = new RankItemVO.ProblemStatus();
                if (ps.solved) {
                    voPs.setStatus("AC");
                    voPs.setTime(ps.solveTime);
                    voPs.setTimeMillis(ps.solveTimeMillis);
                    voPs.setTryCount(ps.preFreezeErrors + 1);
                    voPs.setIsFirstAc(ps.isFirstAc);
                    item.setSolved(item.getSolved() + 1);
                    item.setPenalty(item.getPenalty() + ps.solveTime + (long) ps.preFreezeErrors * 20);
                    item.setPenaltyMillis((item.getPenaltyMillis() != null ? item.getPenaltyMillis() : 0L) + ps.solveTimeMillis + (long) ps.preFreezeErrors * 20 * 60 * 1000);
                } else if (ps.pendingCount > 0) {
                    voPs.setStatus("PENDING");
                    voPs.setTryCount(ps.pendingCount);
                    voPs.setScore(ps.preFreezeErrors);
                    voPs.setTime(ps.lastSubmitTime);
                } else if (ps.preFreezeErrors > 0) {
                    voPs.setStatus("WA");
                    voPs.setTryCount(ps.preFreezeErrors);
                    voPs.setTime(ps.lastSubmitTime);
                } else continue;
                item.getProblemStatus().put(pid, voPs);
            }
        }

        List<RankItemVO> list = new ArrayList<>(rankMap.values());
        list.sort((a, b) -> {
            int cmp = Boolean.compare(b.getHasSubmission(), a.getHasSubmission());
            if (cmp != 0) return cmp;
            cmp = b.getSolved().compareTo(a.getSolved());
            if (cmp != 0) return cmp;
            Long aPenalty = a.getPenaltyMillis() != null ? a.getPenaltyMillis() : a.getPenalty() * 60 * 1000;
            Long bPenalty = b.getPenaltyMillis() != null ? b.getPenaltyMillis() : b.getPenalty() * 60 * 1000;
            return aPenalty.compareTo(bPenalty);
        });

        assignXCPCMedals(list, config.getMedalConfig());
        return list;
    }

    private void prepareGPLTParticipants(Event event, Map<Long, RankItemVO> rankMap, boolean isAdmin) {
        List<SignupTeam> teams = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", event.getEventId()).eq("status", "approved"));
        if (teams.isEmpty()) return;

        List<Long> signupIds = teams.stream().map(SignupTeam::getSignupId).collect(Collectors.toList());
        List<SignupTeamDetail> details = new ArrayList<>();
        if (!signupIds.isEmpty()) {
            details = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>()
                    .in("signup_team_id", signupIds)
                    .orderByAsc("signup_team_id", "member_order", "detail_id"));
        }

        Set<Long> allUserIds = new HashSet<>();
        details.forEach(d -> allUserIds.add(d.getUserId()));

        Map<Long, Users> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) userMapper.selectBatchIds(allUserIds).forEach(u -> userMap.put(u.getUserId(), u));

        Map<Long, SignupTeam> signupMap = teams.stream().collect(Collectors.toMap(SignupTeam::getSignupId, s -> s));

        BiConsumer<Long, SignupTeam> createVO = (uid, st) -> {
            if (rankMap.containsKey(uid)) return;
                RankItemVO v = new RankItemVO();
                v.setItemId(uid);
                v.setMemberIds(Collections.singletonList(uid));
                Users u = userMap.get(uid);
            String name = "Unknown";
            String org = "北京电子科技学院";
            String avatar = null;
            if (u != null) {
                name = u.getRealName();
                if (u.getAvatarFileId() != null) avatar = "/api/file/download/" + u.getAvatarFileId();
                if (isAdmin) {
                    RankItemVO.MemberInfo info = new RankItemVO.MemberInfo();
                    info.setName(u.getRealName());
                    info.setNumberId(u.getNumberId());
                    v.setMemberInfos(Collections.singletonList(info));
                }
            }
            v.setName(name);
            v.setAvatar(avatar);
            v.setOrganization(org);
            v.setGroup(st.getType());
            v.setAdvisor(st.getAdvisor());
            v.setIsStar("star".equals(st.getType()) || (st.getIsRanking() != null && st.getIsRanking() == 0));
            v.setIsGirl(u != null && u.getGender() != null && u.getGender() == 2);
            v.setTotalScore(0);
            v.setPenaltyMillis(0L);
            v.setProblemStatus(new HashMap<>());
            rankMap.put(uid, v);
        };

        for (SignupTeamDetail d : details) {
            SignupTeam st = signupMap.get(d.getSignupTeamId());
            if (st != null) createVO.accept(d.getUserId(), st);
        }
    }

    private void calculateGPLTPersonalStats(BoardVO vo, List<SubmitRecord> records, ExternalBoardConfigDTO config, LocalDateTime nowLimit, List<RankItemVO> rows) {
        Map<String, BoardVO.ProblemInfo> pMap = vo.getProblems().stream().collect(Collectors.toMap(BoardVO.ProblemInfo::getId, p -> p));

        for (BoardVO.ProblemInfo p : vo.getProblems()) {
            p.setSubmitted(0);
            p.setAttempted(0);
            p.setAccepted(0);
            p.setMaxScore(0);
            p.setMinScore(Integer.MAX_VALUE);
            p.setAvgScore(0.0);
        }

        Map<String, List<Integer>> problemScores = new HashMap<>();
        LocalDateTime freezeTime = vo.getFreezeTime() != null ? LocalDateTime.parse(vo.getFreezeTime()) : null;
        boolean isUnfrozen = "ENDED".equals(vo.getStatus()) || Boolean.TRUE.equals(config.getUnfrozen());

        for (SubmitRecord r : records) {
            if (freezeTime != null && !isUnfrozen && r.getSubmitTime().isAfter(freezeTime)) continue;
            BoardVO.ProblemInfo p = pMap.get(r.getProblemId());
            if (p == null) continue;
            p.setSubmitted(p.getSubmitted() + 1);
        }

        if (rows != null) {
            for (RankItemVO row : rows) {
                Map<String, RankItemVO.ProblemStatus> map = row.getProblemStatus();
                if (map == null) continue;
                for (Map.Entry<String, RankItemVO.ProblemStatus> en : map.entrySet()) {
                    String pid = en.getKey();
                    Integer score = en.getValue().getScore();
                    BoardVO.ProblemInfo p = pMap.get(pid);
                    if (p != null) {
                        problemScores.putIfAbsent(pid, new ArrayList<>());
                        problemScores.get(pid).add(score);
                    }
                }
            }
        }

        for (BoardVO.ProblemInfo p : vo.getProblems()) {
            List<Integer> scores = problemScores.getOrDefault(p.getId(), new ArrayList<>());
            p.setAttempted(scores.size());
            if (!scores.isEmpty()) {
                int max = Collections.max(scores);
                int min = Collections.min(scores);
                double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                p.setMaxScore(max);
                p.setMinScore(min);
                p.setAvgScore(Math.round(avg * 100.0) / 100.0);
            } else {
                p.setMinScore(0);
            }
        }
    }

    private void calculateProblemStatsXCPC(Event event, BoardVO vo, List<SubmitRecord> records) {
        Map<String, BoardVO.ProblemInfo> pMap = vo.getProblems().stream().collect(Collectors.toMap(BoardVO.ProblemInfo::getId, p -> p));
        
        // Initialize
        for (BoardVO.ProblemInfo p : vo.getProblems()) {
            p.setAttempted(0); // Total attempts (AC + errors) excluding CE
            p.setAccepted(0);  // AC count
            p.setSubmitted(0); // Total submissions (raw count including CE)
            p.setFirstSolved(-1L); // min time (minutes)
            p.setLastSolved(-1L);  // max time (minutes)
        }

        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();

        // Process records to calculate stats directly
        // "Attempted是只计算已经通过这个题目的人的提交记录，未通过这个题目的人的错误提交记录不算在Attempted里面"
        // "Accepted是通过的提交记录数量" (All AC submissions count)
        // "Submitted" (Total submissions including CE)

        // 1. First pass: Calculate Submitted and Accepted (raw counts)
        for (SubmitRecord r : records) {
            BoardVO.ProblemInfo p = pMap.get(r.getProblemId());
            if (p == null) continue;
            
            p.setSubmitted(p.getSubmitted() + 1); // Total submissions (including CE)
            
            if (isAc(r.getResult())) {
                p.setAccepted(p.getAccepted() + 1); // Total AC submissions
            }
        }

        // 2. Second pass: Calculate Attempted and Solved Times (per user/team logic)
        Map<Long, Map<String, ProblemState>> userStates = new HashMap<>();
        
        for (SubmitRecord r : records) {
            Long id = getParticipantId(event, r);
            if (id == null) continue;

            userStates.putIfAbsent(id, new HashMap<>());
            Map<String, ProblemState> uMap = userStates.get(id);
            uMap.putIfAbsent(r.getProblemId(), new ProblemState());
            ProblemState state = uMap.get(r.getProblemId());

            if (state.solved) continue; // Stop counting for Attempted/Time after first AC

            if (isAc(r.getResult())) {
                 if (!state.solved) {
                     state.solved = true;
                     long minutes = Duration.between(start, r.getSubmitTime()).toMinutes();
                     if (minutes < 0) minutes = 0;
                     state.solveTime = minutes;
                 }
            } else if (!"Compile Error".equals(r.getResult()) && !"CE".equals(r.getResult())) {
                state.preFreezeErrors++;
            }
        }

        // Aggregation for Attempted and Times
        Map<String, Long> minTimeMap = new HashMap<>();
        Map<String, Long> maxTimeMap = new HashMap<>();

        for (Map<String, ProblemState> uMap : userStates.values()) {
            for (Map.Entry<String, ProblemState> en : uMap.entrySet()) {
                String pid = en.getKey();
                ProblemState ps = en.getValue();
                BoardVO.ProblemInfo p = pMap.get(pid);
                if (p == null) continue;

                if (ps.solved) {
                    // Attempted logic: (1 if AC) + errors (excluding CE)
                    // ONLY calculate for those who SOLVED the problem
                    long attempts = 1 + ps.preFreezeErrors;
                    p.setAttempted((int) (p.getAttempted() + attempts));
                    
                    long t = ps.solveTime;
                    
                    // First Solved (min time)
                    if (!minTimeMap.containsKey(pid) || t < minTimeMap.get(pid)) {
                        minTimeMap.put(pid, t);
                        p.setFirstSolved(t);
                    }
                    
                    // Last Solved (max time)
                    if (!maxTimeMap.containsKey(pid) || t > maxTimeMap.get(pid)) {
                        maxTimeMap.put(pid, t);
                        p.setLastSolved(t);
                    }
                }
            }
        }
    }

    private boolean isXcpc(Event event) {
        return !StringUtils.hasText(event.getType()) || "XCPC".equals(event.getType());
    }

    private void constrainBoardRecords(QueryWrapper<SubmitRecord> query, Event event) {
        // Cancelled runs are retractions, not a latest zero-point CE run.
        if ("LANQIAO".equals(event.getType())) query.and(w -> w.isNull("result").or().ne("result", "Cancelled"));
        if (!isXcpc(event)) return;
        LocalDateTime start = event.getActualStartTime() != null ? event.getActualStartTime() : event.getEventStart();
        LocalDateTime end = event.getActualEndTime() != null ? event.getActualEndTime() : event.getEventEnd();
        if (start != null) query.ge("submit_time", start);
        if (end != null) query.le("submit_time", end);
    }

    private Long getParticipantId(Event event, SubmitRecord record) {
        return Integer.valueOf(1).equals(event.getIsTeam()) ? record.getSignupTeamId() : record.getUserId();
    }

    /** Resolve all participants together, so logo lookup does not add a query per row. */
    /** Public signup cards follow the formal board, then an open board, then the first configured board. */
    public void enrichSignupOrganizations(Long eventId, Map<EventSignupVO, Long> participants) {
        List<ExternalBoard> boards = externalBoardMapper.selectList(new QueryWrapper<ExternalBoard>()
                .eq("event_id", eventId).orderByAsc("board_id"));
        ExternalBoard selected = boards.stream().filter(board -> "正式赛".equals(board.getName())).findFirst()
                .orElseGet(() -> boards.stream().filter(board -> Boolean.TRUE.equals(board.getExternalOpen())).findFirst()
                        .orElse(boards.isEmpty() ? null : boards.get(0)));
        String display = selected == null ? "school" : selected.getOrganizationDisplay();
        List<EventSignupVO> signups = new ArrayList<>();
        List<RankItemVO> identities = new ArrayList<>();
        participants.forEach((signup, userId) -> {
            if (userId == null) return;
            RankItemVO identity = new RankItemVO();
            identity.setMemberIds(List.of(userId));
            identity.setOrganization(signup.getSchoolName());
            identities.add(identity);
            signups.add(signup);
        });
        enrichOrganizations(identities, display);
        for (int index = 0; index < signups.size(); index++) {
            signups.get(index).setOrganization(identities.get(index).getOrganization());
            signups.get(index).setOrganizationLogo(identities.get(index).getOrganizationLogo());
        }
    }

    private void enrichOrganizations(List<RankItemVO> rows, String display) {
        Set<Long> ids = rows.stream().map(RankItemVO::getMemberIds).filter(Objects::nonNull)
                .filter(members -> !members.isEmpty()).map(members -> members.get(0))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        List<Users> users = userMapper.selectBatchIds(ids);
        if (users == null || users.isEmpty()) return;
        Map<Long, Users> byUser = users.stream().collect(Collectors.toMap(Users::getUserId, u -> u));
        Set<Long> majorIds = users.stream().map(Users::getMajorId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (majorIds.isEmpty()) return;
        List<Major> majors = majorMapper.selectBatchIds(majorIds);
        if (majors == null || majors.isEmpty()) return;
        Map<Long, Major> byMajor = majors.stream().collect(Collectors.toMap(Major::getMajorId, m -> m));
        Set<Long> schoolIds = majors.stream().map(Major::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (schoolIds.isEmpty()) return;
        List<School> schools = schoolMapper.selectBatchIds(schoolIds);
        if (schools == null || schools.isEmpty()) return;
        Map<Long, School> bySchool = schools.stream().collect(Collectors.toMap(School::getSchoolId, school -> school));
        Set<Long> universityIds = schools.stream().map(School::getUniversityId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, University> byUniversity = new HashMap<>();
        if (!universityIds.isEmpty()) {
            List<University> universities = universityMapper.selectBatchIds(universityIds);
            if (universities != null) universities.forEach(u -> byUniversity.put(u.getUniversityId(), u));
        }
        boolean universityMode = "university".equals(display);
        for (RankItemVO row : rows) {
            if (row.getMemberIds() == null || row.getMemberIds().isEmpty()) continue;
            Users user = byUser.get(row.getMemberIds().get(0));
            Major major = user == null ? null : byMajor.get(user.getMajorId());
            School school = major == null ? null : bySchool.get(major.getSchoolId());
            if (school == null) continue;
            University university = byUniversity.get(school.getUniversityId());
            row.setOrganization(universityMode && university != null ? university.getUniversityName() : school.getSchoolName());
            Long logoId = !universityMode ? school.getLogoFileId() : null;
            if (logoId == null && university != null) logoId = university.getLogoFileId();
            row.setOrganizationLogo(logoId == null ? null : "/api/file/image/" + logoId);
        }
    }

    private boolean isAc(String result) {
        return result != null && (result.equalsIgnoreCase("Accepted") || result.equalsIgnoreCase("AC") || result.equalsIgnoreCase("Correct"));
    }

    private boolean isFrozenTime(LocalDateTime now, LocalDateTime end, LocalDateTime freezeTime) {
        if (freezeTime != null) return now.isAfter(freezeTime) && now.isBefore(end);
        return now.isAfter(end.minusHours(1)) && now.isBefore(end);
    }

    private void calculateFirstAcXCPC(Map<Long, Map<String, ProblemState>> userStates) {
        Map<String, Long> minTimeMillis = new HashMap<>();
        for (Map<String, ProblemState> pMap : userStates.values()) {
            for (Map.Entry<String, ProblemState> en : pMap.entrySet()) {
                if (en.getValue().solved) {
                    String pid = en.getKey();
                    long t = en.getValue().solveTimeMillis;
                    if (!minTimeMillis.containsKey(pid) || t < minTimeMillis.get(pid)) {
                        minTimeMillis.put(pid, t);
                    }
                }
            }
        }
        for (Map<String, ProblemState> pMap : userStates.values()) {
            for (Map.Entry<String, ProblemState> en : pMap.entrySet()) {
                if (en.getValue().solved && Objects.equals(en.getValue().solveTimeMillis, minTimeMillis.get(en.getKey()))) {
                    en.getValue().isFirstAc = true;
                }
            }
        }
    }

    private void prepareParticipantInfo(Event event, Map<Long, RankItemVO> rankMap, boolean isAdmin) {
        boolean isTeam = Integer.valueOf(1).equals(event.getIsTeam());
        if (isTeam) {
            List<SignupTeam> signups = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", event.getEventId()).eq("status", "approved"));
            if (signups.isEmpty()) return;

            List<Long> signupIds = signups.stream().map(SignupTeam::getSignupId).collect(Collectors.toList());
            List<SignupTeamDetail> details = new ArrayList<>();
            if (!signupIds.isEmpty())
                details = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>()
                        .in("signup_team_id", signupIds)
                        .orderByAsc("signup_team_id", "member_order", "detail_id"));
            Map<Long, List<SignupTeamDetail>> detailsMap = details.stream().collect(Collectors.groupingBy(SignupTeamDetail::getSignupTeamId));

            Set<Long> personIds = new HashSet<>();
            details.forEach(d -> personIds.add(d.getUserId()));

            Map<Long, Users> userMap = new HashMap<>();
            if (!personIds.isEmpty())
                userMapper.selectBatchIds(personIds).forEach(u -> userMap.put(u.getUserId(), u));

            for (SignupTeam s : signups) {
                RankItemVO v = new RankItemVO();
                v.setItemId(s.getSignupId());
                String name = StringUtils.hasText(s.getTeamName()) ? s.getTeamName() : "Unknown Team";
                String org = "北京电子科技学院";
                String avatar = null;

                List<String> mNames = new ArrayList<>();
                List<RankItemVO.MemberInfo> memberInfos = new ArrayList<>();
                List<SignupTeamDetail> ds = detailsMap.getOrDefault(s.getSignupId(), new ArrayList<>());

                for (int i = 0; i < ds.size(); i++) {
                    SignupTeamDetail d = ds.get(i);
                    Users u = userMap.get(d.getUserId());
                    if (u == null) continue;
                    if (v.getMemberIds() == null) v.setMemberIds(new ArrayList<>());
                    v.getMemberIds().add(u.getUserId());
                    mNames.add(u.getRealName());
                    if (isAdmin) {
                        memberInfos.add(new RankItemVO.MemberInfo(u.getRealName(), u.getNumberId()));
                    }
                    if (i == 0) {
                        if (u.getAvatarFileId() != null) avatar = "/api/file/download/" + u.getAvatarFileId();
                    }
                }

                v.setMembers(String.join(", ", mNames));
                if (isAdmin) v.setMemberInfos(memberInfos);

                v.setName(name);
                v.setGroup(s.getType());
                v.setAdvisor(s.getAdvisor());
                v.setIsStar("star".equalsIgnoreCase(s.getType()) || (s.getIsRanking() != null && s.getIsRanking() == 0));
                v.setIsGirl("girl".equalsIgnoreCase(s.getType()));
                v.setOrganization(org);
                v.setAvatar(avatar);
                v.setSolved(0);
                v.setPenalty(0L);
                v.setPenaltyMillis(0L);
                v.setTotalScore(0);
                v.setProblemStatus(new HashMap<>());
                rankMap.put(s.getSignupId(), v);
            }
        } else {
            List<SignupPersonal> signups = signupPersonalMapper.selectList(new QueryWrapper<SignupPersonal>().eq("event_id", event.getEventId()).eq("status", "approved"));
            Set<Long> userIds = signups.stream().map(SignupPersonal::getUserId).collect(Collectors.toSet());
            Map<Long, Users> userInfo = new HashMap<>();
            if (!userIds.isEmpty()) userMapper.selectBatchIds(userIds).forEach(u -> userInfo.put(u.getUserId(), u));
            for (SignupPersonal s : signups) {
                RankItemVO v = new RankItemVO();
                v.setItemId(s.getUserId());
                v.setMemberIds(Collections.singletonList(s.getUserId()));
                Users user = userInfo.get(s.getUserId());
                String name = "Unknown";
                String org = "北京电子科技学院";
                String avatar = null;
                if (user != null) {
                    name = user.getRealName();
                    if (user.getAvatarFileId() != null) avatar = "/api/file/download/" + user.getAvatarFileId();
                    if (isAdmin) {
                        RankItemVO.MemberInfo info = new RankItemVO.MemberInfo();
                        info.setName(user.getRealName());
                        info.setNumberId(user.getNumberId());
                        v.setMemberInfos(Collections.singletonList(info));
                    }
                    v.setIsGirl(user.getGender() != null && user.getGender() == 2);
                }
                v.setIsStar("star".equalsIgnoreCase(s.getType()) || (s.getIsRanking() != null && s.getIsRanking() == 0));
                v.setGroup(s.getType());
                v.setAdvisor(s.getAdvisor());
                v.setName(name);
                v.setOrganization(org);
                v.setAvatar(avatar);
                v.setSolved(0);
                v.setPenalty(0L);
                v.setPenaltyMillis(0L);
                v.setTotalScore(0);
                v.setProblemStatus(new HashMap<>());
                rankMap.put(s.getUserId(), v);
            }
        }
    }

    private void assignXCPCMedals(List<RankItemVO> list, ExternalBoardConfigDTO.MedalConfig medals) {
        assignRanks(list, true);

        if (medals == null) return;
        int gold = medals.getGold() != null ? medals.getGold() : 0;
        int silver = medals.getSilver() != null ? medals.getSilver() : 0;
        int bronze = medals.getBronze() != null ? medals.getBronze() : 0;

        for (RankItemVO item : list) {
            if (Boolean.TRUE.equals(item.getIsStar())) continue;

            if (gold > 0) {
                item.setMedal("Gold");
                gold--;
            } else if (silver > 0) {
                item.setMedal("Silver");
                silver--;
            } else if (bronze > 0) {
                item.setMedal("Bronze");
                bronze--;
            }
        }
    }

    private List<RankItemVO> assignGroupMedals(List<RankItemVO> allList) {
        Map<String, List<RankItemVO>> groups = allList.stream().collect(Collectors.groupingBy(item -> item.getGroup() != null ? item.getGroup() : "Default"));

        List<RankItemVO> result = new ArrayList<>();

        for (Map.Entry<String, List<RankItemVO>> entry : groups.entrySet()) {
            List<RankItemVO> groupList = entry.getValue();
            groupList.sort((a, b) -> {
                int cmp = Boolean.compare(b.getHasSubmission(), a.getHasSubmission());
                if (cmp != 0) return cmp;
                cmp = b.getTotalScore().compareTo(a.getTotalScore());
                if (cmp != 0) return cmp;
                Long aPenalty = a.getPenaltyMillis() != null ? a.getPenaltyMillis() : 0L;
                Long bPenalty = b.getPenaltyMillis() != null ? b.getPenaltyMillis() : 0L;
                return aPenalty.compareTo(bPenalty);
            });

            assignRanks(groupList, false);

            long officialCount = groupList.stream().filter(i -> !Boolean.TRUE.equals(i.getIsStar())).count();
            int goldCount = (int) Math.round(officialCount * 0.1);
            if (goldCount == 0 && officialCount > 0) goldCount = 1;
            
            int silverCount = goldCount * 2;
            int bronzeCount = goldCount * 3;

            int remainingGold = goldCount;
            int remainingSilver = silverCount;
            int remainingBronze = bronzeCount;

            for (RankItemVO item : groupList) {
                if (Boolean.TRUE.equals(item.getIsStar())) continue;

                if (remainingGold > 0) {
                    item.setMedal("Gold");
                    remainingGold--;
                } else if (remainingSilver > 0) {
                    item.setMedal("Silver");
                    remainingSilver--;
                } else if (remainingBronze > 0) {
                    item.setMedal("Bronze");
                    remainingBronze--;
                }
            }
            result.addAll(groupList);
        }

        result.sort((a, b) -> {
            int cmp = Boolean.compare(b.getHasSubmission(), a.getHasSubmission());
            if (cmp != 0) return cmp;
            cmp = b.getTotalScore().compareTo(a.getTotalScore());
            if (cmp != 0) return cmp;
            Long aPenalty = a.getPenaltyMillis() != null ? a.getPenaltyMillis() : 0L;
            Long bPenalty = b.getPenaltyMillis() != null ? b.getPenaltyMillis() : 0L;
            return aPenalty.compareTo(bPenalty);
        });
        return result;
    }

    private void assignRanks(List<RankItemVO> list, boolean isXCPC) {
        int currentRank = 1;
        RankItemVO prevOfficial = null;

        for (RankItemVO item : list) {
            if (Boolean.TRUE.equals(item.getIsStar())) {
                item.setRank(-1);
                continue;
            }

            boolean isTie = false;
            if (prevOfficial != null) {
                if (isXCPC) {
                    Long itemPenalty = item.getPenaltyMillis() != null ? item.getPenaltyMillis() : item.getPenalty() * 60 * 1000;
                    Long prevPenalty = prevOfficial.getPenaltyMillis() != null ? prevOfficial.getPenaltyMillis() : prevOfficial.getPenalty() * 60 * 1000;
                    isTie = Objects.equals(item.getSolved(), prevOfficial.getSolved()) && Objects.equals(itemPenalty, prevPenalty);
                } else {
                    Long itemPenalty = item.getPenaltyMillis() != null ? item.getPenaltyMillis() : 0L;
                    Long prevPenalty = prevOfficial.getPenaltyMillis() != null ? prevOfficial.getPenaltyMillis() : 0L;
                    isTie = Objects.equals(item.getTotalScore(), prevOfficial.getTotalScore()) && Objects.equals(itemPenalty, prevPenalty);
                }
            }

            if (isTie) {
                item.setRank(prevOfficial.getRank());
            } else {
                item.setRank(currentRank);
            }

            prevOfficial = item;
            currentRank++;
        }
    }

    // ========== 以下为新增的批量处理代码 ========== //

    @Transactional
    public int processExternalProblemsBatch(Long boardId, List<Map<String, Object>> problems) {
        lockBoard(boardId);
        Event event = boardEvent(boardId);
        Long eventId = event.getEventId();
        if (problems == null || problems.isEmpty()) return 0;
        
        int count = 0;
        for (Map<String, Object> p : problems) {
            String displayId = (String) p.get("display_id");
            if (displayId == null || displayId.trim().isEmpty()) continue;
            
            String title = (String) p.get("title");
            Integer score = 100; // Default to 100
            if (p.containsKey("score") && p.get("score") != null) {
                try {
                    score = Integer.parseInt(String.valueOf(p.get("score")));
                } catch (NumberFormatException e) {
                    // ignore, keep default 100
                }
            }
            
            Integer timeLimit = null;
            if (p.containsKey("time_limit") && p.get("time_limit") != null) {
                 try {
                    timeLimit = Integer.parseInt(String.valueOf(p.get("time_limit")));
                } catch (NumberFormatException e) {}
            }
            
            Integer memoryLimit = null;
             if (p.containsKey("memory_limit") && p.get("memory_limit") != null) {
                 try {
                    memoryLimit = Integer.parseInt(String.valueOf(p.get("memory_limit")));
                } catch (NumberFormatException e) {}
            }

            EventProblem ep = eventProblemMapper.selectOne(new QueryWrapper<EventProblem>()
                    .eq("board_id", boardId)
                    .eq("display_id", displayId));
            
            if (ep != null) {
                ep.setProblem(title);
                ep.setScore(score);
                if (timeLimit != null) ep.setTimeLimit(timeLimit);
                if (memoryLimit != null) ep.setMemoryLimit(memoryLimit);
                eventProblemMapper.updateById(ep);
            } else {
                ep = new EventProblem();
                ep.setEventId(eventId);
                ep.setBoardId(boardId);
                ep.setDisplayId(displayId);
                ep.setProblem(title);
                ep.setScore(score);
                ep.setTimeLimit(timeLimit);
                ep.setMemoryLimit(memoryLimit);
                eventProblemMapper.insert(ep);
            }
            count++;
        }
        return count;
    }

    @Transactional
    public int processExternalSubmissionsBatch(Long boardId, List<Map<String, Object>> submissions) {
        lockBoard(boardId);
        Event event = boardEvent(boardId);
        Long eventId = event.getEventId();

        // 1. 缓存该赛事所有的题目信息，避免循环查库
        List<EventProblem> problems = eventProblemMapper.selectList(new QueryWrapper<EventProblem>().eq("board_id", boardId));
        Map<String, EventProblem> problemMap = new HashMap<>();
        for (EventProblem ep : problems) {
            problemMap.put(ep.getDisplayId(), ep);
            problemMap.put(ep.getProblem(), ep);
        }

        // 2. 只读取当前补传批次的身份字段，避免反复载入整场历史代码。
        Map<String, SubmitRecord> existingRecords = new HashMap<>();
        List<String> incomingIds = submissions.stream().map(p -> p.get("rid"))
                .filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::hasText)
                .distinct().collect(Collectors.toList());
        for (int offset = 0; offset < incomingIds.size(); offset += 256) {
            List<String> ids = incomingIds.subList(offset, Math.min(incomingIds.size(), offset + 256));
            List<SubmitRecord> matches = submitRecordMapper.selectList(new QueryWrapper<SubmitRecord>()
                    .eq("board_id", boardId).in("rid", ids)
                    .select("submit_record_id", "rid", "user_id", "signup_team_id"));
            for (SubmitRecord r : matches) {
                if (StringUtils.hasText(r.getRid())) existingRecords.put(r.getRid(), r);
            }
        }

        // 3. 缓存参赛人员的账号映射关系
        Map<String, Long> accountUserIdMap = new HashMap<>();
        Map<String, Long> accountSignupTeamIdMap = new HashMap<>();

        if (Integer.valueOf(1).equals(event.getIsTeam())) {
            List<SignupTeam> signupTeams = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>()
                    .eq("event_id", eventId).eq("status", "approved"));
            for (SignupTeam st : signupTeams) {
                if (StringUtils.hasText(st.getContestAccount())) {
                    accountSignupTeamIdMap.put(st.getContestAccount().trim(), st.getSignupId());
                }
            }
            List<Long> signupTeamIds = signupTeams.stream().map(SignupTeam::getSignupId).collect(Collectors.toList());
            if (!signupTeamIds.isEmpty()) {
                List<SignupTeamDetail> details = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>()
                        .in("signup_team_id", signupTeamIds));
                for (SignupTeamDetail d : details) {
                    if (StringUtils.hasText(d.getContestAccount())) {
                        accountUserIdMap.put(d.getContestAccount().trim(), d.getUserId());
                        accountSignupTeamIdMap.put(d.getContestAccount().trim(), d.getSignupTeamId());
                    }
                }
            }
        } else {
            List<SignupPersonal> personals = signupPersonalMapper.selectList(new QueryWrapper<SignupPersonal>()
                    .eq("event_id", eventId).eq("status", "approved"));
            for (SignupPersonal p : personals) {
                if (StringUtils.hasText(p.getContestAccount())) {
                    accountUserIdMap.put(p.getContestAccount().trim(), p.getUserId());
                }
            }
        }

        DateTimeFormatter formatter = new java.time.format.DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .optionalStart()
                .appendFraction(java.time.temporal.ChronoField.MILLI_OF_SECOND, 1, 3, true)
                .optionalEnd()
                .toFormatter();
        int savedCount = 0;

        // 4. 遍历处理所有提交记录并在内存中匹配
        for (Map<String, Object> payload : submissions) {
            String contestAccount = (String) payload.get("username");
            String problem = (String) payload.get("problem");
            String status = (String) payload.get("status");
            String rid = (String) payload.get("rid");

            SubmitRecord record = null;
            boolean isUpdate = false;
            
            if (StringUtils.hasText(rid) && existingRecords.containsKey(rid)) {
                record = existingRecords.get(rid);
                isUpdate = true;
            } else {
                record = new SubmitRecord();
                record.setRid(rid);
            }

            String cleanAccount = contestAccount != null ? contestAccount.trim() : "";
            
            Long userId = null;
            Long signupTeamId = null;
            
            if (!cleanAccount.isEmpty()) {
                userId = accountUserIdMap.get(cleanAccount);
                signupTeamId = accountSignupTeamIdMap.get(cleanAccount);
            }
            
            if (isUpdate) {
                if (userId == null) userId = record.getUserId();
                if (signupTeamId == null) signupTeamId = record.getSignupTeamId();
            }

            if (Integer.valueOf(1).equals(event.getIsTeam()) && signupTeamId == null) continue;
            if (!Integer.valueOf(1).equals(event.getIsTeam()) && userId == null) continue;

            EventProblem ep = problemMap.get(problem);
            if (ep == null) continue;

            Integer score = 0;
            try {
                score = Integer.parseInt(String.valueOf(payload.get("score")));
            } catch (Exception ignored) {}

            String code = (String) payload.get("code");
            String lang = (String) payload.get("language");

            Integer timeUsage = parseTimeOrMemory(String.valueOf(payload.get("runtime")));
            Integer memoryUsage = parseTimeOrMemory(String.valueOf(payload.get("memory")));

            LocalDateTime submitTime = LocalDateTime.now();
            String submitTimeStr = (String) payload.get("submit_time");
            if (submitTimeStr != null && !submitTimeStr.trim().isEmpty()) {
                try {
                    submitTime = LocalDateTime.parse(submitTimeStr.trim(), formatter);
                } catch (Exception ignored) {}
            }

            record.setEventId(eventId);
            record.setBoardId(boardId);
            record.setUserId(userId);
            record.setSignupTeamId(signupTeamId);
            record.setProblemId(ep.getDisplayId());
            record.setProblemName(ep.getProblem());
            record.setResult(status);
            record.setScore(score != null ? score : 0);
            record.setCodeContent(code);
            record.setLanguage(lang);
            record.setSubmitTime(submitTime);
            record.setTimeUsage(timeUsage);
            record.setMemoryUsage(memoryUsage);
            record.setCodeLength(code != null ? code.length() : 0);

            if (isUpdate) {
                submitRecordMapper.updateById(record);
            } else {
                try {
                    submitRecordMapper.insert(record);
                } catch (DuplicateKeyException e) {
                    SubmitRecord existing = submitRecordMapper.selectOne(
                        new QueryWrapper<SubmitRecord>()
                            .eq("board_id", boardId)
                            .eq("rid", rid));
                    if (existing != null) {
                        record.setSubmitRecordId(existing.getSubmitRecordId());
                        submitRecordMapper.updateById(record);
                    }
                }
                if (StringUtils.hasText(rid)) existingRecords.put(rid, record);
            }
            
            savedCount++;
        }

        return savedCount;
    }

    // 辅助正则提取数字方法（原类中的parseInt，改个名字避免冲突）
    private Integer parseTimeOrMemory(String str) {
        if (str == null || str.isEmpty()) return 0;
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+)").matcher(str.trim());
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static class ProblemState {
        boolean solved = false;
        int preFreezeErrors = 0;
        int pendingCount = 0;
        long solveTime = 0; // minutes
        long solveTimeMillis = 0; // milliseconds, for First AC check
        long lastSubmitTime = 0;
        boolean isFirstAc = false;
    }
}
