package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.utils.AdvisorNames;
import com.cwnupaa.backend.service.ExternalBoardService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.config.SignupWriteGuard;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import com.cwnupaa.backend.dto.EventDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.AdmissionTicketPdfService;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.EventSignupNotificationService;
import com.cwnupaa.backend.service.EventSignupPolicy;
import com.cwnupaa.backend.service.EventRosterService;
import com.cwnupaa.backend.service.SignupTeamIdentityService;
import com.cwnupaa.backend.service.SignupCredentialService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.vo.EventSignupVO;
import com.cwnupaa.backend.vo.EventVO;
import com.cwnupaa.backend.vo.MySignupInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/event")
public class EventController {
    private static final List<String> GPLT_TYPES = Arrays.asList("星芒初绽", "长风破浪", "凌霄问鼎");
    private static final List<String> LANQIAO_TYPES = Arrays.asList("C/C++程序设计", "Python程序设计", "Java软件开发");
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private SignupPersonalMapper signupPersonalMapper;
    @Autowired
    private SignupTeamMapper signupTeamMapper;
    @Autowired
    private SignupTeamDetailMapper signupTeamDetailMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private TeamMemberMapper teamMemberMapper;
    @Autowired
    private EmailService emailService;
    @Autowired private EventSignupNotificationService signupNotifications;
    @Autowired private EventSignupPolicy signupPolicy;
    @Autowired private EventRosterService eventRoster;
    @Autowired private ExternalBoardService externalBoards;
    @Autowired private SignupTeamIdentityService signupTeamIdentity;
    @Autowired private SignupCredentialService signupCredentials;
    @Autowired
    private AdmissionTicketPdfService admissionTicketPdfService;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private FileRelationMapper fileRelationMapper;
    @Autowired
    private FileSystemMapper fileMapper;
    @Autowired
    private ContestCategoryMapper categoryMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @GetMapping("/list")
    public Result<List<EventVO>> list() {
        List<Event> events = eventMapper.selectList(new QueryWrapper<Event>().eq("status", 1).orderByDesc("signup_start"));
        List<EventVO> voList = events.stream().map(this::convertToVo).collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/detail/{id}")
    public Result<EventVO> detail(@PathVariable Long id) {
        Event event = eventMapper.selectById(id);
        if (event == null) return Result.error("赛事不存在");
        return Result.success(convertToVo(event));
    }

    private EventVO convertToVo(Event e) {
        EventVO vo = new EventVO();
        BeanUtils.copyProperties(e, vo);
        ContestCategory cat = categoryMapper.selectById(e.getCategoryId());
        if (cat != null) vo.setCategoryName(cat.getName());
        Term term = termMapper.selectById(e.getTermId());
        if (term != null) vo.setTermName(term.getTermName());

        Long count;
        if (e.getIsTeam() == 1) {
            count = signupTeamMapper.countApproved(e.getEventId());
        } else {
            count = signupPersonalMapper.countApproved(e.getEventId());
        }
        vo.setSignupCount(EventRosterService.isRoster(e) ? eventRoster.count(e.getEventId()) : count);

        // 计算业务状态
        LocalDateTime now = LocalDateTime.now();
        if (EventRosterService.isRoster(e)) {
            vo.setState(now.isBefore(e.getEventStart()) ? "waiting" : now.isBefore(e.getEventEnd()) ? "ongoing" : "ended");
        } else if (now.isBefore(e.getSignupStart())) {
            vo.setState("pending"); // 报名未开始
        } else if (e.getSignupEnd() == null || now.isBefore(e.getSignupEnd())) {
            vo.setState("registering"); // 报名进行中
        } else if (now.isBefore(e.getEventStart())) {
            vo.setState("waiting"); // 等待开赛
        } else if (now.isBefore(e.getEventEnd())) {
            vo.setState("ongoing"); // 正在比赛中
        } else {
            vo.setState("ended"); // 比赛已结束
        }

        List<FileRelation> relations = fileRelationMapper.selectList(new QueryWrapper<FileRelation>().eq("biz_type", "event").eq("business_id", e.getEventId()));
        if (!relations.isEmpty()) {
            List<Long> fileIds = relations.stream().map(FileRelation::getFileId).collect(Collectors.toList());
            List<FileSystem> files = fileMapper.selectBatchIds(fileIds);
            vo.setAttachments(files.stream().filter(f -> fileAccessPolicy.canAccess(f, null)).toList());
        }
        return vo;
    }

    @PostMapping("/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> publish(@RequestBody EventDTO dto, HttpServletRequest request) {
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");
        if (!checkPermission(operatorId)) return Result.error("无权发布赛事");
        Result<String> validation = validateEventDTO(dto);
        if (validation != null) return validation;
        String fileError = fileAccessPolicy.validatePublicFiles(dto.getAttachmentIds(), SecurityUtils.currentUser(request));
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(dto.getDescription(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);

        Event event = new Event();
        BeanUtils.copyProperties(dto, event);
        if (event.getSignupMode() == null) event.setSignupMode("open");
        if (event.getAdmissionTicketOpen() == null) event.setAdmissionTicketOpen(false);
        eventMapper.insert(event);

        if (dto.getAttachmentIds() != null) {
            for (Long fileId : dto.getAttachmentIds()) {
                if (fileId == null) continue;
                FileRelation fr = new FileRelation();
                fr.setFileId(fileId);
                fr.setBizType("event");
                fr.setBusinessId(event.getEventId());
                fileRelationMapper.insert(fr);
            }
        }
        return Result.success("发布成功");
    }

    @PostMapping("/update")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> update(@RequestBody EventDTO dto, HttpServletRequest request) {
        if (dto == null || dto.getEventId() == null) return Result.error("ID不能为空");
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");
        if (!checkPermission(operatorId)) return Result.error("无权操作");
        Result<String> validation = validateEventDTO(dto);
        if (validation != null) return validation;
        String fileError = fileAccessPolicy.validatePublicFiles(dto.getAttachmentIds(), SecurityUtils.currentUser(request));
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(dto.getDescription(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);

        Event event = eventMapper.selectForSignupUpdate(dto.getEventId());
        if (event == null) return Result.error("赛事不存在");
        if (dto.getMaxSignup() != null && dto.getMaxSignup() < (EventRosterService.isRoster(event) ? eventRoster.count(event.getEventId()) : signupPolicy.approvedCount(event))) {
            return Result.error("报名上限不能小于已审核通过数量");
        }
        Event oldEventState = new Event();
        BeanUtils.copyProperties(event, oldEventState);

        BeanUtils.copyProperties(dto, event);
        if (event.getSignupMode() == null) event.setSignupMode(oldEventState.getSignupMode());
        if (event.getAdmissionTicketOpen() == null) event.setAdmissionTicketOpen(oldEventState.getAdmissionTicketOpen());
        if (dto.getAdmissionTicketRules() == null) event.setAdmissionTicketRules(oldEventState.getAdmissionTicketRules());
        eventMapper.updateById(event);

        List<Long> newFileIds = dto.getAttachmentIds() == null ? new ArrayList<>() : dto.getAttachmentIds();

        fileRelationMapper.delete(new QueryWrapper<FileRelation>().eq("biz_type", "event").eq("business_id", dto.getEventId()));
        for (Long fileId : newFileIds) {
            if (fileId == null) continue;
            FileRelation fr = new FileRelation();
            fr.setFileId(fileId);
            fr.setBizType("event");
            fr.setBusinessId(event.getEventId());
            fileRelationMapper.insert(fr);
        }

        if (Boolean.TRUE.equals(dto.getNotifyParticipants())) {
            new Thread(() -> checkAndSendTimeChangeNotification(oldEventState, event)).start();
        }

        return Result.success("更新成功");
    }

    @PostMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> delete(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");
        if (!checkPermission(operatorId)) return Result.error("无权操作");
        Long eventId = params.get("eventId");

        fileRelationMapper.delete(new QueryWrapper<FileRelation>().eq("biz_type", "event").eq("business_id", eventId));

        eventMapper.deleteById(eventId);
        return Result.success("删除成功");
    }

    @GetMapping("/signup-status")
    public Result<String> getSignupStatus(@RequestParam Long eventId, HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        Long userId = currentUserId;
        Event event = eventMapper.selectById(eventId);
        if (event == null) return Result.error("赛事不存在");

        if (EventRosterService.isRoster(event) && !eventRoster.eligible(eventId,userId)) return Result.error(403,"当前实名身份未匹配到确定名单");
        if (event.getIsTeam() == 0) {
            SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", userId));
            return Result.success(signup == null ? "none" : signup.getStatus());
        } else {
            SignupTeam signup = signupTeamMapper.selectLatestByMember(eventId, userId);
            return Result.success(signup == null ? "none" : signup.getStatus());
        }
    }

    @GetMapping("/my-signup-detail")
    public Result<MySignupInfoVO> getMySignupDetail(@RequestParam Long eventId, HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        Long userId = currentUserId;
        Event event = eventMapper.selectById(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event) && !eventRoster.eligible(eventId, userId)) return Result.error(403, "当前实名身份未匹配到确定名单");

        MySignupInfoVO vo = new MySignupInfoVO();

        if (event.getIsTeam() == 0) {
            SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", userId));
            if (signup == null) return Result.error("未报名");

            vo.setSignupType("personal");
            vo.setStatus(signup.getStatus());
            vo.setSignupTime(signup.getSignupTime());
            vo.setIsRanking(signup.getIsRanking());
            vo.setAdvisor(signup.getAdvisor());
            if (StringUtils.hasText(signup.getType())) {
                vo.setTeamTypeDisplay(signup.getType());
            }

            Users u = userMapper.selectById(userId);
            if (u != null) {
                MySignupInfoVO.SimpleMemberInfo sm = toSimpleMemberInfo(u, false);
                vo.setMembers(Collections.singletonList(sm));
            }

            if ("approved".equals(signup.getStatus())) {
                vo.setContestAccount(signup.getContestAccount());
                vo.setContestPassword(signup.getContestPassword());
                vo.setSeatNumber(signup.getSeatNumber());
            }

        } else {
            SignupTeam mySignup = signupTeamMapper.selectLatestByMember(eventId, userId);

            if (mySignup == null) return Result.error("未报名");

            List<SignupTeamDetail> details = getSignupDetailsOrdered(mySignup.getSignupId());
            List<Users> signupMembers = loadUsersForDetails(details);

            vo.setSignupType("team");
            vo.setStatus(mySignup.getStatus());
            vo.setSignupTime(mySignup.getSignupTime());
            vo.setIsRanking(mySignup.getIsRanking());
            vo.setAdvisor(mySignup.getAdvisor());
            vo.setTeamName(mySignup.getTeamName());
            String typeCode = mySignup.getType();
            boolean isGirls = isAllGirls(signupMembers);

            if ("star".equals(typeCode)) {
                vo.setTeamTypeDisplay("打星队伍");
            } else if (GPLT_TYPES.contains(typeCode)) {
                vo.setTeamTypeDisplay(typeCode);
            } else if ("girl".equals(typeCode) || isGirls) {
                vo.setTeamTypeDisplay("女生队伍");
            } else {
                vo.setTeamTypeDisplay("正式队伍");
            }

            if (!signupMembers.isEmpty()) {
                Long captainId = details.isEmpty() ? null : details.get(0).getUserId();
                boolean captainView = userId.equals(captainId);
                List<MySignupInfoVO.SimpleMemberInfo> simpleMembers = new ArrayList<>();
                for (Users member : signupMembers) {
                    boolean captain = Objects.equals(member.getUserId(), captainId);
                    simpleMembers.add(toSignupMemberInfo(member, captain,
                            captainView || userId.equals(member.getUserId())));
                }
                vo.setMembers(simpleMembers);
            }

            if ("approved".equals(mySignup.getStatus())) {
                Long finalUserId = userId;
                SignupTeamDetail myDetail = details.stream().filter(d -> d.getUserId().equals(finalUserId)).findFirst().orElse(null);
                if (myDetail != null && StringUtils.hasText(myDetail.getContestAccount())) {
                    List<MySignupInfoVO.MemberCredential> creds = new ArrayList<>();
                    MySignupInfoVO.MemberCredential mc = new MySignupInfoVO.MemberCredential();
                    Users u = userMapper.selectById(myDetail.getUserId());
                    if (u != null) {
                        mc.setRealName(u.getRealName());
                        mc.setNumberId(u.getNumberId());
                    }
                    mc.setContestAccount(myDetail.getContestAccount());
                    mc.setContestPassword(myDetail.getContestPassword());
                    mc.setSeatNumber(myDetail.getSeatNumber());
                    creds.add(mc);
                    vo.setMemberCredentials(creds);
                } else {
                    vo.setContestAccount(mySignup.getContestAccount());
                    vo.setContestPassword(mySignup.getContestPassword());
                    vo.setSeatNumber(mySignup.getSeatNumber());
                }
            }
        }
        return Result.success(vo);
    }

    @PostMapping("/signup-preview")
    public Result<MySignupInfoVO> signupPreview(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");
        Long teamId = params.get("teamId") != null ? Long.valueOf(params.get("teamId").toString()) : null;
        Integer isRanking = params.get("isRanking") != null ? Integer.valueOf(params.get("isRanking").toString()) : 1;
        String advisor = AdvisorNames.normalize(params.get("advisor"));
        String inputType = null;
        if (params.get("teamType") != null) inputType = params.get("teamType").toString();
        else if (params.get("type") != null) inputType = params.get("type").toString();

        Event event = eventMapper.selectById(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);

        MySignupInfoVO vo = new MySignupInfoVO();
        vo.setIsRanking(isRanking);
        vo.setAdvisor(advisor);
        
        String finalType = inputType;
        if (event.getIsTeam() == 0) {
            vo.setSignupType("personal");
            if (StringUtils.hasText(inputType)) vo.setTeamTypeDisplay(inputType);
            
            Users u = userMapper.selectById(userId);
            if (u != null) {
                MySignupInfoVO.SimpleMemberInfo sm = toSimpleMemberInfo(u, false);
                vo.setMembers(Collections.singletonList(sm));
            }
        } else {
            vo.setSignupType("team");
            if ("GPLT".equalsIgnoreCase(event.getType())) {
                vo.setTeamTypeDisplay(inputType);
            } else {
                if ("girl".equals(inputType)) {
                    vo.setTeamTypeDisplay("女生队伍");
                } else {
                    vo.setTeamTypeDisplay(isRanking == 1 ? "正式队伍" : "打星队伍");
                }
            }
            
            if (teamId == null) return Result.error("未选择队伍");
            Team team = teamMapper.selectById(teamId);
            if (team == null) return Result.error("队伍不存在");
            if (!team.getLeaderId().equals(userId)) return Result.error("仅队长可报名");
            vo.setTeamName(team.getTeamName());

            List<TeamMember> members = teamMemberMapper.selectList(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("status", "1"));
            Set<Long> memberIds = new HashSet<>();
            memberIds.add(team.getLeaderId());
            for (TeamMember tm : members) memberIds.add(tm.getMemberId());

            if (!memberIds.isEmpty()) {
                List<Users> users = userMapper.selectBatchIds(memberIds);
                Result<String> idPhotoCheck = validateMembersIdPhoto(users);
                if (idPhotoCheck != null) return Result.error(idPhotoCheck.getMsg());
                List<MySignupInfoVO.SimpleMemberInfo> simpleMembers = new ArrayList<>();
                for (Users u : users) {
                    simpleMembers.add(toSimpleMemberInfo(u, u.getUserId().equals(team.getLeaderId())));
                }
                simpleMembers.sort((a, b) -> {
                    if (a.isCaptain()) return -1;
                    if (b.isCaptain()) return 1;
                    return 0;
                });
                vo.setMembers(simpleMembers);
            }
        }
        return Result.success(vo);
    }

    @GetMapping(value = "/admission-ticket", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> downloadAdmissionTicket(@RequestParam Long eventId, HttpServletRequest request) {
        try {
            Long userId = SecurityUtils.currentUserId(request);
            if (userId == null) return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("未登录或会话已失效");
            Event event = eventMapper.selectById(eventId);
            if (event == null) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("赛事不存在");
            if (EventRosterService.isRoster(event) && !eventRoster.eligible(eventId,userId)) return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("当前实名身份未匹配到确定名单");
            Users user = userMapper.selectById(userId);
            if (user == null) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("用户不存在");
            if (!Boolean.TRUE.equals(event.getAdmissionTicketOpen())) {
                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("管理员尚未开放参赛证明下载");
            }
            if (event.getEventEnd() != null && !LocalDateTime.now().isBefore(event.getEventEnd())) {
                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("比赛已结束，不能下载参赛证明");
            }

            byte[] pdf;
            if (event.getIsTeam() == 0) {
                SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", userId));
                if (signup == null) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("未报名");
                if (!"approved".equals(signup.getStatus())) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("报名尚未通过，不能下载参赛证明");
                if (!admissionTicketPdfService.hasCredential(signup)) {
                    return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("管理员尚未导入参赛证明信息，暂不能下载");
                }
                pdf = admissionTicketPdfService.renderPersonal(event, signup, user);
            } else {
                SignupTeam signup = signupTeamMapper.selectLatestByMember(eventId, userId);
                if (signup == null) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("未报名");
                if (!"approved".equals(signup.getStatus())) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("报名尚未通过，不能下载参赛证明");
                List<SignupTeamDetail> details = getSignupDetailsOrdered(signup.getSignupId());
                SignupTeamDetail myDetail = details.stream().filter(d -> userId.equals(d.getUserId())).findFirst().orElse(null);
                if (myDetail == null) return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("未报名");
                if (!admissionTicketPdfService.hasCredential(signup, myDetail)) {
                    return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("管理员尚未导入参赛证明信息，暂不能下载");
                }
                boolean captainView = !details.isEmpty() && userId.equals(details.get(0).getUserId());
                List<Users> ticketMembers = captainView ? loadUsersForDetails(details) : List.of(user);
                pdf = admissionTicketPdfService.renderTeam(event, signup, myDetail, user, ticketMembers);
            }

            String fileName = URLEncoder.encode(event.getName() + "_参赛证明.pdf", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                    .body(pdf);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EventController.class).warn("操作失败，请检查请求参数和服务配置");
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body("参赛证明生成失败：" + e.getMessage());
        }
    }

    @PostMapping("/admin/admission-ticket-open")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateAdmissionTicketOpen(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");
        if (!checkPermission(operatorId)) return Result.error("无权操作");
        if (params.get("eventId") == null || params.get("open") == null) return Result.error("参数不完整");
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        event.setAdmissionTicketOpen(Boolean.parseBoolean(params.get("open").toString()));
        eventMapper.updateById(event);
        return Result.success(Boolean.TRUE.equals(event.getAdmissionTicketOpen()) ? "已开放参赛证明下载" : "已关闭参赛证明下载");
    }

    @PostMapping("/admin/admission-ticket-rules")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateAdmissionTicketRules(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");
        if (!checkPermission(operatorId)) return Result.error("无权操作");
        if (params.get("eventId") == null) return Result.error("参数不完整");
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        event.setAdmissionTicketRules(params.get("rules") == null ? "" : params.get("rules").toString());
        eventMapper.updateById(event);
        return Result.success("参赛证明参赛选手须知已保存");
    }

    @PostMapping("/signup")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> signup(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");
        Long teamId = params.get("teamId") != null ? Long.valueOf(params.get("teamId").toString()) : null;

        Integer isRanking = params.get("isRanking") != null ? Integer.valueOf(params.get("isRanking").toString()) : 1;
        String advisor = AdvisorNames.normalize(params.get("advisor"));

        String inputType = null;
        if (params.get("teamType") != null) inputType = params.get("teamType").toString();
        else if (params.get("type") != null) inputType = params.get("type").toString();

        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);

        String eventType = event.getType();

        Users user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        if (!"normal".equals(user.getAccountStatus())) return Result.error("账号状态异常，无法报名");
        if (user.getIdPhotoFileId() == null) return Result.error("请先在个人信息页面上传证件照后再报名");

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getSignupStart())) return Result.error("报名未开始");
        if (event.getSignupEnd() != null && !now.isBefore(event.getSignupEnd())) return Result.error("报名已截止");

        if (event.getIsTeam() == 0) {
            String finalPersonalType = null;
            if ("LANQIAO".equalsIgnoreCase(eventType)) {
                if (!StringUtils.hasText(inputType)) return Result.error("报名蓝桥杯赛制必须选择参赛组别");
                if (!LANQIAO_TYPES.contains(inputType)) return Result.error("无效的组别类型");
                finalPersonalType = inputType;
            } else if (!"GPLT".equalsIgnoreCase(eventType)) {
                // XCPC 个人赛
                if ("girl".equals(inputType)) {
                    if (user.getGender() == null || user.getGender() != 2) {
                        return Result.error("女生队伍要求参赛选手为女性");
                    }
                    finalPersonalType = "girl";
                } else if ("official".equals(inputType)) {
                    if (user.getGender() != null && user.getGender() == 2) {
                        finalPersonalType = "girl";
                    } else {
                        finalPersonalType = "official";
                    }
                } else if ("star".equals(inputType)) {
                    finalPersonalType = "star";
                }
            }

            Long count = signupPersonalMapper.countApproved(eventId);
            if (event.getMaxSignup() != null && count >= event.getMaxSignup()) return Result.error("名额已满");

            if (signupTeamMapper.countActiveByMember(eventId, userId) > 0) {
                return Result.error("您已随队伍报名此比赛");
            }

            SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", userId));

            if (signup != null) {
                if ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus()))
                    return Result.error("已报名");
                signup.setStatus("pending");
                if (signup.getSignupTime() == null) signup.setSignupTime(now);
                signup.setIsRanking(isRanking);
                signup.setAdvisor(advisor);
                if (finalPersonalType != null) signup.setType(finalPersonalType);
                signup.setSignupSource("self");
                signup.setCreatedByUserId(userId);
                signupPersonalMapper.updateById(signup);
            } else {
                signup = new SignupPersonal();
                signup.setEventId(eventId);
                signup.setUserId(userId);
                signup.setStatus("pending");
                if (signup.getSignupTime() == null) signup.setSignupTime(now);
                signup.setIsRanking(isRanking);
                signup.setAdvisor(advisor);
                if (finalPersonalType != null) signup.setType(finalPersonalType);
                signup.setSignupSource("self");
                signup.setCreatedByUserId(userId);
                signupPersonalMapper.insert(signup);
            }
            Map<String, String> targets = new HashMap<>();
            targets.put(user.getEmail(), user.getRealName());
            signupNotifications.enqueue(eventId, "personal", signup.getSignupId(), targets, event.getName(), null, "pending", null);

        } else {
            String finalTeamType = "official";

            if ("GPLT".equalsIgnoreCase(eventType)) {
                if (!StringUtils.hasText(inputType)) return Result.error("报名GPLT赛制必须选择参赛组别");
                if (!GPLT_TYPES.contains(inputType))
                    return Result.error("无效的组别类型，请选择：星芒初绽、长风破浪、凌霄问鼎");
                finalTeamType = inputType;
            } else {
                if (inputType != null) {
                    if (!"official".equals(inputType) && !"star".equals(inputType)) {
                        finalTeamType = "official";
                    } else {
                        finalTeamType = inputType;
                    }
                }
            }

            if (teamId == null) return Result.error("必须选择队伍");
            Team team = teamMapper.selectById(teamId);
            if (team == null) return Result.error("队伍不存在");
            if (!team.getLeaderId().equals(userId)) return Result.error("仅队长可报名");
            String snapshotTeamName = normalizeTeamName(team.getTeamName());
            if (!StringUtils.hasText(snapshotTeamName)) return Result.error("队伍名称不能为空");

            List<TeamMember> members = teamMemberMapper.selectList(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("status", "1"));
            List<Long> snapshotMemberIds = collectTeamMemberIds(team, members);
            List<Users> snapshotMembers = loadUsersByIdsInOrder(snapshotMemberIds);
            Result<String> idPhotoCheck = validateMembersIdPhoto(snapshotMembers);
            if (idPhotoCheck != null) return idPhotoCheck;

            long totalCount = snapshotMemberIds.size();

            if (totalCount < event.getMinMember() || totalCount > event.getMaxMember()) {
                return Result.error("队伍人数不符合要求 (" + event.getMinMember() + "-" + event.getMaxMember() + "人)，当前" + totalCount + "人");
            }

            if ("official".equals(finalTeamType)) {
                boolean isAllFemale = isAllGirls(snapshotMembers);
                if (isAllFemale) finalTeamType = "girl";
            }

            if ("girl".equals(finalTeamType)) {
                if (!isAllGirls(snapshotMembers)) {
                    return Result.error("女生队伍要求所有成员均为女性");
                }
            }

            String memberConflict = signupPolicy.memberConflict(eventId, snapshotMemberIds, null, null);
            if (memberConflict != null) return Result.error(memberConflict);

            Long count = signupTeamMapper.countApproved(eventId);
            if (event.getMaxSignup() != null && count >= event.getMaxSignup()) return Result.error("队伍名额已满");

            SignupTeam signup;
            try {
                signup = signupTeamIdentity.findForTeam(eventId, team);
            } catch (IllegalArgumentException ex) {
                return Result.error(ex.getMessage());
            }

            if (signup != null) {
                if ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus()))
                    return Result.error("该队名已报名");
                signup.setStatus("pending");
                if (signup.getSignupTime() == null) signup.setSignupTime(now);
                signup.setTeamName(snapshotTeamName);
                signup.setIsRanking(isRanking);
                signup.setAdvisor(advisor);
                signup.setType(finalTeamType);
                signup.setSignupSource("self");
                signup.setCreatedByUserId(userId);
                signupTeamMapper.updateById(signup);
                replaceSignupTeamDetails(signup.getSignupId(), snapshotMemberIds);
            } else {
                signup = new SignupTeam();
                signup.setSourceTeamId(team.getTeamId());
                signup.setEventId(eventId);
                signup.setTeamName(snapshotTeamName);
                signup.setStatus("pending");
                if (signup.getSignupTime() == null) signup.setSignupTime(now);
                signup.setIsRanking(isRanking);
                signup.setAdvisor(advisor);
                signup.setType(finalTeamType);
                signup.setSignupSource("self");
                signup.setCreatedByUserId(userId);
                signupTeamMapper.insert(signup);
                replaceSignupTeamDetails(signup.getSignupId(), snapshotMemberIds);
            }

            Map<String, String> targets = new HashMap<>();
            for (Users member : snapshotMembers) {
                targets.put(member.getEmail(), member.getRealName());
            }
            signupNotifications.enqueue(eventId, "team", signup.getSignupId(), targets, event.getName(), snapshotTeamName, "pending", null);
        }

        return Result.success("报名成功");
    }

    @PostMapping("/cancel")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancel(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long eventId = params.get("eventId");
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");

        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);

        LocalDateTime now = LocalDateTime.now();
        if (signupPolicy.isCancellationClosed(event, now)) {
            return Result.error("报名已结束，无法取消报名");
        }

        // Retain the original row and time so cancellation does not reset signup priority.
        if (event.getIsTeam() == 0) {
            SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", userId));
            if (signup != null) {
                signup.setStatus("cancelled");
                signupPersonalMapper.updateById(signup);
                signupCredentials.clearPersonal(signup.getSignupId());
                signupNotifications.invalidate("personal", signup.getSignupId());
            }
        } else {
            SignupTeam signup = signupTeamMapper.selectLatestByMember(eventId, userId);
            if (signup != null) {
                List<SignupTeamDetail> details = getSignupDetailsOrdered(signup.getSignupId());
                if (!details.isEmpty() && !userId.equals(details.get(0).getUserId())) {
                    return Result.error("只有队长可以取消团队赛报名");
                }
                signup.setStatus("cancelled");
                signupTeamMapper.updateById(signup);
                signupCredentials.clearTeam(signup.getSignupId());
                signupNotifications.invalidate("team", signup.getSignupId());
            }
        }
        return Result.success("已取消");
    }

    @GetMapping("/public/signups")
    public Result<Page<EventSignupVO>> publicSignups(@RequestParam Long eventId, @RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String keyword, @RequestParam(required = false) String teamType) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) return Result.error("赛事不存在");

        current = SecurityUtils.normalizePage(current);
        size = SecurityUtils.clampPageSize(size);
        if (EventRosterService.isRoster(event)) return Result.success(eventRoster.publicPage(eventId,current,size,keyword,teamType));
        Page<EventSignupVO> resultPage = new Page<>(current, size);
        List<EventSignupVO> voList = new ArrayList<>();
        Map<EventSignupVO, Long> organizationUsers = new LinkedHashMap<>();

        if (event.getIsTeam() == 0) {
            Page<SignupPersonal> pageParam = new Page<>(current, size);
            pageParam.setOptimizeCountSql(false); // Disable count optimization
            Page<SignupPersonal> page = signupPersonalMapper.selectPublicSignupPage(pageParam, eventId, "approved", teamType, keyword);
            resultPage.setTotal(page.getTotal());

            for (SignupPersonal sp : page.getRecords()) {
                Users u = userMapper.selectById(sp.getUserId());
                EventSignupVO vo = new EventSignupVO();
                // 仅返回公示所需字段
                // 个人赛：学院、姓名、年级、参赛组别、指导老师、是否参与排名
                vo.setRealName(u.getRealName());
                vo.setGradeYear(u.getGradeYear());
                vo.setTeamType(sp.getType());
                vo.setAdvisor(sp.getAdvisor());
                vo.setIsRanking(sp.getIsRanking());
                fillUserSchoolInfo(vo, u);
                organizationUsers.put(vo, sp.getUserId());
                voList.add(vo);
            }
        } else {
            Page<SignupTeam> pageParam = new Page<>(current, size);
            pageParam.setOptimizeCountSql(false); // Disable count optimization
            Page<SignupTeam> page = signupTeamMapper.selectPublicSignupPage(pageParam, eventId, "approved", teamType, keyword);
            resultPage.setTotal(page.getTotal());

            for (SignupTeam st : page.getRecords()) {
                List<Users> members = loadSignupUsers(st.getSignupId());
                EventSignupVO vo = new EventSignupVO();
                // 团队赛：学院、队伍名称、队伍类型、队伍成员、指导老师、是否参与排名
                vo.setTeamName(st.getTeamName());
                vo.setTeamType(st.getType());
                vo.setAdvisor(st.getAdvisor());
                vo.setIsRanking(st.getIsRanking());

                // 获取队长信息以填充学院
                Users leader = members.isEmpty() ? null : members.get(0);
                fillUserSchoolInfo(vo, leader);
                if (leader != null) organizationUsers.put(vo, leader.getUserId());

                // 获取成员名单
                String names = members.stream().map(Users::getRealName).collect(Collectors.joining(", "));
                vo.setMembersContent(names);
                
                voList.add(vo);
            }
        }
        externalBoards.enrichSignupOrganizations(eventId, organizationUsers);
        resultPage.setRecords(voList);
        return Result.success(resultPage);
    }

    @GetMapping("/admin/signups")
    public Result<Page<EventSignupVO>> listSignups(@RequestParam Long eventId, @RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(required = false) String teamType, @RequestParam(defaultValue = "summary") String viewType) {

        Event event = eventMapper.selectById(eventId);
        if (event == null) return Result.error("赛事不存在");

        current = SecurityUtils.normalizePage(current);
        size = SecurityUtils.clampPageSize(size);
        Page<EventSignupVO> resultPage = new Page<>(current, size);
        List<EventSignupVO> voList = new ArrayList<>();

        if (event.getIsTeam() == 0) {
            Page<SignupPersonal> page = signupPersonalMapper.selectSignupPage(new Page<>(current, size), eventId, status, teamType, keyword);
            resultPage.setTotal(page.getTotal());

            for (SignupPersonal sp : page.getRecords()) {
                Users u = userMapper.selectById(sp.getUserId());
                // keyword filtered in mapper

                EventSignupVO vo = new EventSignupVO();
                vo.setSignupId(sp.getSignupId());
                vo.setEventId(eventId);
                vo.setUserId(u.getUserId());
                vo.setRealName(u.getRealName());
                vo.setGender(convertGender(u.getGender()));
                vo.setNumberId(u.getNumberId());
                vo.setClassName(u.getClassName());
                vo.setGradeYear(u.getGradeYear()); // 🔴 新增：设置年级
                vo.setSignupTime(sp.getSignupTime());
                vo.setStatus(sp.getStatus());
                vo.setAdvisor(sp.getAdvisor());
                vo.setIsRanking(sp.getIsRanking());
                vo.setContestAccount(sp.getContestAccount());
                vo.setContestPassword(sp.getContestPassword());
                vo.setSeatNumber(sp.getSeatNumber());
                vo.setTeamType(sp.getType());
                fillUserSchoolInfo(vo, u);
                voList.add(vo);
            }
        } else {
            Page<SignupTeam> page = signupTeamMapper.selectSignupPage(new Page<>(current, size), eventId, status, teamType, keyword);
            resultPage.setTotal(page.getTotal());

            for (SignupTeam st : page.getRecords()) {
                List<SignupTeamDetail> signupDetails = getSignupDetailsOrdered(st.getSignupId());
                List<Users> allMembers = loadUsersForDetails(signupDetails);
                Users leader = allMembers.isEmpty() ? null : allMembers.get(0);

                if ("detail".equals(viewType)) {
                    for (int i = 0; i < allMembers.size(); i++) {
                        Users member = allMembers.get(i);
                        EventSignupVO vo = new EventSignupVO();
                        vo.setSignupId(st.getSignupId());
                        vo.setEventId(eventId);
                        vo.setTeamName(st.getTeamName());
                        vo.setSignupTime(st.getSignupTime());
                        vo.setStatus(st.getStatus());
                        vo.setAdvisor(st.getAdvisor());
                        vo.setIsRanking(st.getIsRanking());
                        vo.setTeamType(st.getType());
                        vo.setRealName(member.getRealName());
                        vo.setGender(convertGender(member.getGender()));
                        vo.setNumberId(member.getNumberId());
                        vo.setClassName(member.getClassName());
                        vo.setGradeYear(member.getGradeYear()); // 🔴 新增：设置年级
                        fillUserSchoolInfo(vo, member);

                        SignupTeamDetail detail = i < signupDetails.size() ? signupDetails.get(i) : null;
                        if (detail != null) {
                            vo.setContestAccount(detail.getContestAccount());
                            vo.setContestPassword(detail.getContestPassword());
                            vo.setSeatNumber(detail.getSeatNumber());
                        } else {
                            vo.setContestAccount(st.getContestAccount());
                            vo.setContestPassword(st.getContestPassword());
                            vo.setSeatNumber(st.getSeatNumber());
                        }
                        voList.add(vo);
                    }
                } else {
                    EventSignupVO vo = new EventSignupVO();
                    vo.setSignupId(st.getSignupId());
                    vo.setEventId(eventId);
                    vo.setTeamName(st.getTeamName());
                    vo.setSignupTime(st.getSignupTime());
                    vo.setStatus(st.getStatus());
                    vo.setAdvisor(st.getAdvisor());
                    vo.setIsRanking(st.getIsRanking());
                    vo.setTeamType(st.getType());
                    vo.setContestAccount(st.getContestAccount());
                    vo.setContestPassword(st.getContestPassword());
                    vo.setSeatNumber(st.getSeatNumber());
                    fillUserSchoolInfo(vo, leader);
                    String names = allMembers.stream().map(Users::getRealName).collect(Collectors.joining(","));
                    vo.setMembersContent(names);
                    voList.add(vo);
                }
            }
        }
        resultPage.setRecords(voList);
        return Result.success(resultPage);
    }
    
    // ... rest of the file
    @PostMapping("/admin/batch-update-status")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> batchUpdateStatus(@RequestBody Map<String, Object> params) {
        if (params.get("eventId") == null || params.get("status") == null) {
            return Result.error("参数不完整");
        }
        Long eventId = Long.valueOf(params.get("eventId").toString());
        List<Long> ids = parseLongIdList(params.get("signupIds"));
        String status = params.get("status").toString();
        String reason = params.get("reason") != null ? params.get("reason").toString() : null;

        if (ids.isEmpty()) return Result.error("未选择任何记录");
        if (!isValidAdminSignupStatus(status)) return Result.error("状态不合法");

        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if ("cancelled".equals(status) && signupPolicy.isCancellationClosed(event, LocalDateTime.now()))
            return Result.error("报名已结束，无法取消报名");

        Set<Long> uniqueSignupIds = new LinkedHashSet<>(ids);
        List<SignupPersonal> personal = event.getIsTeam() == 0
                ? signupPersonalMapper.selectBatchIds(uniqueSignupIds) : List.of();
        List<SignupTeam> teams = event.getIsTeam() == 1
                ? signupTeamMapper.selectBatchIds(uniqueSignupIds) : List.of();
        if (personal.size() + teams.size() != uniqueSignupIds.size()
                || personal.stream().anyMatch(row -> !eventId.equals(row.getEventId()))
                || teams.stream().anyMatch(row -> !eventId.equals(row.getEventId()))) {
            return Result.error("报名记录不存在或不属于此比赛");
        }
        if ("approved".equals(status)) {
            long additions = personal.stream().filter(row -> !"approved".equals(row.getStatus())).count()
                    + teams.stream().filter(row -> !"approved".equals(row.getStatus())).count();
            if (signupPolicy.exceedsCapacity(event, signupPolicy.approvedCount(event) + additions)) {
                return Result.error("审核通过后将超过报名名额上限，请减少选择数量");
            }
        }
        // Validate the entire batch before writing anything, including reactivation.
        if ("approved".equals(status) || "pending".equals(status)) {
            Set<Long> selectedMembers = new HashSet<>();
            for (SignupPersonal row : personal) {
                if (!selectedMembers.add(row.getUserId())) return Result.error("所选报名存在重复成员");
                String conflict = signupPolicy.memberConflict(eventId, List.of(row.getUserId()), row.getSignupId(), null);
                if (conflict != null) return Result.error(conflict);
            }
            for (SignupTeam row : teams) {
                List<Long> members = getSignupDetailsOrdered(row.getSignupId()).stream()
                        .map(SignupTeamDetail::getUserId).toList();
                for (Long member : members) {
                    if (!selectedMembers.add(member)) return Result.error("所选队伍存在重复成员");
                }
                String conflict = signupPolicy.memberConflict(eventId, members, null, row.getSignupId());
                if (conflict != null) return Result.error(conflict);
            }
        }
        for (SignupPersonal row : personal) {
            if ("cancelled".equals(status)) {
                row.setStatus("cancelled");
                signupPersonalMapper.updateById(row);
            } else if (!status.equals(row.getStatus())) {
                row.setStatus(status);
                signupPersonalMapper.updateById(row);
                Users user = userMapper.selectById(row.getUserId());
                if (user != null) {
                    Map<String, String> targets = new HashMap<>();
                    targets.put(user.getEmail(), user.getRealName());
                    signupNotifications.enqueue(eventId, "personal", row.getSignupId(), targets, event.getName(), null, status, reason);
                }
            }
            if ("cancelled".equals(status) || "rejected".equals(status)) {
                signupCredentials.clearPersonal(row.getSignupId());
                if ("cancelled".equals(status)) signupNotifications.invalidate("personal", row.getSignupId());
            }
        }
        for (SignupTeam row : teams) {
            if ("cancelled".equals(status)) {
                row.setStatus("cancelled");
                signupTeamMapper.updateById(row);
            } else if (!status.equals(row.getStatus())) {
                row.setStatus(status);
                signupTeamMapper.updateById(row);
                signupNotifications.enqueue(eventId, "team", row.getSignupId(), getSignupTeamEmailTargets(row.getSignupId()), event.getName(), row.getTeamName(), status, reason);
            }
            if ("cancelled".equals(status) || "rejected".equals(status)) {
                signupCredentials.clearTeam(row.getSignupId());
                if ("cancelled".equals(status)) signupNotifications.invalidate("team", row.getSignupId());
            }
        }
        return Result.success("批量操作成功");
    }

    @PostMapping("/admin/batch-import")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchImport(@RequestBody Map<String, Object> params) {
        if (params.get("eventId") == null || params.get("content") == null) {
            return Result.error("参数不完整");
        }
        Long eventId = Long.valueOf(params.get("eventId").toString());
        String tsvContent = params.get("content").toString();
        boolean preview = Boolean.parseBoolean(String.valueOf(params.getOrDefault("preview", false)));
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);

        // Run exactly the real import path, including member replacements and DB
        // constraints. The preview transaction (including its outbox) never commits.
        if (preview) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        long approvedCount = signupPolicy.approvedCount(event);
        Set<String> previewApprovedKeys = new HashSet<>();
        String[] lines = tsvContent.split("\\r?\\n");
        int successCount = 0;
        int failCount = 0;
        List<Map<String, String>> failedList = new ArrayList<>();
        List<Map<String, String>> successList = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (StringUtils.isEmpty(line)) continue;
            String[] parts = line.split("\t", -1);

            if (event.getIsTeam() == 0) {
                if (parts.length < 3) {
                    failCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("content", line);
                    f.put("reason", "格式错误");
                    failedList.add(f);
                    continue;
                }
                String universityName = parts[0].trim();
                String numberId = parts[1].trim();
                String realName = parts[2].trim();
                String type = parts.length > 3 ? parseTeamType(parts[3].trim()) : null;
                Integer isRanking = parts.length > 4 ? (parts[4].trim().equals("否") || parts[4].trim().equals("0") ? 0 : 1) : 1;
                String advisor = parts.length > 5 ? parts[5].trim() : null;

                Users user = findUserBySchoolAndNumber(universityName, numberId);
                if (user == null || !user.getRealName().equals(realName)) {
                    failCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("schoolName", universityName);
                    f.put("numberId", numberId);
                    f.put("reason", user == null ? "学校与学号对应的用户不存在" : "姓名不匹配");
                    failedList.add(f);
                    continue;
                }
                try {
                    type = resolveImportedSignupType(event, type, Collections.singletonList(user));
                } catch (IllegalArgumentException e) {
                    failCount++;
                    failedList.add(Map.of("content", line, "reason", e.getMessage()));
                    continue;
                }
                SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", user.getUserId()));
                String importKey = "personal:" + user.getUserId();
                String conflict = signupPolicy.memberConflict(eventId, List.of(user.getUserId()), signup == null ? null : signup.getSignupId(), null);
                boolean addsPlace = (signup == null || !"approved".equals(signup.getStatus())) && !previewApprovedKeys.contains(importKey);
                if (conflict != null || (addsPlace && signupPolicy.exceedsCapacity(event, approvedCount + 1))) {
                    failCount++;
                    failedList.add(Map.of("content", line, "reason", conflict != null ? conflict : "报名名额已满"));
                    continue;
                }
                if (addsPlace) approvedCount++;
                previewApprovedKeys.add(importKey);
                boolean willCreate = signup == null;
                {
                    if (signup == null) {
                        signup = new SignupPersonal();
                        signup.setEventId(eventId);
                        signup.setUserId(user.getUserId());
                        signup.setStatus("approved");
                        signup.setSignupTime(LocalDateTime.now());
                        signup.setIsRanking(isRanking);
                        signup.setAdvisor(advisor);
                        if (StringUtils.hasText(type)) signup.setType(type);
                        signup.setSignupSource("admin_import");
                        signupPersonalMapper.insert(signup);
                    } else {
                        signup.setStatus("approved");
                        signup.setIsRanking(isRanking);
                        signup.setAdvisor(advisor);
                        if (StringUtils.hasText(type)) signup.setType(type);
                        signup.setSignupSource("admin_import");
                        signupPersonalMapper.updateById(signup);
                    }
                    Map<String, String> targets = new HashMap<>();
                    targets.put(user.getEmail(), user.getRealName());
                    signupNotifications.enqueue(eventId, "personal", signup.getSignupId(), targets, event.getName(), null, "approved", null);
                }
                Map<String, String> successMap = new HashMap<>();
                successMap.put("schoolName", universityName);
                successMap.put("numberId", numberId);
                successMap.put("realName", realName);
                successMap.put("reason", (willCreate ? "将新增报名" : "将更新报名") + "（" + formatTeamType(type, event.getIsTeam() == 1) + "）");
                successList.add(successMap);
                successCount++;
            } else {
                if (parts.length < 1) {
                    failCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("content", line);
                    f.put("reason", "格式错误");
                    failedList.add(f);
                    continue;
                }
                String teamName = normalizeTeamName(parts[0]);
                String type = parts.length > 1 ? parseTeamType(parts[1].trim()) : "official";
                Integer isRanking = parts.length > 2 ? (parts[2].trim().equals("否") || parts[2].trim().equals("0") ? 0 : 1) : 1;
                String advisor = parts.length > 3 ? parts[3].trim() : null;

                List<Team> matchedTeams = teamMapper.selectList(new QueryWrapper<Team>().eq("team_name", teamName).orderByAsc("team_id"));
                if (matchedTeams.isEmpty()) {
                    failCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("content", teamName);
                    f.put("reason", "队伍不存在");
                    failedList.add(f);
                    continue;
                }
                if (matchedTeams.size() > 1) {
                    failCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("content", teamName);
                    f.put("reason", "存在多个同名队伍，请使用队长在线报名");
                    failedList.add(f);
                    continue;
                }
                Team team = matchedTeams.get(0);

                List<Long> memberIds = collectTeamMemberIds(team, teamMemberMapper.selectList(new QueryWrapper<TeamMember>().eq("team_id", team.getTeamId()).eq("status", "1")));
                try {
                    List<Users> members = loadUsersByIdsInOrder(memberIds);
                    if (members.size() != memberIds.size()) throw new IllegalArgumentException("队伍成员信息不完整");
                    type = resolveImportedSignupType(event, type, members);
                } catch (IllegalArgumentException e) {
                    failCount++;
                    failedList.add(Map.of("content", teamName, "reason", e.getMessage()));
                    continue;
                }
                SignupTeam signup;
                try {
                    signup = signupTeamIdentity.findForTeam(eventId, team);
                } catch (IllegalArgumentException ex) {
                    failCount++;
                    failedList.add(Map.of("content", teamName, "reason", ex.getMessage()));
                    continue;
                }
                String importKey = "team:" + teamName;
                String conflict = signupPolicy.memberConflict(eventId, memberIds, null, signup == null ? null : signup.getSignupId());
                boolean addsPlace = (signup == null || !"approved".equals(signup.getStatus())) && !previewApprovedKeys.contains(importKey);
                if (conflict != null || (addsPlace && signupPolicy.exceedsCapacity(event, approvedCount + 1))) {
                    failCount++;
                    failedList.add(Map.of("content", teamName, "reason", conflict != null ? conflict : "队伍名额已满"));
                    continue;
                }
                if (addsPlace) approvedCount++;
                previewApprovedKeys.add(importKey);
                boolean willCreate = signup == null;
                {
                    if (signup == null) {
                        signup = new SignupTeam();
                        signup.setSourceTeamId(team.getTeamId());
                        signup.setEventId(eventId);
                        signup.setTeamName(teamName);
                        signup.setStatus("approved");
                        signup.setSignupTime(LocalDateTime.now());
                        signup.setIsRanking(isRanking);
                        signup.setAdvisor(advisor);
                        signup.setType(type);
                        signup.setSignupSource("admin_import");
                        signupTeamMapper.insert(signup);
                        replaceSignupTeamDetails(signup.getSignupId(), memberIds);
                    } else {
                        signup.setStatus("approved");
                        signup.setTeamName(teamName);
                        signup.setIsRanking(isRanking);
                        signup.setAdvisor(advisor);
                        signup.setType(type);
                        signup.setSignupSource("admin_import");
                        signupTeamMapper.updateById(signup);
                        replaceSignupTeamDetails(signup.getSignupId(), memberIds);
                    }

                    Map<String, String> targets = getSignupTeamEmailTargets(signup.getSignupId());
                    signupNotifications.enqueue(eventId, "team", signup.getSignupId(), targets, event.getName(), teamName, "approved", null);
                }
                Map<String, String> successMap = new HashMap<>();
                successMap.put("teamName", teamName);
                successMap.put("reason", (willCreate ? "将新增报名" : "将更新报名") + "（" + formatTeamType(type, event.getIsTeam() == 1) + "）");
                successList.add(successMap);
                successCount++;
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", successCount);
        res.put("failure", failCount);
        res.put("failedList", failedList);
        res.put("successList", successList);
        return Result.success(res);
    }

    private boolean isValidAdminSignupStatus(String status) {
        return "approved".equals(status)
                || "rejected".equals(status)
                || "cancelled".equals(status)
                || "pending".equals(status);
    }

    private Result<String> validateEventDTO(EventDTO dto) {
        if (dto != null && dto.getSignupMode() != null && !List.of("open","roster").contains(dto.getSignupMode())) return Result.error("无效的报名方式");
        if (dto != null && "roster".equals(dto.getSignupMode()) && !Integer.valueOf(0).equals(dto.getIsTeam())) return Result.error("固定名单仅支持个人赛");

        if (dto == null) return Result.error("参数不能为空");
        if (dto.getCategoryId() == null) return Result.error("赛事分类不能为空");
        if (dto.getTermId() == null) return Result.error("学期不能为空");
        if (!StringUtils.hasText(dto.getName()) || dto.getName().trim().length() > 100) return Result.error("赛事名称不能为空且不能超过100字");
        dto.setName(dto.getName().trim());
        if (dto.getIsTeam() == null || (dto.getIsTeam() != 0 && dto.getIsTeam() != 1)) return Result.error("赛事报名类型不合法");
        if (dto.getSignupStart() == null || dto.getEventStart() == null || dto.getEventEnd() == null) {
            return Result.error("时间信息不能为空");
        }
        if (dto.getSignupEnd() != null && !dto.getSignupStart().isBefore(dto.getSignupEnd())) return Result.error("报名开始时间必须早于报名结束时间");
        if (!dto.getEventStart().isBefore(dto.getEventEnd())) return Result.error("比赛开始时间必须早于比赛结束时间");
        if (dto.getMaxSignup() != null && (dto.getMaxSignup() < 1 || dto.getMaxSignup() > 100000)) return Result.error("报名人数上限不合法");
        if (dto.getIsTeam() == 1) {
            if (dto.getMinMember() == null || dto.getMaxMember() == null || dto.getMinMember() < 1 || dto.getMaxMember() < dto.getMinMember() || dto.getMaxMember() > 10) {
                return Result.error("队伍人数限制不合法");
            }
        }
        if (dto.getDescription() != null && dto.getDescription().length() > 20000) return Result.error("赛事介绍不能超过20000字");
        return null;
    }

    private List<Long> parseLongIdList(Object rawIds) {
        if (!(rawIds instanceof List<?> rawList)) return new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Object rawId : rawList) {
            if (rawId == null) continue;
            try {
                ids.add(Long.valueOf(rawId.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    @PostMapping("/admin/import-credentials")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> importCredentials(@RequestBody Map<String, Object> params) {
        if (params.get("eventId") == null || params.get("content") == null) return Result.error("参数不完整");
        Long eventId = Long.valueOf(params.get("eventId").toString());
        String content = params.get("content").toString();
        boolean preview = Boolean.parseBoolean(String.valueOf(params.getOrDefault("preview", false)));
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        String[] lines = content.split("\\r?\\n");
        int success = 0;
        int fail = 0;
        List<Map<String, String>> successList = new ArrayList<>();
        List<Map<String, String>> failedList = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (StringUtils.isEmpty(line)) continue;
            String[] parts = line.split("\t", -1);
            if (event.getIsTeam() == 0) {
                if (parts.length < 5) {
                    fail++;
                    failedList.add(Map.of("content", line, "reason", "格式错误，应包含学校、学号、账号、密码、座位号"));
                    continue;
                }
                String schoolName = parts[0].trim();
                String numberId = parts[1].trim();
                String account = parts[2].trim();
                String pwd = parts[3].trim();
                String seat = parts[4].trim();
                Users u = findUserBySchoolAndNumber(schoolName, numberId);
                if (u == null) {
                    fail++;
                    failedList.add(Map.of("content", schoolName + " / " + numberId, "reason", "学校与学号对应的用户不存在"));
                    continue;
                }
                SignupPersonal sp = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", eventId).eq("user_id", u.getUserId()));
                if (sp != null) {
                    if ("rejected".equals(sp.getStatus()) || "cancelled".equals(sp.getStatus())) {
                        fail++;
                        failedList.add(Map.of("content", schoolName + " / " + numberId, "reason", "报名已拒绝或取消，不能导入比赛凭证"));
                        continue;
                    }
                    if (!preview) {
                        sp.setContestAccount(account);
                        sp.setContestPassword(pwd);
                        sp.setSeatNumber(seat);
                        signupPersonalMapper.updateById(sp);
                    }
                    successList.add(Map.of("content", schoolName + " / " + numberId, "reason", "将更新个人比赛凭证"));
                    success++;
                } else {
                    fail++;
                    failedList.add(Map.of("content", schoolName + " / " + numberId, "reason", "该用户未报名本赛事"));
                }
            } else {
                if (parts.length < 6) {
                    fail++;
                    failedList.add(Map.of("content", line, "reason", "格式错误，应包含队伍名称、学校、成员学号、账号、密码、座位号"));
                    continue;
                }
                String teamName = normalizeTeamName(parts[0]);
                SignupTeam st = signupTeamMapper.selectOne(new QueryWrapper<SignupTeam>().eq("event_id", eventId).eq("team_name", teamName));
                if (st == null) {
                    fail++;
                    failedList.add(Map.of("content", teamName, "reason", "队伍未报名本赛事"));
                    continue;
                }
                if ("rejected".equals(st.getStatus()) || "cancelled".equals(st.getStatus())) {
                    fail++;
                    failedList.add(Map.of("content", teamName, "reason", "报名已拒绝或取消，不能导入比赛凭证"));
                    continue;
                }
                String schoolName = parts[1].trim();
                String stuId = parts[2].trim();
                if (StringUtils.hasText(schoolName) || StringUtils.hasText(stuId)) {
                    if (!StringUtils.hasText(schoolName) || !StringUtils.hasText(stuId)) {
                        fail++;
                        failedList.add(Map.of("content", teamName, "reason", "成员学校与学号必须同时填写"));
                        continue;
                    }
                    String account = parts[3].trim();
                    String pwd = parts[4].trim();
                    String seat = parts[5].trim();
                    Users u = findUserBySchoolAndNumber(schoolName, stuId);
                    if (u != null) {
                        SignupTeamDetail detail = signupTeamDetailMapper.selectOne(new QueryWrapper<SignupTeamDetail>().eq("signup_team_id", st.getSignupId()).eq("user_id", u.getUserId()));
                        if (detail == null) {
                            fail++;
                            failedList.add(Map.of("content", teamName + " / " + schoolName + " / " + stuId, "reason", "该用户不是报名队伍成员"));
                            continue;
                        }
                        if (!preview) {
                            detail.setContestAccount(account);
                            detail.setContestPassword(pwd);
                            detail.setSeatNumber(seat);
                            signupTeamDetailMapper.updateById(detail);
                        }
                        successList.add(Map.of("content", teamName + " / " + schoolName + " / " + stuId, "reason", "将更新队员比赛凭证"));
                        success++;
                    } else {
                        fail++;
                        failedList.add(Map.of("content", schoolName + " / " + stuId, "reason", "学校与学号对应的用户不存在"));
                    }
                } else {
                    String account = parts[3].trim();
                    String pwd = parts[4].trim();
                    String seat = parts[5].trim();
                    if (!preview) {
                        st.setContestAccount(account);
                        st.setContestPassword(pwd);
                        st.setSeatNumber(seat);
                        signupTeamMapper.updateById(st);
                    }
                    successList.add(Map.of("content", teamName, "reason", "将更新队伍共用比赛凭证"));
                    success++;
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failure", fail);
        result.put("successList", successList);
        result.put("failedList", failedList);
        return Result.success(result);
    }

    @GetMapping("/admin/export")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void exportSignups(@RequestParam Long eventId, @RequestParam(defaultValue = "summary") String viewType, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(required = false) String teamType, HttpServletResponse response) {
        try {
            Event event = eventMapper.selectById(eventId);
            if (event == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "赛事不存在");
                return;
            }
            List<EventSignupVO> list = new ArrayList<>();
            for (int current = 1; ; current++) {
                Result<Page<EventSignupVO>> result = listSignups(eventId, current, 100, keyword, status, teamType, viewType);
                if (result.getCode() != 200 || result.getData() == null) {
                    throw new IllegalStateException("读取报名名单失败");
                }
                Page<EventSignupVO> page = result.getData();
                list.addAll(page.getRecords());
                // Team detail pages expand each signup into multiple member rows.
                // Advance by signup pages, not the number of exported member rows.
                if (current >= page.getPages()) break;
            }
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("报名名单");
            List<String> headerList = new ArrayList<>();
            headerList.add("学院");
            if (event.getIsTeam() == 1) {
                headerList.add("队伍名称");
                if ("detail".equals(viewType)) {
                    headerList.add("姓名");
                    headerList.add("性别");
                    headerList.add("学号");
                    // headerList.add("学院"); // 移除重复的学院列
                    headerList.add("专业");
                    headerList.add("年级"); // 🔴 新增
                    headerList.add("行政班级");
                } else {
                    headerList.add("队伍成员");
                }
                headerList.add("队伍类型");
            } else {
                headerList.add("姓名");
                headerList.add("性别");
                headerList.add("学号");
                // headerList.add("学院"); // 移除重复的学院列
                headerList.add("专业");
                headerList.add("年级"); // 🔴 新增
                headerList.add("行政班级");
                headerList.add("参赛组别");
            }
            headerList.add("指导老师");
            headerList.add("是否排名");
            headerList.add("报名时间");
            headerList.add("状态");
            headerList.add("比赛账号");
            headerList.add("比赛密码");
            headerList.add("座位号");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headerList.size(); i++) {
                headerRow.createCell(i).setCellValue(headerList.get(i));
            }
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int r = 1;
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            for (EventSignupVO vo : list) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(vo.getSchoolName());
                if (event.getIsTeam() == 1) {
                    row.createCell(c++).setCellValue(vo.getTeamName());
                    if ("detail".equals(viewType)) {
                        row.createCell(c++).setCellValue(vo.getRealName());
                        row.createCell(c++).setCellValue(vo.getGender());
                        row.createCell(c++).setCellValue(vo.getNumberId());
                        // row.createCell(c++).setCellValue(vo.getSchoolName());
                        row.createCell(c++).setCellValue(vo.getMajorName());
                        row.createCell(c++).setCellValue(vo.getGradeYear()); // 🔴 新增
                        row.createCell(c++).setCellValue(vo.getClassName());
                    } else {
                        Cell mc = row.createCell(c++);
                        mc.setCellValue(vo.getMembersContent());
                        mc.setCellStyle(wrapStyle);
                    }
                    row.createCell(c++).setCellValue(formatTeamType(vo.getTeamType(), true));
                } else {
                    row.createCell(c++).setCellValue(vo.getRealName());
                    row.createCell(c++).setCellValue(vo.getGender());
                    row.createCell(c++).setCellValue(vo.getNumberId());
                    // row.createCell(c++).setCellValue(vo.getSchoolName());
                    row.createCell(c++).setCellValue(vo.getMajorName());
                    row.createCell(c++).setCellValue(vo.getGradeYear()); // 🔴 新增
                    row.createCell(c++).setCellValue(vo.getClassName());
                    row.createCell(c++).setCellValue(formatTeamType(vo.getTeamType(), false));
                }
                row.createCell(c++).setCellValue(vo.getAdvisor());
                row.createCell(c++).setCellValue(vo.getIsRanking() != null && vo.getIsRanking() == 1 ? "是" : "否");
                row.createCell(c++).setCellValue(vo.getSignupTime().format(dtf));
                row.createCell(c++).setCellValue(getStatusText(vo.getStatus()));
                row.createCell(c++).setCellValue(vo.getContestAccount());
                row.createCell(c++).setCellValue(vo.getContestPassword());
                row.createCell(c++).setCellValue(vo.getSeatNumber());
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = URLEncoder.encode(event.getName() + (event.getIsTeam() == 1 ? ("_" + viewType) : "") + "_报名名单.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EventController.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    private void fillMemberSchoolInfo(MySignupInfoVO.SimpleMemberInfo sm, Users user) {
        if (user == null) return;
        sm.setGradeYear(user.getGradeYear());
        sm.setIdPhotoUrl(user.getIdPhotoFileId() == null ? null : "/api/user/id-photo/" + user.getUserId());
        Major major = majorMapper.selectById(user.getMajorId());
        if (major != null) {
            sm.setMajorName(major.getMajorName());
            School school = schoolMapper.selectById(major.getSchoolId());
            if (school != null) {
                sm.setSchoolName(school.getSchoolName());
                University university = universityMapper.selectById(school.getUniversityId());
                if (university != null) sm.setUniversityName(university.getUniversityName());
            }
        }
    }

    private MySignupInfoVO.SimpleMemberInfo toSimpleMemberInfo(Users user, boolean captain) {
        MySignupInfoVO.SimpleMemberInfo sm = new MySignupInfoVO.SimpleMemberInfo();
        if (user == null) return sm;
        fillPublicMemberInfo(sm, user);
        sm.setRealName(user.getRealName());
        sm.setNumberId(user.getNumberId());
        sm.setCaptain(captain);
        fillMemberSchoolInfo(sm, user);
        return sm;
    }

    private MySignupInfoVO.SimpleMemberInfo toSignupMemberInfo(Users user, boolean captain, boolean identityView) {
        if (identityView) return toSimpleMemberInfo(user, captain);
        MySignupInfoVO.SimpleMemberInfo member = new MySignupInfoVO.SimpleMemberInfo();
        fillPublicMemberInfo(member, user);
        String name = user.getRealName();
        if (StringUtils.hasText(name)) member.setRealName(name.substring(0, name.offsetByCodePoints(0, 1)) + "*");
        member.setCaptain(captain);
        return member;
    }

    private void fillPublicMemberInfo(MySignupInfoVO.SimpleMemberInfo member, Users user) {
        member.setUserId(user.getUserId());
        member.setUserName(user.getUserName());
        if (user.getAvatarFileId() != null) member.setAvatarUrl("/api/file/image/" + user.getAvatarFileId());
    }

    private Result<String> validateMembersIdPhoto(List<Users> members) {
        if (members == null || members.isEmpty()) return Result.error("报名成员为空");
        for (Users member : members) {
            if (member == null || member.getIdPhotoFileId() == null) {
                String name = member == null ? "未知成员" : (StringUtils.hasText(member.getRealName()) ? member.getRealName() : String.valueOf(member.getUserId()));
                return Result.error("成员【" + name + "】未上传证件照，无法报名");
            }
        }
        return null;
    }

    private void fillUserSchoolInfo(EventSignupVO vo, Users user) {
        if (user == null) return;
        Major major = majorMapper.selectById(user.getMajorId());
        if (major != null) {
            vo.setMajorName(major.getMajorName());
            School school = schoolMapper.selectById(major.getSchoolId());
            if (school != null) {
                vo.setSchoolName(school.getSchoolName());
                University uni = universityMapper.selectById(school.getUniversityId());
                if (uni != null) vo.setUniversityName(uni.getUniversityName());
            }
        }
    }

    private Users findUserBySchoolAndNumber(String schoolName, String numberId) {
        return StudentNumberValidator.findByUniversityNameAndNumber(
                userMapper, universityMapper, schoolName, numberId);
    }

    private String normalizeTeamName(String teamName) {
        return teamName == null ? "" : teamName.trim();
    }

    private List<Long> collectTeamMemberIds(Team team, List<TeamMember> members) {
        List<Long> ids = new ArrayList<>();
        if (team != null && team.getLeaderId() != null) ids.add(team.getLeaderId());
        if (members != null) {
            for (TeamMember tm : members) {
                if (tm.getMemberId() != null && !ids.contains(tm.getMemberId())) {
                    ids.add(tm.getMemberId());
                }
            }
        }
        return ids;
    }

    private List<Users> loadUsersByIdsInOrder(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        Map<Long, Users> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, u -> u, (a, b) -> a));
        List<Users> users = new ArrayList<>();
        for (Long id : userIds) {
            Users u = userMap.get(id);
            if (u != null) users.add(u);
        }
        return users;
    }

    private List<SignupTeamDetail> getSignupDetailsOrdered(Long signupId) {
        if (signupId == null) return new ArrayList<>();
        return signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>()
                .eq("signup_team_id", signupId)
                .orderByAsc("member_order", "detail_id"));
    }

    private List<Users> loadUsersForDetails(List<SignupTeamDetail> details) {
        if (details == null || details.isEmpty()) return new ArrayList<>();
        List<Long> ids = details.stream().map(SignupTeamDetail::getUserId).filter(Objects::nonNull).collect(Collectors.toList());
        return loadUsersByIdsInOrder(ids);
    }

    private List<Users> loadSignupUsers(Long signupId) {
        return loadUsersForDetails(getSignupDetailsOrdered(signupId));
    }

    private void replaceSignupTeamDetails(Long signupId, List<Long> memberIds) {
        signupTeamDetailMapper.delete(new QueryWrapper<SignupTeamDetail>().eq("signup_team_id", signupId));
        if (memberIds == null) return;
        for (int i = 0; i < memberIds.size(); i++) {
            SignupTeamDetail detail = new SignupTeamDetail();
            detail.setSignupTeamId(signupId);
            detail.setUserId(memberIds.get(i));
            detail.setMemberOrder(i);
            signupTeamDetailMapper.insert(detail);
        }
    }

    private Map<String, String> getSignupTeamEmailTargets(Long signupId) {
        Map<String, String> targets = new HashMap<>();
        for (Users u : loadSignupUsers(signupId)) {
            if (StringUtils.hasText(u.getEmail())) {
                targets.put(u.getEmail(), u.getRealName());
            }
        }
        return targets;
    }

    String resolveImportedSignupType(Event event, String type, List<Users> members) {
        if ("GPLT".equalsIgnoreCase(event.getType()) || "LANQIAO".equalsIgnoreCase(event.getType())) {
            List<String> allowed = "GPLT".equalsIgnoreCase(event.getType()) ? GPLT_TYPES : LANQIAO_TYPES;
            if (!allowed.contains(type)) throw new IllegalArgumentException("参赛组别请填写：" + String.join("、", allowed));
            return type;
        }
        if (!StringUtils.hasText(type)) type = "official";
        if (!Arrays.asList("official", "girl", "star").contains(type)) {
            throw new IllegalArgumentException("参赛类型请填写：正式参赛、女生队伍、打星参赛");
        }
        if ("girl".equals(type) && !isAllGirls(members)) {
            throw new IllegalArgumentException("女生队伍要求所有参赛成员均为女性");
        }
        return "official".equals(type) && isAllGirls(members) ? "girl" : type;
    }

    private boolean isAllGirls(List<Users> users) {
        if (users == null || users.isEmpty()) return false;
        for (Users u : users) {
            if (u == null || u.getGender() == null || u.getGender() != 2) return false;
        }
        return true;
    }

    private String convertGender(Integer g) {
        if (g == null) return "";
        if (g == 1) return "男";
        if (g == 2) return "女";
        return "未知";
    }

    private boolean checkPermission(Long userId) {
        com.cwnupaa.backend.vo.UserProfileVO actor = new com.cwnupaa.backend.vo.UserProfileVO();
        actor.setUserId(userId);
        return fileAccessPolicy.hasPermission(actor, "manage_contest");
    }

    private String getStatusText(String s) {
        if ("approved".equals(s)) return "已报名";
        if ("rejected".equals(s)) return "已拒绝";
        if ("cancelled".equals(s)) return "已取消";
        return "待审核";
    }

    private void checkAndSendTimeChangeNotification(Event oldEvent, Event newEvent) {
        boolean timeChanged = isTimeChanged(oldEvent.getEventStart(), newEvent.getEventStart()) || isTimeChanged(oldEvent.getEventEnd(), newEvent.getEventEnd());
        boolean signupTimeChanged = isTimeChanged(oldEvent.getSignupStart(), newEvent.getSignupStart()) || isTimeChanged(oldEvent.getSignupEnd(), newEvent.getSignupEnd());

        if (timeChanged || signupTimeChanged) {
            Map<String, String> targets = getEventParticipants(newEvent);
            if (targets.isEmpty()) return;

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder();
            if (timeChanged) {
                sb.append("比赛时间调整为：").append(newEvent.getEventStart() != null ? newEvent.getEventStart().format(dtf) : "待定").append(" 至 ").append(newEvent.getEventEnd() != null ? newEvent.getEventEnd().format(dtf) : "待定").append("。");
            }
            if (signupTimeChanged) {
                if (sb.length() > 0) sb.append("<br/>");
                sb.append("报名时间调整为：").append(newEvent.getSignupStart() != null ? newEvent.getSignupStart().format(dtf) : "待定").append(newEvent.getSignupEnd() != null ? " 至 " + newEvent.getSignupEnd().format(dtf) : " 起，不设截止时间").append("。");
            }

            emailService.sendEventTimeChangeEmail(targets, newEvent.getName(), sb.toString());
        }
    }

    private boolean isTimeChanged(LocalDateTime t1, LocalDateTime t2) {
        if (t1 == null && t2 == null) return false;
        if (t1 == null || t2 == null) return true;
        return !t1.isEqual(t2);
    }

    private Map<String, String> getEventParticipants(Event event) {
        Map<String, String> targets = new HashMap<>();
        if (event.getIsTeam() == 0) {
            List<SignupPersonal> list = signupPersonalMapper.selectList(new QueryWrapper<SignupPersonal>().eq("event_id", event.getEventId()).eq("status", "approved"));
            for (SignupPersonal sp : list) {
                Users u = userMapper.selectById(sp.getUserId());
                if (u != null && StringUtils.hasText(u.getEmail())) {
                    targets.put(u.getEmail(), u.getRealName());
                }
            }
        } else {
            List<SignupTeam> list = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", event.getEventId()).eq("status", "approved"));
            for (SignupTeam st : list) {
                targets.putAll(getSignupTeamEmailTargets(st.getSignupId()));
            }
        }
        return targets;
    }

    private String parseTeamType(String input) {
        if (StringUtils.isEmpty(input)) return null;
        if ("女生队伍".equals(input) || "女队".equals(input)) return "girl";
        if ("正式队伍".equals(input) || "正式参赛".equals(input)) return "official";
        if ("打星队伍".equals(input) || "打星参赛".equals(input)) return "star";
        return input;
    }

    private String formatTeamType(String code, boolean isTeam) {
        if (StringUtils.isEmpty(code)) return "";
        if ("girl".equals(code)) return "女生队伍";
        if ("official".equals(code)) return isTeam ? "正式队伍" : "正式参赛";
        if ("star".equals(code)) return isTeam ? "打星队伍" : "打星参赛";
        return code;
    }

    private String formatTeamType(String code) {
        return formatTeamType(code, true);
    }
}
