package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.CertificateVerifyDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.service.CertificateIdentityService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.vo.CertificateVerifyVO;
import com.cwnupaa.backend.vo.HonorWallVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/certificate")
public class CertificateController {

    @Autowired
    private CertificateVerifyMapper certificateVerifyMapper;
    @Autowired
    private CertificateLevelMapper certificateLevelMapper;
    @Autowired
    private ContestCategoryMapper contestCategoryMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private EmailService emailService;
    @Autowired
    private CertificateGrantMapper certificateGrantMapper;
    @Autowired
    private CertificateTypeMapper certificateTypeMapper;
    @Autowired
    private CertificateMemberMapper certificateMemberMapper;
    @Autowired
    private CertificateIssueMapper certificateIssueMapper;
    @Autowired
    private FileAccessPolicy fileAccessPolicy;
    @Autowired private CertificateIdentityService certificateIdentityService;
    @Autowired private TeamMapper teamMapper;
    @Autowired private TeamMemberMapper teamMemberMapper;

    @PostMapping("/submit")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> submit(@RequestBody CertificateVerifyDTO dto,
                                @RequestAttribute(value = "userId", required = false) Long sessionUserId) {
        if (sessionUserId == null) return Result.error(401, "未登录或会话已失效");
        Users applicant = certificateIdentityService.verifiedUser(sessionUserId);
        if (applicant == null) return Result.error("账号须已实名审核通过");
        if (dto.getCertificateVerifyId() == null && dto.getFileId() == null) return Result.error("必须上传证书图片");
        if (dto.getLevelId() == null || dto.getCategoryId() == null || dto.getTermId() == null) return Result.error("请选择获奖等级、赛事分类和学期");
        if (!StringUtils.hasText(dto.getEventName()) || dto.getTime() == null) return Result.error("请填写认证名称和获奖时间");
        if (dto.getMembers() != null && dto.getMembers().size() > 20) return Result.error("团队成员数量不能超过20人");
        List<CertificateMember> members = new ArrayList<>();
        Set<Long> includedUsers = new java.util.HashSet<>();
        includedUsers.add(sessionUserId);
        CertificateMember captain = new CertificateMember();
        captain.setUserId(sessionUserId); captain.setName(applicant.getRealName()); captain.setStudentId(applicant.getNumberId());
        captain.setSchoolName(certificateIdentityService.schoolName(applicant));
        captain.setIsCaptain(1); captain.setSortOrder(0); members.add(captain);
        if (Integer.valueOf(1).equals(dto.getIsTeam()) && dto.getMembers() != null) {
            for (CertificateVerifyDTO.MemberDTO input : dto.getMembers()) {
                if (input == null) return Result.error("成员资料不能为空");
                CertificateMember member = new CertificateMember();
                if (input.getUserId() != null) {
                    if (!includedUsers.add(input.getUserId())) continue;
                    if (!canUseInternalMember(sessionUserId, input.getUserId())) return Result.error("站内队员须为您所带队伍中已接受邀请的成员；其他获奖人请手工填写并由管理员核实归属");
                    Users verified = certificateIdentityService.verifiedUser(input.getUserId());
                    if (verified == null) return Result.error("站内成员须已实名审核通过");
                    member.setUserId(verified.getUserId()); member.setName(verified.getRealName()); member.setStudentId(verified.getNumberId());
                    member.setSchoolName(certificateIdentityService.schoolName(verified));
                } else {
                    if (!StringUtils.hasText(input.getName()) || !StringUtils.hasText(input.getStudentId())) return Result.error("站外成员姓名和学号不能为空");
                    member.setName(input.getName().trim()); member.setStudentId(input.getStudentId().trim());
                    // External recipients remain unbound; a student number alone is never proof of identity.
                }
                member.setIsCaptain(0); member.setSortOrder(members.size()); members.add(member);
            }
        }
        boolean update = dto.getCertificateVerifyId() != null;
        CertificateVerify certificate = update ? certificateVerifyMapper.selectById(dto.getCertificateVerifyId()) : new CertificateVerify();
        if (certificate == null) return Result.error("记录不存在");
        if (update && (!sessionUserId.equals(certificate.getUserId()) || !"rejected".equals(certificate.getStatus()))) return Result.error("仅能修改本人被驳回的记录");
        UserProfileVO actor = new UserProfileVO();
        actor.setUserId(sessionUserId); actor.setRole(applicant.getRole());
        Long oldFileId = update ? certificate.getFileId() : null;
        Long fileId = dto.getFileId() != null ? dto.getFileId() : oldFileId;
        String fileError = fileAccessPolicy.validateCertificateFile(fileId, actor, false);
        if (fileError != null) return Result.error(fileError);
        BeanUtils.copyProperties(dto, certificate);
        certificate.setUserId(sessionUserId); certificate.setFileId(fileId); certificate.setTerm(dto.getTermId());
        certificate.setTime(dto.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        certificate.setStatus("pending"); certificate.setComments(""); certificate.setAuditUserId(null); certificate.setReplayTime(null);
        if (update) {
            certificateVerifyMapper.updateById(certificate);
            certificateMemberMapper.delete(new QueryWrapper<CertificateMember>().eq("certificate_verify_id", certificate.getCertificateVerifyId()));
        } else {
            certificate.setCreateTime(LocalDateTime.now()); certificateVerifyMapper.insert(certificate);
        }
        for (CertificateMember member : members) {
            member.setCertificateVerifyId(certificate.getCertificateVerifyId()); certificateMemberMapper.insert(member);
        }
        if (oldFileId != null && !oldFileId.equals(fileId)) fileAccessPolicy.deleteIfUnreferenced(oldFileId, actor);
        return Result.success(update ? "修改成功，请等待审核" : "提交成功，请等待审核");
    }

    private boolean canUseInternalMember(Long applicantId, Long memberId) {
        for (Team team : teamMapper.selectList(new QueryWrapper<Team>().eq("leader_id", applicantId))) {
            if (teamMemberMapper.selectCount(new QueryWrapper<TeamMember>().eq("team_id", team.getTeamId())
                    .eq("member_id", applicantId).eq("status", "1")) > 0
                    && teamMemberMapper.selectCount(new QueryWrapper<TeamMember>().eq("team_id", team.getTeamId())
                    .eq("member_id", memberId).eq("status", "1")) > 0) return true;
        }
        return false;
    }

    @GetMapping("/honor-wall")
    public Result<List<HonorWallVO>> getHonorWall(@RequestParam Long userId) {
        List<HonorWallVO> honorList = new ArrayList<>();

        List<CertificateLevel> levels = certificateLevelMapper.selectList(null);
        Map<Long, CertificateLevel> levelMap = levels.stream().collect(Collectors.toMap(CertificateLevel::getLevelId, l -> l));

        // Use custom mapper method to include team certificates
        List<CertificateVerify> verifies = certificateVerifyMapper.selectMyHonorWall(userId);

        for (CertificateVerify v : verifies) {
            HonorWallVO vo = new HonorWallVO();
            vo.setId("V-" + v.getCertificateVerifyId());
            vo.setName(v.getEventName());
            vo.setAwardDate(v.getTime() != null ? v.getTime() : v.getCreateTime().toLocalDate());

            CertificateLevel level = levelMap.get(v.getLevelId());
            fillLevelInfo(vo, level);
            honorList.add(vo);
        }

        Users currentUser = certificateIdentityService.verifiedUser(userId);
        List<CertificateGrant> grants = certificateGrantMapper.selectList(new QueryWrapper<CertificateGrant>().eq("user_id", userId))
                .stream().filter(g -> certificateIdentityService.owns(currentUser, g)).toList();

        if (!grants.isEmpty()) {
            Set<Long> typeIds = grants.stream().map(CertificateGrant::getCertificateTypeId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, CertificateType> typeMap = typeIds.isEmpty() ? Map.of()
                    : certificateTypeMapper.selectBatchIds(typeIds).stream().collect(Collectors.toMap(CertificateType::getCertificateTypeId, t -> t));
            Set<Long> issueIds = grants.stream().map(CertificateGrant::getIssueId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, CertificateIssue> issueMap = issueIds.isEmpty() ? Map.of()
                    : certificateIssueMapper.selectBatchIds(issueIds).stream().collect(Collectors.toMap(CertificateIssue::getIssueId, issue -> issue));

            for (CertificateGrant g : grants) {
                CertificateType type = typeMap.get(g.getCertificateTypeId());
                if (type != null) {
                    CertificateIssue issue = g.getIssueId() == null ? null : issueMap.get(g.getIssueId());
                    if (issue != null && !"active".equals(issue.getStatus())) continue;
                    HonorWallVO vo = new HonorWallVO();
                    vo.setId("G-" + g.getCertificateDownloadId());
                    vo.setName(type.getTypeName());
                    vo.setAwardDate(issue != null && issue.getIssueDate() != null ? issue.getIssueDate() : type.getTime());

                    CertificateLevel level = resolveGrantLevel(type, issue, levels, levelMap);
                    fillLevelInfo(vo, level);
                    applyIssueHonorSnapshot(vo, issue, type);
                    honorList.add(vo);
                }
            }
        }

        honorList.sort((a, b) -> {
            int weightCompare = b.getSortWeight().compareTo(a.getSortWeight());
            if (weightCompare != 0) return weightCompare;

            if (b.getAwardDate() == null) return -1;
            if (a.getAwardDate() == null) return 1;
            return b.getAwardDate().compareTo(a.getAwardDate());
        });

        return Result.success(honorList);
    }

    private void fillLevelInfo(HonorWallVO vo, CertificateLevel level) {
        if (level != null) {
            vo.setLevelName(level.getName());
            vo.setLevelType(level.getLevelType());
            vo.setRankType(level.getRankType());
            vo.setSortWeight(level.getSortWeight() != null ? level.getSortWeight() : 0);
        } else {
            vo.setLevelName("未知等级");
            vo.setLevelType("其他");
            vo.setRankType("");
            vo.setSortWeight(0);
        }
    }

    private CertificateLevel resolveGrantLevel(CertificateType type, CertificateIssue issue, List<CertificateLevel> levels, Map<Long, CertificateLevel> levelMap) {
        if (issue != null) {
            String levelScope = StringUtils.hasText(type.getLevelScope()) ? type.getLevelScope() : issue.getLevelScope();
            if (StringUtils.hasText(levelScope) && StringUtils.hasText(issue.getAwardName())) {
                for (CertificateLevel level : levels) {
                    if (levelScope.equals(level.getLevelType()) && awardMatches(level, issue.getAwardName())) return level;
                }
            }
        }
        CertificateLevel templateLevel = type.getTypeId() == null ? null : levelMap.get(type.getTypeId());
        if (templateLevel != null) return templateLevel;
        if (StringUtils.hasText(type.getLevelScope())) {
            return levels.stream()
                    .filter(level -> type.getLevelScope().equals(level.getLevelType()))
                    .min(Comparator.comparingInt(level -> level.getSortWeight() == null ? 0 : level.getSortWeight()))
                    .orElse(null);
        }
        return null;
    }

    private void applyIssueHonorSnapshot(HonorWallVO vo, CertificateIssue issue, CertificateType type) {
        if (issue == null) return;
        String levelScope = StringUtils.hasText(type.getLevelScope()) ? type.getLevelScope() : issue.getLevelScope();
        if (StringUtils.hasText(levelScope)) {
            vo.setLevelType(levelScope);
            if (!StringUtils.hasText(vo.getLevelName()) || "未知等级".equals(vo.getLevelName())) {
                vo.setLevelName(levelScope);
            }
            if (vo.getSortWeight() == null || vo.getSortWeight() == 0) {
                vo.setSortWeight(calculateWeight(levelScope));
            }
        }
        if (StringUtils.hasText(issue.getAwardName())) {
            vo.setRankType(issue.getAwardName());
        }
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
                || (award.contains("金") && (rank.contains("金") || rank.contains("一等")))
                || (award.contains("银") && (rank.contains("银") || rank.contains("二等")))
                || (award.contains("铜") && (rank.contains("铜") || rank.contains("三等")))
                || (award.contains("优胜") && rank.contains("优秀"));
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

    private int calculateWeight(String levelType) {
        if (!StringUtils.hasText(levelType)) return 0;
        if (levelType.contains("国") || levelType.contains("National")) return 40;
        if (levelType.contains("省") || levelType.contains("Provincial")) return 30;
        if (levelType.contains("校") || levelType.contains("School") || levelType.contains("市")) return 20;
        if (levelType.contains("院") || levelType.contains("College")) return 10;
        return 0;
    }

    @GetMapping("/admin/list")
    public Result<Page<CertificateVerifyVO>> getAdminList(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(defaultValue = "pending") String status, @RequestParam(required = false) String keyword) {

        Page<CertificateVerify> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        QueryWrapper<CertificateVerify> wrapper = new QueryWrapper<>();

        if (!"all".equalsIgnoreCase(status)) {
            wrapper.eq("status", status);
        }

        if (StringUtils.hasText(keyword)) {
            QueryWrapper<Users> userWrapper = new QueryWrapper<>();
            userWrapper.select("user_id").and(w -> w.like("real_name", keyword).or().like("user_name", keyword));
            List<Object> userIds = userMapper.selectObjs(userWrapper);

            if (userIds.isEmpty()) {
                return Result.success(new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size)));
            }
            wrapper.in("user_id", userIds);
        }

        wrapper.orderByDesc("create_time");
        certificateVerifyMapper.selectPage(page, wrapper);

        List<CertificateVerifyVO> voList = page.getRecords().stream().map(this::convertToFullVo).collect(Collectors.toList());

        Page<CertificateVerifyVO> resultPage = new Page<>();
        BeanUtils.copyProperties(page, resultPage);
        resultPage.setRecords(voList);
        return Result.success(resultPage);
    }

    @GetMapping("/categories")
    public Result<List<ContestCategory>> getCategories() {
        return Result.success(contestCategoryMapper.selectList(new QueryWrapper<ContestCategory>().eq("status", 1)));
    }

    @GetMapping("/levels")
    public Result<List<CertificateLevel>> getLevels() {
        return Result.success(certificateLevelMapper.selectList(new QueryWrapper<CertificateLevel>().orderByDesc("base_rating")));
    }

    @GetMapping("/terms")
    public Result<List<Term>> getTerms() {
        return Result.success(termMapper.selectList(new QueryWrapper<Term>().orderByDesc("start_time")));
    }

    @GetMapping("/my-list")
    public Result<Page<CertificateVerifyVO>> getMyList(@RequestAttribute("userId") Long userId, @RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size) {

        Page<CertificateVerify> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        IPage<CertificateVerify> result = certificateVerifyMapper.selectMyCertificates(page, userId);

        List<CertificateVerifyVO> voList = result.getRecords().stream().map(this::convertToVo).collect(Collectors.toList());

        Page<CertificateVerifyVO> resultPage = new Page<>();
        BeanUtils.copyProperties(result, resultPage);
        resultPage.setRecords(voList);
        return Result.success(resultPage);
    }

    @PostMapping("/admin/audit")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> audit(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long verifyId = Long.valueOf(params.get("id").toString());
        boolean pass = Boolean.parseBoolean(params.get("pass").toString());
        String comment = params.containsKey("comment") ? params.get("comment").toString() : "";
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");

        if (!StringUtils.hasText(comment)) return Result.error("审核意见必须填写");

        CertificateVerify verify = certificateVerifyMapper.selectById(verifyId);
        if (verify == null) return Result.error("记录不存在");

        verify.setStatus(pass ? "approved" : "rejected");
        verify.setComments(comment);
        verify.setAuditUserId(operatorId);
        verify.setReplayTime(LocalDateTime.now());

        certificateVerifyMapper.updateById(verify);

        Users user = userMapper.selectById(verify.getUserId());
        if (user != null && StringUtils.hasText(user.getEmail())) {
            emailService.sendCertificateAuditResultEmail(user.getEmail(), user.getRealName(), verify.getEventName(), pass, comment);
        }
        return Result.success("审核完成");
    }

    private CertificateVerifyVO convertToVo(CertificateVerify entity) {
        CertificateVerifyVO vo = new CertificateVerifyVO();
        BeanUtils.copyProperties(entity, vo);

        if (entity.getLevelId() != null) {
            CertificateLevel level = certificateLevelMapper.selectById(entity.getLevelId());
            if (level != null) {
                vo.setLevelName(level.getName());
                vo.setLevelType(level.getLevelType());
            }
        }
        if (entity.getCategoryId() != null) {
            ContestCategory category = contestCategoryMapper.selectById(entity.getCategoryId());
            if (category != null) vo.setCategoryName(category.getName());
        }
        if (entity.getTerm() != null) {
            Term term = termMapper.selectById(entity.getTerm());
            if (term != null) vo.setTermName(term.getTermName());
        }
        if (entity.getFileId() != null) {
            FileSystem file = fileSystemMapper.selectById(entity.getFileId());
            if (file != null) vo.setImageUrl("/api/file/image/" + file.getFileId());
        }

        if (entity.getAuditUserId() != null && entity.getAuditUserId() != 0) {
            Users auditor = userMapper.selectById(entity.getAuditUserId());
            if (auditor != null) {
                vo.setAuditUserRealName(auditor.getRealName());
            }
        }
        
        // Fill Team Members
        List<CertificateMember> members = certificateMemberMapper.selectList(new QueryWrapper<CertificateMember>()
                .eq("certificate_verify_id", entity.getCertificateVerifyId())
                .orderByAsc("sort_order"));
        if (!members.isEmpty()) {
            List<CertificateVerifyVO.MemberVO> memberVOs = members.stream().map(m -> {
                CertificateVerifyVO.MemberVO mvo = new CertificateVerifyVO.MemberVO();
                mvo.setName(m.getName());
                mvo.setStudentId(m.getStudentId());
                mvo.setIsCaptain(m.getIsCaptain());
                mvo.setSortOrder(m.getSortOrder());
                return mvo;
            }).collect(Collectors.toList());
            vo.setMembers(memberVOs);
        }

        return vo;
    }

    private boolean isOwnedFile(Long fileId, Long userId) {
        FileSystem file = fileSystemMapper.selectById(fileId);
        return file != null && file.getUploaderId() != null && file.getUploaderId().equals(userId);
    }

    private CertificateVerifyVO convertToFullVo(CertificateVerify entity) {
        CertificateVerifyVO vo = convertToVo(entity);

        if (entity.getUserId() != null) {
            Users user = userMapper.selectById(entity.getUserId());
            if (user != null) {
                vo.setUserRealName(user.getRealName());
                vo.setUserNumberId(user.getNumberId());
                vo.setUserClassName(user.getClassName());
                if (user.getMajorId() != null) {
                    Major major = majorMapper.selectById(user.getMajorId());
                    if (major != null) vo.setUserMajorName(major.getMajorName());
                }
            }
        }
        return vo;
    }

    @PostMapping("/admin/level/update-weights")
    public Result<String> updateLevelWeights(@RequestBody List<CertificateLevel> levels) {
        for (CertificateLevel level : levels) {
            if (level.getLevelId() != null && level.getSortWeight() != null) {
                CertificateLevel entity = new CertificateLevel();
                entity.setLevelId(level.getLevelId());
                entity.setSortWeight(level.getSortWeight());
                certificateLevelMapper.updateById(entity);
            }
        }
        return Result.success("更新成功");
    }
}
