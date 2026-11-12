package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.service.SecurityAuditService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.service.CertificateGrantService;
import com.cwnupaa.backend.service.AdminRolePermissionService;
import com.cwnupaa.backend.vo.AuditLogVO;
import com.cwnupaa.backend.vo.AuthAuditListVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.cwnupaa.backend.vo.UserOjAccountVO;
import com.cwnupaa.backend.dto.OjAccountUpdateDTO;
import com.cwnupaa.backend.dto.UserImportDTO;
import com.cwnupaa.backend.utils.Md5Util;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    private static final Set<String> ROLE_WHITELIST = Set.of(
            "student", "school_admin", "department", "stu_admin", "super_admin", "supper_admin");
    private static final Set<String> ACCOUNT_STATUS_WHITELIST = Set.of(
            "pending_activation", "register", "pending", "normal", "restricted", "banned");

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private EmailService emailService;
    @Autowired
    private AuthStateChangeService authStateChangeService;
    @Autowired private AccountSecurityService accountSecurityService;
    @Autowired private SecurityAuditService securityAuditService;
    @Autowired private FileAccessPolicy fileAccessPolicy;
    @Autowired private CertificateGrantService certificateGrantService;
    @Autowired private AdminRolePermissionService adminRolePermissionService;
    @Autowired
    private AuditRecordsMapper auditRecordsMapper;

    @Autowired
    private OjUserAccountMapper ojUserAccountMapper;

    @GetMapping("/list")
    public Result<Page<UserProfileVO>> getUserList(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long universityId, @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long majorId, @RequestParam(required = false) Integer gender, @RequestParam(required = false) String gradeYear, @RequestParam(required = false) Integer isEnrolledStu, @RequestParam(required = false) String roleFilter, @RequestParam(required = false) String accountStatus) {

        Page<Users> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        QueryWrapper<Users> wrapper = new QueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("number_id", keyword).or().like("user_account", keyword).or().like("real_name", keyword).or().like("user_name", keyword).or().like("email", keyword).or().like("phone", keyword));
        }

        if (gender != null) wrapper.eq("gender", gender);
        if (StringUtils.hasText(gradeYear)) wrapper.eq("grade_year", gradeYear);
        if (isEnrolledStu != null) wrapper.eq("is_enrolled_stu", isEnrolledStu);
        
        if (StringUtils.hasText(accountStatus)) wrapper.eq("account_status", accountStatus);

        if (StringUtils.hasText(roleFilter)) {
            if ("admins".equals(roleFilter)) {
                wrapper.in("role", "supper_admin", "super_admin", "stu_admin", "department", "school_admin");
            } else {
                wrapper.eq("role", roleFilter);
            }
        }

        if (majorId != null) {
            wrapper.eq("major_id", majorId);
        } else if (schoolId != null) {
            List<Major> majors = majorMapper.selectList(new QueryWrapper<Major>().eq("school_id", schoolId));
            List<Long> majorIds = majors.stream().map(Major::getMajorId).collect(Collectors.toList());
            if (majorIds.isEmpty()) {
                wrapper.eq("major_id", -1);
            } else {
                wrapper.in("major_id", majorIds);
            }
        } else if (universityId != null) {
            List<School> schools = schoolMapper.selectList(new QueryWrapper<School>().eq("university_id", universityId));
            List<Long> schoolIds = schools.stream().map(School::getSchoolId).collect(Collectors.toList());

            if (schoolIds.isEmpty()) {
                wrapper.eq("major_id", -1);
            } else {
                List<Major> majors = majorMapper.selectList(new QueryWrapper<Major>().in("school_id", schoolIds));
                List<Long> majorIds = majors.stream().map(Major::getMajorId).collect(Collectors.toList());
                if (majorIds.isEmpty()) {
                    wrapper.eq("major_id", -1);
                } else {
                    wrapper.in("major_id", majorIds);
                }
            }
        }

        wrapper.orderByDesc("register_time");

        userMapper.selectPage(page, wrapper);

        List<UserProfileVO> voList = page.getRecords().stream().map(this::convertToVo).collect(Collectors.toList());

        Page<UserProfileVO> resultPage = new Page<>();
        BeanUtils.copyProperties(page, resultPage);
        resultPage.setRecords(voList);

        return Result.success(resultPage);
    }

    public record BanRequest(Long userId, LocalDateTime banUntil, String reason) {}

    @Autowired
    private com.cwnupaa.backend.service.UserBanService userBanService;

    @GetMapping("/ban/search")
    public Result<List<UserProfileVO>> searchBanUsers(@RequestParam String numberId) {
        if (!StringUtils.hasText(numberId)) return Result.error("请输入学号");
        return Result.success(userMapper.selectList(new QueryWrapper<Users>()
                .eq("number_id", numberId.trim()).orderByAsc("user_id").last("LIMIT 50"))
                .stream().map(this::convertToVo).collect(Collectors.toList()));
    }

    @PostMapping("/ban")
    public Result<Map<String, Object>> banUser(@RequestBody BanRequest dto, HttpServletRequest request) {
        UserProfileVO operator = SecurityUtils.currentUser(request);
        if (operator == null) return Result.error("未登录或会话已失效");
        Users user;
        try {
            user = userBanService.ban(dto.userId(), dto.banUntil(), dto.reason(), operator);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        boolean emailSent = true;
        try {
            emailService.sendAccountBanEmail(user.getEmail(), user.getRealName(), dto.banUntil(), dto.reason().trim());
        } catch (Exception e) {
            emailSent = false;
            org.slf4j.LoggerFactory.getLogger(AdminUserController.class).warn("封禁通知邮件发送失败，用户 {}", user.getUserId(), e);
        }
        return Result.success(Map.of("emailSent", emailSent));
    }

    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> importUsers(@RequestBody List<UserImportDTO> list, HttpServletRequest request) {
        UserProfileVO operator = SecurityUtils.currentUser(request);
        if (!SecurityUtils.hasPermission(operator, "manage_user")) return Result.error(403, "无用户管理权限");
        if (list == null || list.isEmpty()) return Result.error("导入数据为空");

        int successCount = 0;
        int skipCount = 0;
        List<Long> createdUserIds = new ArrayList<>();

        // 记录本批次内已导入的（大学, 学号），防止同批次内重复
        Set<String> seenNumberKeys = new HashSet<>();
        // 记录系统自动生成的初始密码（导入表单未填密码时），随结果返回便于管理员下发
        Map<String, String> generatedPasswords = new java.util.LinkedHashMap<>();

        for (UserImportDTO dto : list) {
            try {
                if (!StringUtils.hasText(dto.getUniversityName())
                        || !StringUtils.hasText(dto.getSchoolName())
                        || !StringUtils.hasText(dto.getMajorName())
                        || !StringUtils.hasText(dto.getRealName())
                        || !StringUtils.hasText(dto.getNumberId())) {
                    skipCount++;
                    continue;
                }

                String numberId = dto.getNumberId().trim();
                String universityName = dto.getUniversityName().trim();
                String schoolName = dto.getSchoolName().trim();
                String majorName = dto.getMajorName().trim();

                Users user = new Users();
                user.setNumberId(numberId);
                user.setUserAccount(SecurityUtils.pendingActivationAccount());
                user.setRealName(dto.getRealName().trim());
                user.setUserName(dto.getRealName().trim()); // Initial nickname
                user.setAccountStatus("pending_activation");
                user.setRegisterTime(LocalDateTime.now());
                
                if (List.of("老师", "教师", "teacher").contains(dto.getRole() == null ? "" : dto.getRole()) || List.of("教师", "teacher").contains(dto.getEduBg() == null ? "" : dto.getEduBg())) { skipCount++; continue; }
                String role = "student";
                if ("部门".equals(dto.getRole())) {
                    if (!SecurityUtils.hasPermission(operator, "manage_user_role") || !SecurityUtils.isRoot(operator) && SecurityUtils.roleRank(operator.getRole()) <= SecurityUtils.roleRank("department")) { skipCount++; continue; }
                    role = "department";
                }
                user.setRole(role);

                // Map EduBg
                String eduBg = "undergraduate";
                if ("研究生".equals(dto.getEduBg())) eduBg = "postgraduate";
                else if ("部门".equals(dto.getEduBg())) eduBg = "department";
                user.setEduBg(eduBg);
                
                // 导入格式不再包含年级/班级，年级缺省取当前年份（grade_year 非空列）
                String gradeYear = StringUtils.hasText(dto.getGradeYear()) ? dto.getGradeYear() : String.valueOf(LocalDateTime.now().getYear());
                user.setGradeYear(gradeYear);
                user.setClassName(dto.getClassName());
                
                // Handle School/Major/University
                if (StringUtils.hasText(universityName)) {
                    University uni = universityMapper.selectOne(new QueryWrapper<University>().eq("university_name", universityName));
                    if (uni == null) {
                        uni = new University();
                        uni.setUniversityName(universityName);
                        universityMapper.insert(uni);
                    }
                    
                    if (StringUtils.hasText(schoolName)) {
                        School school = schoolMapper.selectOne(new QueryWrapper<School>().eq("university_id", uni.getUniversityId()).eq("school_name", schoolName));
                        if (school == null) {
                            school = new School();
                            school.setUniversityId(uni.getUniversityId());
                            school.setSchoolName(schoolName);
                            schoolMapper.insert(school);
                        }
                        
                        if (StringUtils.hasText(majorName)) {
                            Major major = majorMapper.selectOne(new QueryWrapper<Major>().eq("school_id", school.getSchoolId()).eq("major_name", majorName));
                            if (major == null) {
                                major = new Major();
                                major.setSchoolId(school.getSchoolId());
                                major.setMajorName(majorName);
                                major.setSchoolingYears(4);
                                majorMapper.insert(major);
                            }
                            user.setMajorId(major.getMajorId());
                        }
                    }
                }

                // 同一学校（大学）内学号不能重复，不同学校（大学）之间的学号可以重复
                if (user.getMajorId() != null) {
                    Long universityId = StudentNumberValidator.resolveUniversityId(majorMapper, schoolMapper, user.getMajorId());
                    if (universityId != null) {
                        String numKey = universityId + ":" + numberId;
                        if (seenNumberKeys.contains(numKey)
                                || StudentNumberValidator.existsInSameUniversity(userMapper, majorMapper, schoolMapper,
                                        numberId, user.getMajorId(), null)) {
                            skipCount++;
                            continue;
                        }
                        seenNumberKeys.add(numKey);
                    }
                }

                // 初始密码：优先使用导入表单设置的密码；留空则系统随机生成
                String rawPassword = StringUtils.hasText(dto.getPassword())
                        ? dto.getPassword().trim()
                        : SecurityUtils.randomPassword();
                user.setPassword(Md5Util.encrypt(rawPassword));
                if (!StringUtils.hasText(dto.getPassword())) {
                    generatedPasswords.put(universityName + "+" + numberId, rawPassword);
                }
                
                // Default Values
                user.setGender(null);
                user.setIsEnrolledStu(1);
                user.setEmail(null);
                user.setPhone(null);

                userMapper.insert(user);
                createdUserIds.add(user.getUserId());
                successCount++;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AdminUserController.class).warn("操作失败，请检查请求参数和服务配置");
                skipCount++;
            }
        }

        for (Long createdId : createdUserIds) securityAuditService.record(operator.getUserId(), createdId, "admin-user-import", List.of("profile", "role", "accountStatus"));
        StringBuilder msg = new StringBuilder("导入完成，成功 " + successCount + " 条，跳过 " + skipCount + " 条");
        if (!generatedPasswords.isEmpty()) {
            msg.append("；以下学号的初始密码为系统自动生成，请及时下发：");
            generatedPasswords.forEach((num, pwd) -> msg.append(num).append("=").append(pwd).append("，"));
            msg.setLength(msg.length() - 1);
        }
        return Result.success(msg.toString());
    }

    @PostMapping("/import/preview")
    public Result<Map<String, Object>> previewImportUsers(@RequestBody List<UserImportDTO> list) {
        if (list == null || list.isEmpty()) return Result.error("导入数据为空");
        List<Map<String, String>> successList = new ArrayList<>();
        List<Map<String, String>> failedList = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (int i = 0; i < list.size(); i++) {
            UserImportDTO dto = list.get(i);
            String universityName = dto == null || dto.getUniversityName() == null ? "" : dto.getUniversityName().trim();
            String schoolName = dto == null || dto.getSchoolName() == null ? "" : dto.getSchoolName().trim();
            String majorName = dto == null || dto.getMajorName() == null ? "" : dto.getMajorName().trim();
            String realName = dto == null || dto.getRealName() == null ? "" : dto.getRealName().trim();
            String numberId = dto == null || dto.getNumberId() == null ? "" : dto.getNumberId().trim();
            String content = universityName + " / " + schoolName + " / " + majorName + " / " + realName + " / " + numberId;
            if (!StringUtils.hasText(universityName) || !StringUtils.hasText(schoolName)
                    || !StringUtils.hasText(majorName) || !StringUtils.hasText(realName) || !StringUtils.hasText(numberId)) {
                failedList.add(Map.of("content", content, "reason", "第 " + (i + 2) + " 行必填字段不完整"));
                continue;
            }
            String key = universityName + "\u0001" + numberId;
            if (!seenKeys.add(key)) {
                failedList.add(Map.of("content", content, "reason", "同一学校的学号在文件中重复"));
                continue;
            }
            Users existing = StudentNumberValidator.findByUniversityNameAndNumber(userMapper, universityMapper, universityName, numberId);
            if (existing != null) {
                failedList.add(Map.of("content", content, "reason", "该学校下已存在此学号"));
                continue;
            }
            successList.add(Map.of("content", content, "reason", "可导入"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", successList.size());
        result.put("failure", failedList.size());
        result.put("successList", successList);
        result.put("failedList", failedList);
        return Result.success(result);
    }

    @PostMapping("/update")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateUserInfo(@RequestBody Users user, HttpServletRequest request) {
        if (user.getUserId() == null) {
            return Result.error("用户ID不能为空");
        }

        Users existUser = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", user.getUserId()).last("FOR UPDATE"));
        if (existUser == null) {
            return Result.error("用户不存在");
        }

        UserProfileVO operator = SecurityUtils.currentUser(request);
        if (operator == null) return Result.error("未登录或会话已失效");

        String previousRole = existUser.getRole();
        String previousAccountStatus = existUser.getAccountStatus();

        if (!SecurityUtils.canManageTarget(operator, existUser)) return Result.error(403, "无权修改自身、同级、更高级或内置管理员账号");
        if ("teacher".equals(user.getEduBg())) return Result.error("身份类型不合法");
        boolean emailChanged = user.getEmail() != null && !user.getEmail().equalsIgnoreCase(existUser.getEmail());
        boolean roleChanged = user.getRole() != null && !user.getRole().equals(existUser.getRole());
        boolean statusChanged = user.getAccountStatus() != null && !user.getAccountStatus().equals(existUser.getAccountStatus());
        if (emailChanged && (!SecurityUtils.hasPermission(operator, "manage_user_email") || operator.getUserId().equals(existUser.getUserId()))) return Result.error(403, "无邮箱管理权限；修改本人邮箱请使用账号安全中心");
        if (roleChanged && !SecurityUtils.hasPermission(operator, "manage_user_role")) return Result.error(403, "无角色管理权限");
        if (statusChanged && !SecurityUtils.hasPermission(operator, "manage_user_status")) return Result.error(403, "无账号状态管理权限");
        if (user.getRole() != null && !ROLE_WHITELIST.contains(user.getRole())) return Result.error("非法的角色值");
        if (roleChanged && !SecurityUtils.isRoot(operator) && SecurityUtils.roleRank(user.getRole()) >= SecurityUtils.roleRank(operator.getRole())) return Result.error(403, "不能授予同级或更高级角色");
        if ("admin".equals(existUser.getUserAccount()) && roleChanged && !List.of("supper_admin", "super_admin").contains(user.getRole())) return Result.error("内置管理员账号不能降级");
        if (user.getAccountStatus() != null && !ACCOUNT_STATUS_WHITELIST.contains(user.getAccountStatus())) return Result.error("非法的账号状态");
        String previousEmail = existUser.getEmail();
        if (emailChanged) {
            String normalizedEmail = user.getEmail().trim().toLowerCase();
            if (normalizedEmail.length() > 100 || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return Result.error("邮箱格式不正确");
            if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(email) = {0}", normalizedEmail).ne("user_id", existUser.getUserId())) > 0) return Result.error("邮箱已被注册");
            user.setEmail(normalizedEmail);
        }
        if (user.getUserName() != null) existUser.setUserName(user.getUserName());
        if (user.getEmail() != null) existUser.setEmail(user.getEmail());
        if (user.getPhone() != null) existUser.setPhone(user.getPhone());
        if (user.getRole() != null) existUser.setRole(user.getRole());
        if (user.getAccountStatus() != null) existUser.setAccountStatus(user.getAccountStatus());
        if (user.getRealName() != null) existUser.setRealName(user.getRealName());
        if (user.getNumberId() != null) {
            Long effectiveMajorId = user.getMajorId() != null ? user.getMajorId() : existUser.getMajorId();
            if (StudentNumberValidator.existsInSameUniversity(userMapper, majorMapper, schoolMapper,
                    user.getNumberId(), effectiveMajorId, existUser.getUserId())) {
                return Result.error("该学号在本校已存在");
            }
        }
        if (user.getNumberId() != null) existUser.setNumberId(user.getNumberId());
        if (user.getGender() != null) existUser.setGender(user.getGender());
        if (user.getEduBg() != null) existUser.setEduBg(user.getEduBg());
        if (user.getMajorId() != null) existUser.setMajorId(user.getMajorId());
        if (user.getGradeYear() != null) existUser.setGradeYear(user.getGradeYear());
        if (user.getClassName() != null) existUser.setClassName(user.getClassName());
        if (user.getIsEnrolledStu() != null) existUser.setIsEnrolledStu(user.getIsEnrolledStu());

        Long previousAvatar = existUser.getAvatarFileId();
        Long previousBackground = existUser.getBackgroundFileId();
        Long previousPhoto = existUser.getIdPhotoFileId();
        if (user.getAvatarFileId() != null && !user.getAvatarFileId().equals(previousAvatar)) {
            String error = fileAccessPolicy.validatePublicFile(user.getAvatarFileId(), operator, true);
            if (error != null) return Result.error(error);
            existUser.setAvatarFileId(user.getAvatarFileId());
        }
        if (user.getBackgroundFileId() != null && !user.getBackgroundFileId().equals(previousBackground)) {
            String error = fileAccessPolicy.validatePublicFile(user.getBackgroundFileId(), operator, true);
            if (error != null) return Result.error(error);
            existUser.setBackgroundFileId(user.getBackgroundFileId());
        }
        if (user.getIdPhotoFileId() != null && !user.getIdPhotoFileId().equals(previousPhoto)) {
            String error = fileAccessPolicy.validatePrivateFile(user.getIdPhotoFileId(), operator, true);
            if (error != null) return Result.error(error);
            existUser.setIdPhotoFileId(user.getIdPhotoFileId());
            existUser.setIdPhotoUpdatedAt(LocalDateTime.now());
        }
        userMapper.updateById(existUser);
        if (roleChanged) adminRolePermissionService.initializeForPromotion(existUser, previousRole);
        if (!java.util.Objects.equals(previousAccountStatus, existUser.getAccountStatus())) {
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Users>()
                    .eq("user_id", existUser.getUserId()).set("ban_until", null)
                    .set("ban_reason", null).set("pre_ban_status", null));
        }
        if (statusChanged && "normal".equals(existUser.getAccountStatus())) certificateGrantService.linkCertificatesToUser(existUser.getUserId(), existUser.getNumberId(), null);
        List<String> fields = new ArrayList<>();
        if (emailChanged) fields.add("email");
        if (roleChanged) fields.add("role");
        if (roleChanged) fields.add("permissions");
        if (statusChanged) fields.add("accountStatus");
        if (!fields.isEmpty()) {
            accountSecurityService.revokeSessions(existUser, request, false);
            authStateChangeService.notifyChanged(existUser.getUserId());
            if (emailChanged) notifySecurityChange(previousEmail, fields);
        }
        fields.add("profile");
        securityAuditService.record(operator.getUserId(), existUser.getUserId(), "admin-user-update", fields);
        if (!java.util.Objects.equals(previousAvatar, existUser.getAvatarFileId())) fileAccessPolicy.deleteIfUnreferenced(previousAvatar, operator);
        if (!java.util.Objects.equals(previousBackground, existUser.getBackgroundFileId())) fileAccessPolicy.deleteIfUnreferenced(previousBackground, operator);
        if (!java.util.Objects.equals(previousPhoto, existUser.getIdPhotoFileId())) fileAccessPolicy.deleteIfUnreferenced(previousPhoto, operator);
        return Result.success("更新成功");
    }

    @GetMapping("/auth-list")
    public Result<Page<UserProfileVO>> getAuthList(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(defaultValue = "pending") String status, @RequestParam(required = false) String keyword) {

        Page<Users> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        QueryWrapper<Users> wrapper = new QueryWrapper<>();

        wrapper.eq("account_status", "pending");
        wrapper.isNotNull("student_card_file_id");
        wrapper.gt("student_card_file_id", 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("user_name", keyword).or().like("real_name", keyword).or().like("number_id", keyword));
        }
        wrapper.orderByAsc("user_id");

        userMapper.selectPage(page, wrapper);

        List<UserProfileVO> voList = page.getRecords().stream().map(u -> {
            UserProfileVO vo = convertToVo(u);
            if (u.getStudentCardFileId() != null) {
                FileSystem fs = fileSystemMapper.selectById(u.getStudentCardFileId());
                if (fs != null) {
                    vo.setStudentCardUrl("/api" + fs.getUrl());
                }
            }
            return vo;
        }).collect(Collectors.toList());

        Page<UserProfileVO> resultPage = new Page<>();
        BeanUtils.copyProperties(page, resultPage);
        resultPage.setRecords(voList);

        return Result.success(resultPage);
    }

    @GetMapping("/auth-history-list")
    public Result<Page<AuthAuditListVO>> getAuthHistoryList(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(defaultValue = "all") String status, @RequestParam(required = false) String keyword) {

        Page<AuditRecords> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        QueryWrapper<AuditRecords> wrapper = new QueryWrapper<>();

        wrapper.eq("biz_type", "user_auth");

        if (!"all".equalsIgnoreCase(status)) {
            wrapper.eq("status", status);
        }

        if (StringUtils.hasText(keyword)) {
            QueryWrapper<Users> userWrapper = new QueryWrapper<>();
            userWrapper.select("user_id").and(w -> w.like("user_name", keyword).or().like("real_name", keyword).or().like("number_id", keyword));
            List<Object> userIds = userMapper.selectObjs(userWrapper);
            if (userIds.isEmpty()) {
                return Result.success(new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size)));
            }
            wrapper.in("applicant_id", userIds);
        }

        wrapper.orderByDesc("create_time");

        auditRecordsMapper.selectPage(page, wrapper);

        List<AuthAuditListVO> voList = page.getRecords().stream().map(record -> {
            AuthAuditListVO vo = new AuthAuditListVO();
            BeanUtils.copyProperties(record, vo);

            if (record.getUserId() != null) {
                Users auditor = userMapper.selectById(record.getUserId());
                vo.setAuditorName(auditor != null ? auditor.getRealName() : "未知管理员");
            } else {
                vo.setAuditorName("系统自动");
            }

            if (record.getFileId() != null) {
                FileSystem fs = fileSystemMapper.selectById(record.getFileId());
                if (fs != null) {
                    vo.setStudentCardUrl("/api" + fs.getUrl());
                }
            }

            if (record.getApplicantId() != null) {
                Users applicant = userMapper.selectById(record.getApplicantId());
                if (applicant != null) {
                    vo.setRealName(applicant.getRealName());
                    vo.setNumberId(applicant.getNumberId());
                    vo.setUserAccount(applicant.getUserAccount());

                    if (applicant.getMajorId() != null) {
                        Major major = majorMapper.selectById(applicant.getMajorId());
                        if (major != null) {
                            vo.setMajorName(major.getMajorName());
                            School school = schoolMapper.selectById(major.getSchoolId());
                            if (school != null) {
                                vo.setSchoolName(school.getSchoolName());
                                University university = universityMapper.selectById(school.getUniversityId());
                                if (university != null) vo.setUniversityName(university.getUniversityName());
                            }
                        }
                    }
                }
            }
            return vo;
        }).collect(Collectors.toList());

        Page<AuthAuditListVO> resultPage = new Page<>();
        BeanUtils.copyProperties(page, resultPage);
        resultPage.setRecords(voList);

        return Result.success(resultPage);
    }

    @GetMapping("/latest-audit")
    public Result<AuditLogVO> getLatestAuditInfo(@RequestParam Long userId) {
        QueryWrapper<AuditRecords> wrapper = new QueryWrapper<>();
        wrapper.eq("applicant_id", userId).eq("biz_type", "user_auth").orderByDesc("create_time").last("LIMIT 1");

        AuditRecords record = auditRecordsMapper.selectOne(wrapper);
        if (record == null) {
            return Result.success(null);
        }

        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(record, vo);

        if (record.getUserId() != null) {
            Users auditor = userMapper.selectById(record.getUserId());
            vo.setAuditorName(auditor != null ? auditor.getRealName() : "未知管理员");
        }
        return Result.success(vo);
    }

    @PostMapping("/audit-auth")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> auditAuth(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = Long.valueOf(params.get("userId").toString());
        boolean pass = Boolean.parseBoolean(params.get("pass").toString());
        String reason = params.containsKey("reason") ? params.get("reason").toString() : "";
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");

        if (!pass && !StringUtils.hasText(reason)) return Result.error("驳回时必须填写审核意见");
        if (!StringUtils.hasText(reason)) return Result.error("审核意见不能为空");

        Users user = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", userId).last("FOR UPDATE"));
        if (user == null) return Result.error("用户不存在");
        UserProfileVO operator = SecurityUtils.currentUser(request);
        if (!SecurityUtils.canManageTarget(operator, user) || !SecurityUtils.hasPermission(operator, "manage_user_status")) return Result.error(403, "无权限审核该账号");
        if (!"pending".equals(user.getAccountStatus())) return Result.error("当前账号没有待审核申请");

        AuditRecords record = new AuditRecords();
        record.setBizType("user_auth");
        record.setFileId(user.getStudentCardFileId());
        record.setUserId(operatorId);
        record.setApplicantId(user.getUserId());
        record.setComments(reason);
        record.setStatus(pass ? "approved" : "rejected");
        record.setCreateTime(LocalDateTime.now());
        auditRecordsMapper.insert(record);

        if (pass) {
            user.setAccountStatus("normal");
        } else {
            user.setAccountStatus("restricted");
        }
        userMapper.updateById(user);

        if (pass) certificateGrantService.linkCertificatesToUser(userId, user.getNumberId(), null);
        accountSecurityService.revokeSessions(user, request, false);
        securityAuditService.record(operatorId, userId, "admin-identity-audit", List.of("accountStatus"));
        authStateChangeService.notifyChanged(userId);

        emailService.sendAuthAuditResultEmail(user.getEmail(), user.getRealName(), pass, reason);
        return Result.success("审核完成");
    }

    private UserProfileVO convertToVo(Users user) {
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(user, vo);
        vo.setPassword(null);
        if (user.getIdPhotoFileId() != null) {
            vo.setIdPhotoUrl("/api/user/id-photo/" + user.getUserId());
        }

        if (user.getMajorId() != null && user.getMajorId() != 0) {
            Major major = majorMapper.selectById(user.getMajorId());
            if (major != null) {
                vo.setMajorName(major.getMajorName());
                School school = schoolMapper.selectById(major.getSchoolId());
                if (school != null) {
                    vo.setSchoolName(school.getSchoolName());
                    vo.setSchoolId(school.getSchoolId());

                    University university = universityMapper.selectById(school.getUniversityId());
                    if (university != null) {
                        vo.setUniversityName(university.getUniversityName());
                        vo.setUniversityId(university.getUniversityId());
                    }
                }
            }
        }
        return vo;
    }

    @GetMapping("/oj-accounts")
    public Result<IPage<UserOjAccountVO>> getOjAccountList(@RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "10") Integer size,
                                                           @RequestParam(required = false) String keyword) {
        Page<UserOjAccountVO> pageParam = new Page<>(SecurityUtils.normalizePage(page), SecurityUtils.clampPageSize(size));
        return Result.success(ojUserAccountMapper.selectUserOjAccounts(pageParam, keyword));
    }

    @PostMapping("/oj-account/update")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateOjAccount(@RequestBody OjAccountUpdateDTO dto, HttpServletRequest request) {
        if (dto.getUserId() == null) {
            return Result.error("用户ID不能为空");
        }
        if (StringUtils.hasText(dto.getNewcoderId()) && !dto.getNewcoderId().matches("\\d+"))
            return Result.error("牛客账号请填写竞赛个人主页 /profile/ 后的数字 UID，不是昵称或学号");
        if (StringUtils.hasText(dto.getLuoguId()) && !dto.getLuoguId().matches("\\d+"))
            return Result.error("洛谷账号请填写个人主页 /user/ 后的数字 UID，不是昵称");

        Users target = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", dto.getUserId()).last("FOR UPDATE"));
        UserProfileVO operator = SecurityUtils.currentUser(request);
        if (!SecurityUtils.canManageTarget(operator, target)) return Result.error(403, "无权修改该用户的账号绑定");
        OjUserAccount account = ojUserAccountMapper.selectById(dto.getUserId());
        boolean isNew = false;

        if (account == null) {
            account = new OjUserAccount();
            account.setUserId(dto.getUserId());
            isNew = true;
        }

        account.setCodeforcesId(dto.getCodeforcesId());
        account.setAtcoderId(dto.getAtcoderId());
        account.setLuoguId(dto.getLuoguId());
        account.setNewcoderId(dto.getNewcoderId());
        account.setCwnuojId(dto.getCwnuojId());
        if (!com.cwnupaa.backend.utils.OjBindingPolicy.validQojHandle(dto.getQojId())
                || !com.cwnupaa.backend.utils.OjBindingPolicy.validQojHandle(dto.getUcupId()))
            return Result.error("QOJ / UCup 请填写登录用户名，不要填队名或完整网址");
        if (dto.getQojId() != null) account.setQojId(dto.getQojId().trim());
        if (dto.getUcupId() != null) account.setUcupId(dto.getUcupId().trim());

        if (isNew) {
            ojUserAccountMapper.insert(account);
        } else {
            ojUserAccountMapper.updateById(account);
        }

        securityAuditService.record(operator.getUserId(), dto.getUserId(), "admin-oj-account-update", List.of("ojAccounts"));
        return Result.success("更新成功");
    }

    private void notifySecurityChange(String previousEmail, List<String> fields) {
        if (!StringUtils.hasText(previousEmail)) return;
        Runnable send = () -> { try { emailService.sendAccountSecurityChangedEmail(previousEmail, fields); } catch (Exception e) { org.slf4j.LoggerFactory.getLogger(AdminUserController.class).warn("账号安全变更通知发送失败"); } };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }
}
