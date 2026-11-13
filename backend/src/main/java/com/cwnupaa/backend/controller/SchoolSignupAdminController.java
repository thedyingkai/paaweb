package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.utils.AdvisorNames;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.config.SignupWriteGuard;
import com.cwnupaa.backend.dto.UserImportDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EventSignupNotificationService;
import com.cwnupaa.backend.service.EventSignupPolicy;
import com.cwnupaa.backend.service.EventRosterService;
import com.cwnupaa.backend.service.SignupTeamIdentityService;
import com.cwnupaa.backend.service.SignupCredentialService;
import com.cwnupaa.backend.utils.Md5Util;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.vo.EventSignupVO;
import com.cwnupaa.backend.vo.TeamVO;
import com.cwnupaa.backend.vo.TeamMemberVO;
import com.cwnupaa.backend.service.SecurityAuditService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.service.CertificateGrantService;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class SchoolSignupAdminController {

    private static final List<String> GPLT_TYPES = Arrays.asList("星芒初绽", "长风破浪", "凌霄问鼎");
    private static final List<String> LANQIAO_TYPES = Arrays.asList("C/C++程序设计", "Python程序设计", "Java软件开发");

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private SchoolAdminApplicationMapper schoolAdminApplicationMapper;
    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private TeamMemberMapper teamMemberMapper;
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private SignupPersonalMapper signupPersonalMapper;
    @Autowired
    private SignupTeamMapper signupTeamMapper;
    @Autowired
    private SignupTeamDetailMapper signupTeamDetailMapper;
    @Autowired
    private EventSignupNotificationService signupNotifications;
    @Autowired private EventSignupPolicy signupPolicy;
    @Autowired private SignupTeamIdentityService signupTeamIdentity;
    @Autowired private SignupCredentialService signupCredentials;
    @Autowired private SecurityAuditService securityAuditService;
    @Autowired private FileAccessPolicy fileAccessPolicy;
    @Autowired private CertificateGrantService certificateGrantService;

    @GetMapping("/admin/school-signup/school-profile")
    public Result<Map<String, Object>> getSchoolProfile(HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");

        University university = universityMapper.selectById(universityId);
        if (university == null) return Result.error("学校不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("universityId", university.getUniversityId());
        data.put("universityName", university.getUniversityName());
        data.put("logoFileId", university.getLogoFileId());
        if (university.getLogoFileId() != null) {
            FileSystem logo = fileSystemMapper.selectById(university.getLogoFileId());
            if (logo != null) data.put("logoUrl", "/api/file/image/" + logo.getFileId());
        }
        data.put("collegeCount", schoolMapper.selectCount(new QueryWrapper<School>().eq("university_id", universityId)));
        data.put("userCount", countUniversityUsers(universityId));
        return Result.success(data);
    }

    @PostMapping("/admin/school-signup/school-profile")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateSchoolProfile(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");
        University university = universityMapper.selectById(universityId);
        if (university == null) return Result.error("学校不存在");

        String name = params.get("universityName") == null ? "" : params.get("universityName").toString().trim();
        if (!StringUtils.hasText(name)) return Result.error("学校名称不能为空");
        if (name.length() > 100) return Result.error("学校名称不能超过100字");
        Long duplicate = universityMapper.selectCount(new QueryWrapper<University>()
                .eq("university_name", name).ne("university_id", universityId));
        if (duplicate > 0) return Result.error("学校名称已存在");

        university.setUniversityName(name);
        Long previousLogoId = university.getLogoFileId();
        if (params.containsKey("logoFileId")) {
            Long logoFileId = parseLong(params.get("logoFileId"));
            if (logoFileId != null) {
                String fileError = fileAccessPolicy.validatePublicFile(logoFileId, getSessionUser(request), true);
                if (fileError != null) return Result.error(fileError);
            }
            university.setLogoFileId(logoFileId);
        }
        universityMapper.updateById(university);
        if (!Objects.equals(previousLogoId, university.getLogoFileId())) fileAccessPolicy.deleteIfUnreferenced(previousLogoId, getSessionUser(request));
        return Result.success("学校信息已保存");
    }

    @GetMapping("/admin/school-signup/organization")
    public Result<Map<String, Object>> getSchoolOrganization(HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");
        List<Map<String, Object>> colleges = new ArrayList<>();
        for (School school : schoolMapper.selectList(new QueryWrapper<School>()
                .eq("university_id", universityId).orderByAsc("school_id"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("schoolId", school.getSchoolId());
            item.put("schoolName", school.getSchoolName());
            item.put("majors", majorMapper.selectList(new QueryWrapper<Major>()
                    .eq("school_id", school.getSchoolId()).orderByAsc("major_id")));
            colleges.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("universityId", universityId);
        data.put("colleges", colleges);
        return Result.success(data);
    }

    @PostMapping("/admin/school-signup/organization/college/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> saveSchoolCollege(@RequestBody School school, HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");
        if (school == null || !StringUtils.hasText(school.getSchoolName())) return Result.error("学院名称不能为空");
        String name = school.getSchoolName().trim();
        if (name.length() > 100) return Result.error("学院名称不能超过100字");
        if (school.getSchoolId() == null) {
            if (schoolMapper.selectCount(new QueryWrapper<School>().eq("university_id", universityId).eq("school_name", name)) > 0) {
                return Result.error("该学校下已存在同名学院");
            }
            school.setUniversityId(universityId);
            school.setSchoolName(name);
            schoolMapper.insert(school);
        } else {
            School target = schoolMapper.selectById(school.getSchoolId());
            if (target == null || !universityId.equals(target.getUniversityId())) return Result.error("只能维护本校学院");
            if (schoolMapper.selectCount(new QueryWrapper<School>().eq("university_id", universityId)
                    .eq("school_name", name).ne("school_id", school.getSchoolId())) > 0) return Result.error("该学校下已存在同名学院");
            target.setSchoolName(name);
            schoolMapper.updateById(target);
        }
        return Result.success("学院已保存");
    }

    @DeleteMapping("/admin/school-signup/organization/college/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteSchoolCollege(@PathVariable Long id, HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");
        School school = schoolMapper.selectById(id);
        if (school == null || !universityId.equals(school.getUniversityId())) return Result.error("只能维护本校学院");
        if (majorMapper.selectCount(new QueryWrapper<Major>().eq("school_id", id)) > 0) return Result.error("该学院下存在专业，禁止删除");
        schoolMapper.deleteById(id);
        return Result.success("学院已删除");
    }

    @PostMapping("/admin/school-signup/organization/major/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> saveSchoolMajor(@RequestBody Major major, HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");
        if (major == null || major.getSchoolId() == null || !StringUtils.hasText(major.getMajorName())) return Result.error("学院和专业名称不能为空");
        if (!isSchoolInUniversity(major.getSchoolId(), universityId)) return Result.error("专业所属学院不属于本校");
        String name = major.getMajorName().trim();
        if (name.length() > 100) return Result.error("专业名称不能超过100字");
        if (major.getMajorId() == null) {
            if (majorMapper.selectCount(new QueryWrapper<Major>().eq("school_id", major.getSchoolId()).eq("major_name", name)) > 0) return Result.error("该学院下已存在同名专业");
            major.setMajorName(name);
            if (major.getSchoolingYears() == null) major.setSchoolingYears(4);
            majorMapper.insert(major);
        } else {
            Major target = majorMapper.selectById(major.getMajorId());
            if (target == null || !isSchoolInUniversity(target.getSchoolId(), universityId)) return Result.error("只能维护本校专业");
            if (majorMapper.selectCount(new QueryWrapper<Major>().eq("school_id", major.getSchoolId()).eq("major_name", name).ne("major_id", major.getMajorId())) > 0) return Result.error("该学院下已存在同名专业");
            target.setMajorName(name);
            target.setSchoolId(major.getSchoolId());
            if (major.getSchoolingYears() != null) target.setSchoolingYears(major.getSchoolingYears());
            majorMapper.updateById(target);
        }
        return Result.success("专业已保存");
    }

    @DeleteMapping("/admin/school-signup/organization/major/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteSchoolMajor(@PathVariable Long id, HttpServletRequest request) {
        Long universityId = requireManagedUniversityId(request);
        if (universityId == null) return Result.error(403, "无学校报名管理员权限");
        Major major = majorMapper.selectById(id);
        if (major == null || !isSchoolInUniversity(major.getSchoolId(), universityId)) return Result.error("只能维护本校专业");
        if (userMapper.selectCount(new QueryWrapper<Users>().eq("major_id", id)) > 0) return Result.error("该专业下存在用户，禁止删除");
        majorMapper.deleteById(id);
        return Result.success("专业已删除");
    }

    @GetMapping("/admin/school-signup/students/list")
    public Result<Page<UserProfileVO>> listSchoolStudents(@RequestParam(defaultValue = "1") Integer current,
                                                          @RequestParam(defaultValue = "10") Integer size,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Long majorId,
                                                          @RequestParam(required = false) Long collegeId,
                                                          @RequestParam(required = false) Integer gender,
                                                          @RequestParam(required = false) String gradeYear,
                                                          @RequestParam(required = false) Integer isEnrolledStu,
                                                          @RequestParam(required = false) String accountStatus,
                                                          HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");

        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("role", "student");
        applySchoolScope(wrapper, schoolId, majorId, collegeId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("number_id", keyword)
                    .or().like("user_account", keyword)
                    .or().like("real_name", keyword)
                    .or().like("user_name", keyword)
                    .or().like("email", keyword)
                    .or().like("phone", keyword));
        }
        if (gender != null) wrapper.eq("gender", gender);
        if (StringUtils.hasText(gradeYear)) wrapper.eq("grade_year", gradeYear);
        if (isEnrolledStu != null) wrapper.eq("is_enrolled_stu", isEnrolledStu);
        if (StringUtils.hasText(accountStatus)) wrapper.eq("account_status", accountStatus);
        wrapper.orderByDesc("register_time");

        Page<Users> page = userMapper.selectPage(new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size)), wrapper);
        Page<UserProfileVO> result = new Page<>();
        BeanUtils.copyProperties(page, result);
        result.setRecords(page.getRecords().stream().map(this::convertUserToVo).collect(Collectors.toList()));
        return Result.success(result);
    }

    @GetMapping("/admin/school-signup/students/search")
    public Result<List<UserProfileVO>> searchSchoolStudents(@RequestParam(required = false) String keyword,
                                                            HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");
        if (!StringUtils.hasText(keyword)) return Result.success(List.of());

        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("role", "student");
        applySchoolScope(wrapper, schoolId, null, null);
        wrapper.and(w -> w.like("user_account", keyword)
                .or().like("number_id", keyword)
                .or().like("email", keyword)
                .or().like("real_name", keyword)
                .or().like("user_name", keyword));
        wrapper.last("LIMIT 30");

        return Result.success(userMapper.selectList(wrapper).stream().map(this::convertUserToVo).collect(Collectors.toList()));
    }

    @PostMapping("/admin/school-signup/students/update")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateSchoolStudent(@RequestBody Users user, HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");
        if (user.getUserId() == null) return Result.error("用户ID不能为空");

        Users target = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", user.getUserId()).last("FOR UPDATE"));
        if (target == null) return Result.error("用户不存在");
        if (!"student".equals(target.getRole())) return Result.error("只能管理学生用户");
        if (!isUserInUniversity(target, schoolId)) return Result.error("只能管理本校学生");
        if (user.getMajorId() != null && !isMajorInUniversity(user.getMajorId(), schoolId)) return Result.error("专业不属于本校");

        UserProfileVO operator = getSessionUser(request);
        boolean emailChanged = user.getEmail() != null && !user.getEmail().equalsIgnoreCase(target.getEmail());
        if (emailChanged) return Result.error(403, "学校报名管理不能修改登录邮箱，请使用账号安全中心或联系系统管理员");
        if ("teacher".equals(user.getRole()) || "teacher".equals(user.getEduBg())) return Result.error("身份类型不合法");
        Long previousPhoto = target.getIdPhotoFileId();
        target.setRole("student");
        if (user.getUserName() != null) target.setUserName(user.getUserName());
        if (user.getEmail() != null) target.setEmail(user.getEmail());
        if (user.getPhone() != null) target.setPhone(user.getPhone());
        if (user.getRealName() != null) target.setRealName(user.getRealName());
        if (user.getNumberId() != null) {
            Long effectiveMajorId = user.getMajorId() != null ? user.getMajorId() : target.getMajorId();
            if (StudentNumberValidator.existsInSameUniversity(userMapper, majorMapper, schoolMapper,
                    user.getNumberId(), effectiveMajorId, target.getUserId())) {
                return Result.error("该学号在本校已存在");
            }
            target.setNumberId(user.getNumberId());
        }
        if (user.getGender() != null) target.setGender(user.getGender());
        if (user.getEduBg() != null) {
            if (!"undergraduate".equals(user.getEduBg()) && !"postgraduate".equals(user.getEduBg())) {
                return Result.error("学校报名管理员只能维护学生身份");
            }
            target.setEduBg(user.getEduBg());
        }
        if (user.getMajorId() != null) target.setMajorId(user.getMajorId());
        if (user.getGradeYear() != null) target.setGradeYear(user.getGradeYear());
        if (user.getClassName() != null) target.setClassName(user.getClassName());
        if (user.getIsEnrolledStu() != null) target.setIsEnrolledStu(user.getIsEnrolledStu());
        if (user.getIdPhotoFileId() != null && !user.getIdPhotoFileId().equals(target.getIdPhotoFileId())) {
            String fileError = fileAccessPolicy.validatePrivateFile(user.getIdPhotoFileId(), operator, true);
            if (fileError != null) return Result.error(fileError);
            target.setIdPhotoFileId(user.getIdPhotoFileId());
            target.setIdPhotoUpdatedAt(LocalDateTime.now());
        }

        userMapper.updateById(target);
        if ("normal".equals(target.getAccountStatus()) && (user.getRealName() != null || user.getNumberId() != null || user.getMajorId() != null)) certificateGrantService.linkCertificatesToUser(target.getUserId(), target.getNumberId(), null);
        securityAuditService.record(operator.getUserId(), target.getUserId(), "school-student-update", List.of("profile"));
        if (!Objects.equals(previousPhoto, target.getIdPhotoFileId())) fileAccessPolicy.deleteIfUnreferenced(previousPhoto, operator);
        return Result.success("更新成功");
    }

    @PostMapping("/admin/school-signup/students/import")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> importSchoolStudents(@RequestBody List<UserImportDTO> list, HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");
        if (list == null || list.isEmpty()) return Result.error("导入数据为空");

        University university = universityMapper.selectById(schoolId);
        if (university == null) return Result.error("学校不存在");

        int success = 0;
        int skip = 0;
        // 初始密码：优先使用导入表单设置的密码；留空则系统随机生成
        Map<String, String> generatedPasswords = new LinkedHashMap<>();
        // 当前管理范围已限定学校；记录本批次内学号，联合当前学校防止重复
        Set<String> seenNumbers = new HashSet<>();
        for (UserImportDTO dto : list) {
            try {
                if (isRemovedTeacherIdentity(dto)) {
                    skip++;
                    continue;
                }
                if (!StringUtils.hasText(dto.getNumberId()) || !StringUtils.hasText(dto.getRealName())) {
                    skip++;
                    continue;
                }
                String num = dto.getNumberId().trim();
                String majorName = StringUtils.hasText(dto.getMajorName()) ? dto.getMajorName().trim() : "未分配专业";
                Long importSchoolId = resolveImportSchoolId(dto, schoolId, request);
                if (importSchoolId == null) {
                    skip++;
                    continue;
                }
                Major major = majorMapper.selectOne(new QueryWrapper<Major>()
                        .eq("school_id", importSchoolId)
                        .eq("major_name", majorName));
                if (major == null) {
                    major = new Major();
                    major.setSchoolId(importSchoolId);
                    major.setMajorName(majorName);
                    major.setSchoolingYears(4);
                    majorMapper.insert(major);
                }

                // 同一学校（大学）内学号不能重复，不同学校（大学）之间的学号可以重复
                if (seenNumbers.contains(num)
                        || StudentNumberValidator.existsInSameUniversity(userMapper, majorMapper, schoolMapper,
                                num, major.getMajorId(), null)) {
                    skip++;
                    continue;
                }
                seenNumbers.add(num);

                String rawPassword = StringUtils.hasText(dto.getPassword())
                        ? dto.getPassword().trim()
                        : SecurityUtils.randomPassword();
                String defaultPwd = Md5Util.encrypt(rawPassword);
                if (!StringUtils.hasText(dto.getPassword())) {
                    generatedPasswords.put(num, rawPassword);
                }

                Users user = new Users();
                user.setNumberId(num);
                user.setUserAccount(SecurityUtils.pendingActivationAccount());
                user.setRealName(dto.getRealName().trim());
                user.setUserName(dto.getRealName().trim());
                user.setPassword(defaultPwd);
                user.setRole("student");
                user.setAccountStatus("pending_activation");
                user.setRegisterTime(LocalDateTime.now());
                user.setEduBg("研究生".equals(dto.getEduBg()) ? "postgraduate" : "undergraduate");
                user.setMajorId(major.getMajorId());
                // 导入格式不再包含年级，年级缺省取当前年份（grade_year 非空列）
                String gradeYear = StringUtils.hasText(dto.getGradeYear()) ? dto.getGradeYear() : String.valueOf(LocalDateTime.now().getYear());
                user.setGradeYear(gradeYear);
                user.setClassName(dto.getClassName());
                user.setIsEnrolledStu(1);
                userMapper.insert(user);
                success++;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(SchoolSignupAdminController.class).warn("操作失败，请检查请求参数和服务配置");
                skip++;
            }
        }
        StringBuilder msg = new StringBuilder("导入完成，成功 " + success + " 条，跳过 " + skip + " 条");
        if (!generatedPasswords.isEmpty()) {
            msg.append("；以下学号的初始密码为系统自动生成，请及时下发：");
            generatedPasswords.forEach((num, pwd) -> msg.append(num).append("=").append(pwd).append("，"));
            msg.setLength(msg.length() - 1);
        }
        return Result.success(msg.toString());
    }

    @PostMapping("/admin/school-signup/students/import/preview")
    public Result<Map<String, Object>> previewSchoolStudents(@RequestBody List<UserImportDTO> list, HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");
        if (list == null || list.isEmpty()) return Result.error("导入数据为空");
        University university = universityMapper.selectById(schoolId);
        if (university == null) return Result.error("学校不存在");

        List<Map<String, String>> successList = new ArrayList<>();
        List<Map<String, String>> failedList = new ArrayList<>();
        Set<String> seenNumbers = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            UserImportDTO dto = list.get(i);
            String numberId = dto == null || dto.getNumberId() == null ? "" : dto.getNumberId().trim();
            String realName = dto == null || dto.getRealName() == null ? "" : dto.getRealName().trim();
            String majorName = dto == null || dto.getMajorName() == null ? "" : dto.getMajorName().trim();
            String content = realName + " / " + numberId + " / " + majorName;
            if (isRemovedTeacherIdentity(dto)) {
                failedList.add(Map.of("content", content, "reason", "教师身份已停止使用"));
                continue;
            }
            if (!StringUtils.hasText(realName) || !StringUtils.hasText(numberId) || !StringUtils.hasText(majorName)) {
                failedList.add(Map.of("content", content, "reason", "第 " + (i + 2) + " 行姓名、学号或专业为空"));
                continue;
            }
            if (!seenNumbers.add(numberId)) {
                failedList.add(Map.of("content", content, "reason", "当前学校的学号在文件中重复"));
                continue;
            }
            if (resolveImportSchoolId(dto, schoolId, request) == null) {
                failedList.add(Map.of("content", content, "reason", "学院名称不存在或不属于本校"));
                continue;
            }
            Users existing = StudentNumberValidator.findByUniversityIdAndNumber(userMapper, schoolId, numberId);
            if (existing != null) {
                failedList.add(Map.of("content", content, "reason", "当前学校下已存在此学号"));
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

    private boolean isRemovedTeacherIdentity(UserImportDTO dto) {
        if (dto == null) return false;
        String role = dto.getRole() == null ? "" : dto.getRole().trim();
        String education = dto.getEduBg() == null ? "" : dto.getEduBg().trim();
        return List.of("teacher", "老师", "教师").contains(role)
                || List.of("teacher", "老师", "教师").contains(education);
    }

    @GetMapping("/admin/school-signup/teams")
    public Result<List<TeamVO>> listSchoolTeams(HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");

        List<TeamVO> teams = teamMapper.selectList(new QueryWrapper<Team>().orderByDesc("team_id")).stream()
                .filter(team -> {
                    Users leader = userMapper.selectById(team.getLeaderId());
                    return leader != null && isUserInUniversity(leader, schoolId);
                })
                .map(this::assembleTeamVO)
                .collect(Collectors.toList());
        return Result.success(teams);
    }

    @PostMapping("/admin/school-signup/teams/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> saveSchoolTeam(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");

        String teamName = params.get("teamName") == null ? "" : params.get("teamName").toString().trim();
        if (!StringUtils.hasText(teamName)) return Result.error("队伍名称不能为空");
        if (teamName.length() > 15) return Result.error("队伍名称不能超过15个字");

        Long teamId = params.get("teamId") == null ? null : Long.valueOf(params.get("teamId").toString());
        Long leaderId = params.get("leaderId") == null ? null : Long.valueOf(params.get("leaderId").toString());
        String introduce = params.get("introduce") == null ? null : params.get("introduce").toString();
        List<Long> memberIds = parseLongList(params.get("memberIds"));

        if (leaderId == null) return Result.error("请选择队长");
        Users leader = validateSchoolStudent(leaderId, schoolId, false);
        if (leader == null) return Result.error("队长必须是本校学生");

        List<Long> allMemberIds = new ArrayList<>();
        allMemberIds.add(leaderId);
        for (Long id : memberIds) {
            if (id != null && !allMemberIds.contains(id)) allMemberIds.add(id);
        }
        if (allMemberIds.size() > 5) return Result.error("队伍最多5人");
        for (Long id : allMemberIds) {
            if (validateSchoolStudent(id, schoolId, false) == null) return Result.error("成员必须全部为本校学生");
        }

        Team team = teamId == null ? new Team() : teamMapper.selectById(teamId);
        if (team == null) return Result.error("队伍不存在");
        if (teamId != null) {
            Users oldLeader = userMapper.selectById(team.getLeaderId());
            if (oldLeader == null || !isUserInUniversity(oldLeader, schoolId)) return Result.error("只能管理本校队伍");
        }

        team.setTeamName(teamName);
        team.setIntroduce(introduce);
        team.setLeaderId(leaderId);
        if (teamId == null) teamMapper.insert(team);
        else teamMapper.updateById(team);

        teamMemberMapper.delete(new QueryWrapper<TeamMember>().eq("team_id", team.getTeamId()));
        for (Long id : allMemberIds) {
            TeamMember tm = new TeamMember();
            tm.setTeamId(team.getTeamId());
            tm.setMemberId(id);
            tm.setStatus("1");
            teamMemberMapper.insert(tm);
        }

        return Result.success(teamId == null ? "创建成功" : "更新成功");
    }

    @PostMapping("/admin/school-signup/teams/delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteSchoolTeam(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");
        Long teamId = params.get("teamId");
        Team team = teamMapper.selectById(teamId);
        if (team == null) return Result.error("队伍不存在");
        Users leader = userMapper.selectById(team.getLeaderId());
        if (leader == null || !isUserInUniversity(leader, schoolId)) return Result.error("只能删除本校队伍");
        teamMemberMapper.delete(new QueryWrapper<TeamMember>().eq("team_id", teamId));
        teamMapper.deleteById(teamId);
        return Result.success("删除成功");
    }

    @GetMapping("/admin/school-signup/signups")
    public Result<Page<EventSignupVO>> listSchoolSignups(@RequestParam Long eventId,
                                                         @RequestParam(defaultValue = "1") Integer current,
                                                         @RequestParam(defaultValue = "10") Integer size,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String teamType,
                                                         @RequestParam(defaultValue = "summary") String viewType,
                                                         HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");

        Event event = eventMapper.selectById(eventId);
        if (event == null) return Result.error("赛事不存在");

        List<EventSignupVO> all = new ArrayList<>();
        if (event.getIsTeam() == 0) {
            QueryWrapper<SignupPersonal> wrapper = new QueryWrapper<>();
            wrapper.eq("event_id", eventId);
            if (StringUtils.hasText(status) && !"all".equals(status)) wrapper.eq("status", status);
            applySignupTypeFilter(wrapper, teamType);
            wrapper.orderByDesc("signup_time");
            for (SignupPersonal signup : signupPersonalMapper.selectList(wrapper)) {
                Users user = userMapper.selectById(signup.getUserId());
                if (user == null || !isUserInUniversity(user, schoolId)) continue;
                if (!matchesSignupKeyword(keyword, user, null, null)) continue;
                all.add(toPersonalSignupVO(signup, user));
            }
        } else {
            QueryWrapper<SignupTeam> wrapper = new QueryWrapper<>();
            wrapper.eq("event_id", eventId);
            if (StringUtils.hasText(status) && !"all".equals(status)) wrapper.eq("status", status);
            applySignupTypeFilter(wrapper, teamType);
            wrapper.orderByDesc("signup_time");
            for (SignupTeam signup : signupTeamMapper.selectList(wrapper)) {
                List<SignupTeamDetail> details = getSignupDetailsOrdered(signup.getSignupId());
                List<Users> members = loadUsersForDetails(details);
                Users leader = members.isEmpty() ? null : members.get(0);
                if (leader == null || !isUserInUniversity(leader, schoolId)) continue;
                if (!matchesSignupKeyword(keyword, leader, signup.getTeamName(), members)) continue;
                if ("detail".equals(viewType)) {
                    for (int i = 0; i < members.size(); i++) {
                        all.add(toTeamSignupDetailVO(signup, members.get(i), i));
                    }
                } else {
                    all.add(toTeamSignupSummaryVO(signup, leader, members));
                }
            }
        }

        Page<EventSignupVO> result = paginate(all, current, size);
        return Result.success(result);
    }

    @PostMapping("/admin/school-signup/signup")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> schoolAdminSignup(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        UserProfileVO operator = getSessionUser(request);
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null || operator == null) return Result.error(403, "无学校报名管理员权限");
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getSignupStart())) return Result.error("报名未开始");
        if (event.getSignupEnd() != null && !now.isBefore(event.getSignupEnd())) return Result.error("报名已截止");

        Integer isRanking = params.get("isRanking") == null ? 1 : Integer.valueOf(params.get("isRanking").toString());
        String advisor = AdvisorNames.normalize(params.get("advisor"));
        String teamType = params.get("teamType") == null ? null : params.get("teamType").toString();

        List<String> failures = new ArrayList<>();
        int success = 0;
        if (event.getIsTeam() == 0) {
            List<Long> userIds = parseLongList(params.get("userIds"));
            if (userIds.isEmpty()) return Result.error("请选择学生");
            for (Long userId : userIds) {
                try {
                    registerPersonal(event, userId, schoolId, operator.getUserId(), isRanking, advisor, teamType);
                    success++;
                } catch (IllegalArgumentException ex) {
                    failures.add(ex.getMessage());
                }
            }
        } else {
            List<Long> teamIds = parseLongList(params.get("teamIds"));
            if (teamIds.isEmpty()) return Result.error("请选择队伍");
            for (Long teamId : teamIds) {
                try {
                    registerTeam(event, teamId, schoolId, operator.getUserId(), isRanking, advisor, teamType);
                    success++;
                } catch (IllegalArgumentException ex) {
                    failures.add(ex.getMessage());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failure", failures.size());
        result.put("failedList", failures);
        return Result.success(result);
    }

    @PostMapping("/admin/school-signup/cancel")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelSchoolSignups(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null) return Result.error(403, "无学校报名管理员权限");
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);
        if (signupPolicy.isCancellationClosed(event, LocalDateTime.now()))
            return Result.error("报名已结束，无法取消报名");
        List<Long> signupIds = parseLongList(params.get("signupIds"));
        if (signupIds.isEmpty()) return Result.error("请选择报名记录");

        // Authorize the complete batch before revoking any credentials.
        List<SignupPersonal> personal = new ArrayList<>();
        List<SignupTeam> teams = new ArrayList<>();
        for (Long signupId : new HashSet<>(signupIds)) {
            if (event.getIsTeam() == 0) {
                SignupPersonal signup = signupPersonalMapper.selectById(signupId);
                if (signup == null || !signup.getEventId().equals(eventId)) continue;
                Users user = userMapper.selectById(signup.getUserId());
                if (user == null || !isUserInUniversity(user, schoolId)) return Result.error("只能取消本校报名记录");
                personal.add(signup);
            } else {
                SignupTeam signup = signupTeamMapper.selectById(signupId);
                if (signup == null || !signup.getEventId().equals(eventId)) continue;
                if (!isSignupTeamInSchool(signup, schoolId)) return Result.error("只能取消本校报名记录");
                teams.add(signup);
            }
        }
        for (SignupPersonal signup : personal) {
            signup.setStatus("cancelled");
            signupPersonalMapper.updateById(signup);
            signupCredentials.clearPersonal(signup.getSignupId());
            signupNotifications.invalidate("personal", signup.getSignupId());
        }
        for (SignupTeam signup : teams) {
            signup.setStatus("cancelled");
            signupTeamMapper.updateById(signup);
            signupCredentials.clearTeam(signup.getSignupId());
            signupNotifications.invalidate("team", signup.getSignupId());
        }
        return Result.success("已取消报名");
    }

    @PostMapping("/admin/school-signup/reapply")
    @SignupWriteGuard
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> reapplySchoolSignups(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        UserProfileVO operator = getSessionUser(request);
        Long schoolId = requireManagedUniversityId(request);
        if (schoolId == null || operator == null) return Result.error(403, "无学校报名管理员权限");
        Long eventId = Long.valueOf(params.get("eventId").toString());
        Event event = eventMapper.selectForSignupUpdate(eventId);
        if (event == null) return Result.error("赛事不存在");
        if (EventRosterService.isRoster(event)) return Result.error(403, EventRosterService.LOCKED_MESSAGE);

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getSignupStart())) return Result.error("报名未开始");
        if (event.getSignupEnd() != null && !now.isBefore(event.getSignupEnd())) return Result.error("报名已截止");

        List<Long> signupIds = parseLongList(params.get("signupIds"));
        if (signupIds.isEmpty()) return Result.error("请选择报名记录");
        List<String> failures = new ArrayList<>();
        int success = 0;

        for (Long signupId : new HashSet<>(signupIds)) {
            try {
                if (event.getIsTeam() == 0) {
                    reapplyPersonal(event, signupId, schoolId, operator.getUserId());
                } else {
                    reapplyTeam(event, signupId, schoolId, operator.getUserId());
                }
                success++;
            } catch (IllegalArgumentException ex) {
                failures.add(ex.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failure", failures.size());
        result.put("failedList", failures);
        return Result.success(result);
    }

    private void registerPersonal(Event event, Long userId, Long schoolId, Long operatorId, Integer isRanking, String advisor, String inputType) {
        Users user = validateSchoolStudent(userId, schoolId, true);
        if (user == null) throw new IllegalArgumentException("学生不存在、非本校学生或账号状态异常：" + userId);
        requireIdPhoto(user);
        String finalType = resolvePersonalType(event, user, inputType);
        if (event.getMaxSignup() != null && signupPersonalMapper.countApproved(event.getEventId()) >= event.getMaxSignup()) throw new IllegalArgumentException(user.getRealName() + "：名额已满");
        if (signupTeamMapper.countActiveByMember(event.getEventId(), userId) > 0) throw new IllegalArgumentException(user.getRealName() + "：已随队伍报名");
        SignupPersonal signup = signupPersonalMapper.selectOne(new QueryWrapper<SignupPersonal>().eq("event_id", event.getEventId()).eq("user_id", userId));
        if (signup != null && ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus()))) {
            throw new IllegalArgumentException(user.getRealName() + "：已报名或审核中");
        }
        if (signup == null) {
            signup = new SignupPersonal();
            signup.setEventId(event.getEventId());
            signup.setUserId(userId);
        }
        signup.setStatus("pending");
        if (signup.getSignupTime() == null) signup.setSignupTime(LocalDateTime.now());
        signup.setIsRanking(isRanking);
        signup.setAdvisor(advisor);
        signup.setType(finalType);
        signup.setSignupSource("school_admin");
        signup.setCreatedByUserId(operatorId);
        if (signup.getSignupId() == null) signupPersonalMapper.insert(signup);
        else signupPersonalMapper.updateById(signup);
        sendSignupMail(user, event, signup.getSignupId(), "pending");
    }

    private void registerTeam(Event event, Long teamId, Long schoolId, Long operatorId, Integer isRanking, String advisor, String inputType) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) throw new IllegalArgumentException("队伍不存在：" + teamId);
        Users leader = userMapper.selectById(team.getLeaderId());
        if (leader == null || !isUserInUniversity(leader, schoolId)) throw new IllegalArgumentException(team.getTeamName() + "：不是本校队伍");
        List<Long> memberIds = collectTeamMemberIds(team, teamMemberMapper.selectList(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("status", "1")));
        List<Users> members = loadUsersByIdsInOrder(memberIds);
        if (members.size() != memberIds.size() || members.stream().anyMatch(member -> !isUserInUniversity(member, schoolId))) {
            throw new IllegalArgumentException(team.getTeamName() + "：队伍成员必须全部来自本校");
        }
        validateTeamSignupMembers(event, members, team.getTeamName());
        requireMembersIdPhoto(members, team.getTeamName());
        String finalType = resolveTeamType(event, members, inputType);
        if (event.getMaxSignup() != null && signupTeamMapper.countApproved(event.getEventId()) >= event.getMaxSignup()) throw new IllegalArgumentException(team.getTeamName() + "：队伍名额已满");
        checkTeamConflicts(event.getEventId(), memberIds, team.getTeamName());

        String teamName = normalizeTeamName(team.getTeamName());
        SignupTeam signup = signupTeamIdentity.findForTeam(event.getEventId(), team);
        if (signup != null && ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus()))) {
            throw new IllegalArgumentException(teamName + "：已报名或审核中");
        }
        if (signup == null) {
            signup = new SignupTeam();
            signup.setSourceTeamId(team.getTeamId());
            signup.setEventId(event.getEventId());
        }
        signup.setTeamName(teamName);
        signup.setStatus("pending");
        if (signup.getSignupTime() == null) signup.setSignupTime(LocalDateTime.now());
        signup.setIsRanking(isRanking);
        signup.setAdvisor(advisor);
        signup.setType(finalType);
        signup.setSignupSource("school_admin");
        signup.setCreatedByUserId(operatorId);
        if (signup.getSignupId() == null) signupTeamMapper.insert(signup);
        else signupTeamMapper.updateById(signup);
        replaceSignupTeamDetails(signup.getSignupId(), memberIds);
        signupNotifications.enqueue(event.getEventId(), "team", signup.getSignupId(), getSignupTeamEmailTargets(signup.getSignupId()), event.getName(), teamName, "pending", null);
    }

    private void reapplyPersonal(Event event, Long signupId, Long schoolId, Long operatorId) {
        SignupPersonal signup = signupPersonalMapper.selectById(signupId);
        if (signup == null || !signup.getEventId().equals(event.getEventId())) throw new IllegalArgumentException("报名记录不存在：" + signupId);
        Users user = validateSchoolStudent(signup.getUserId(), schoolId, true);
        if (user == null) throw new IllegalArgumentException("学生不存在、非本校学生或账号状态异常：" + signupId);
        requireIdPhoto(user);
        if ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus())) throw new IllegalArgumentException(user.getRealName() + "：已报名或审核中");
        if (signupTeamMapper.countActiveByMember(event.getEventId(), user.getUserId()) > 0) throw new IllegalArgumentException(user.getRealName() + "：已随队伍报名");
        signup.setStatus("pending");
        if (signup.getSignupTime() == null) signup.setSignupTime(LocalDateTime.now());
        signup.setSignupSource("school_admin");
        signup.setCreatedByUserId(operatorId);
        signupPersonalMapper.updateById(signup);
        sendSignupMail(user, event, signup.getSignupId(), "pending");
    }

    private void reapplyTeam(Event event, Long signupId, Long schoolId, Long operatorId) {
        SignupTeam signup = signupTeamMapper.selectById(signupId);
        if (signup == null || !signup.getEventId().equals(event.getEventId())) throw new IllegalArgumentException("报名记录不存在：" + signupId);
        if ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus())) throw new IllegalArgumentException(signup.getTeamName() + "：已报名或审核中");
        if (!isSignupTeamInSchool(signup, schoolId)) throw new IllegalArgumentException(signup.getTeamName() + "：不是本校报名记录");
        List<Long> memberIds = getSignupDetailsOrdered(signupId).stream().map(SignupTeamDetail::getUserId).collect(Collectors.toList());
        List<Users> members = loadUsersByIdsInOrder(memberIds);
        if (members.size() != memberIds.size() || members.stream().anyMatch(member -> !isUserInUniversity(member, schoolId))) {
            throw new IllegalArgumentException(signup.getTeamName() + "：队伍成员必须全部来自本校");
        }
        validateTeamSignupMembers(event, members, signup.getTeamName());
        requireMembersIdPhoto(members, signup.getTeamName());
        checkTeamConflicts(event.getEventId(), memberIds, signup.getTeamName());
        signup.setStatus("pending");
        if (signup.getSignupTime() == null) signup.setSignupTime(LocalDateTime.now());
        signup.setSignupSource("school_admin");
        signup.setCreatedByUserId(operatorId);
        signupTeamMapper.updateById(signup);
        signupNotifications.enqueue(event.getEventId(), "team", signup.getSignupId(), getSignupTeamEmailTargets(signup.getSignupId()), event.getName(), signup.getTeamName(), "pending", null);
    }

    private UserProfileVO getSessionUser(HttpServletRequest request) {
        Object requestUser = request.getAttribute("user");
        if (requestUser instanceof UserProfileVO user) return user;
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object sessionUser = session.getAttribute("user");
        return sessionUser instanceof UserProfileVO user ? user : null;
    }

    private Long requireManagedUniversityId(HttpServletRequest request) {
        UserProfileVO user = getSessionUser(request);
        if (user == null || !"normal".equals(user.getAccountStatus()) || !"school_admin".equals(user.getRole())) return null;
        return resolveManagedUniversityId(user.getUserId());
    }

    private Long resolveManagedUniversityId(Long userId) {
        SchoolAdminApplication approved = schoolAdminApplicationMapper.selectOne(new QueryWrapper<SchoolAdminApplication>()
                .eq("user_id", userId)
                .eq("status", "approved")
                .orderByDesc("audit_time", "create_time")
                .last("LIMIT 1"));
        if (approved != null && approved.getUniversityId() != null) return approved.getUniversityId();
        Users user = userMapper.selectById(userId);
        return user == null ? null : resolveUniversityIdByUser(user);
    }

    private Long resolveSchoolIdByUser(Users user) {
        if (user == null || user.getMajorId() == null) return null;
        Major major = majorMapper.selectById(user.getMajorId());
        return major == null ? null : major.getSchoolId();
    }

    private Long resolveUniversityIdByUser(Users user) {
        Long schoolId = resolveSchoolIdByUser(user);
        if (schoolId == null) return null;
        School school = schoolMapper.selectById(schoolId);
        return school == null ? null : school.getUniversityId();
    }

    private boolean isUserInUniversity(Users user, Long universityId) {
        Long userUniversityId = resolveUniversityIdByUser(user);
        return userUniversityId != null && userUniversityId.equals(universityId);
    }

    private boolean isSchoolInUniversity(Long schoolId, Long universityId) {
        School school = schoolMapper.selectById(schoolId);
        return school != null && universityId.equals(school.getUniversityId());
    }

    private boolean isMajorInUniversity(Long majorId, Long universityId) {
        Major major = majorMapper.selectById(majorId);
        return major != null && isSchoolInUniversity(major.getSchoolId(), universityId);
    }

    private Users validateSchoolStudent(Long userId, Long schoolId, boolean requireNormal) {
        Users user = userMapper.selectById(userId);
        if (user == null || !"student".equals(user.getRole()) || !isUserInUniversity(user, schoolId)) return null;
        if (requireNormal && !"normal".equals(user.getAccountStatus())) return null;
        return user;
    }

    private void applySchoolScope(QueryWrapper<Users> wrapper, Long schoolId, Long majorId, Long collegeId) {
        if (majorId != null) {
            if (isMajorInUniversity(majorId, schoolId)) wrapper.eq("major_id", majorId);
            else wrapper.eq("major_id", -1);
            return;
        }
        QueryWrapper<School> schoolQuery = new QueryWrapper<School>().eq("university_id", schoolId);
        if (collegeId != null) schoolQuery.eq("school_id", collegeId);
        List<Long> schoolIds = schoolMapper.selectList(schoolQuery)
                .stream().map(School::getSchoolId).collect(Collectors.toList());
        List<Long> majorIds = schoolIds.isEmpty() ? List.of() : majorMapper.selectList(new QueryWrapper<Major>().in("school_id", schoolIds))
                .stream().map(Major::getMajorId).collect(Collectors.toList());
        if (majorIds.isEmpty()) wrapper.eq("major_id", -1);
        else wrapper.in("major_id", majorIds);
    }

    private Long resolveImportSchoolId(UserImportDTO dto, Long universityId, HttpServletRequest request) {
        String schoolName = dto == null || dto.getSchoolName() == null ? "" : dto.getSchoolName().trim();
        if (StringUtils.hasText(schoolName)) {
            School school = schoolMapper.selectOne(new QueryWrapper<School>()
                    .eq("university_id", universityId).eq("school_name", schoolName).last("LIMIT 1"));
            return school == null ? null : school.getSchoolId();
        }
        UserProfileVO operator = getSessionUser(request);
        if (operator == null) return null;
        SchoolAdminApplication approved = schoolAdminApplicationMapper.selectOne(new QueryWrapper<SchoolAdminApplication>()
                .eq("user_id", operator.getUserId()).eq("status", "approved")
                .orderByDesc("audit_time", "create_time").last("LIMIT 1"));
        return approved != null && isSchoolInUniversity(approved.getSchoolId(), universityId) ? approved.getSchoolId() : null;
    }

    private long countUniversityUsers(Long universityId) {
        List<Long> schoolIds = schoolMapper.selectList(new QueryWrapper<School>().eq("university_id", universityId))
                .stream().map(School::getSchoolId).collect(Collectors.toList());
        if (schoolIds.isEmpty()) return 0;
        List<Long> majorIds = majorMapper.selectList(new QueryWrapper<Major>().in("school_id", schoolIds))
                .stream().map(Major::getMajorId).collect(Collectors.toList());
        return majorIds.isEmpty() ? 0 : userMapper.selectCount(new QueryWrapper<Users>().in("major_id", majorIds));
    }

    private Long parseLong(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) return null;
        try { return Long.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }

    private boolean isImageFile(FileSystem file) {
        if (file == null || file.getType() == null) return false;
        return List.of("jpg", "jpeg", "png").contains(file.getType().toLowerCase());
    }

    private UserProfileVO convertUserToVo(Users user) {
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(user, vo);
        vo.setPassword(null);
        if (user.getAvatarFileId() != null) {
            FileSystem fs = fileSystemMapper.selectById(user.getAvatarFileId());
            if (fs != null) vo.setAvatarUrl("/api" + fs.getUrl());
        }
        if (user.getIdPhotoFileId() != null) {
            vo.setIdPhotoUrl("/api/user/id-photo/" + user.getUserId());
        }
        fillUserSchoolText(vo, user);
        return vo;
    }

    private void fillUserSchoolText(UserProfileVO vo, Users user) {
        if (user == null || user.getMajorId() == null) return;
        Major major = majorMapper.selectById(user.getMajorId());
        if (major == null) return;
        vo.setMajorName(major.getMajorName());
        School school = schoolMapper.selectById(major.getSchoolId());
        if (school == null) return;
        vo.setSchoolId(school.getSchoolId());
        vo.setSchoolName(school.getSchoolName());
        University university = universityMapper.selectById(school.getUniversityId());
        if (university != null) {
            vo.setUniversityId(university.getUniversityId());
            vo.setUniversityName(university.getUniversityName());
        }
    }

    private TeamVO assembleTeamVO(Team team) {
        TeamVO vo = new TeamVO();
        BeanUtils.copyProperties(team, vo);
        Users leader = userMapper.selectById(team.getLeaderId());
        if (leader != null) vo.setLeaderName(leader.getRealName());
        List<Long> memberIds = collectTeamMemberIds(team, teamMemberMapper.selectList(new QueryWrapper<TeamMember>().eq("team_id", team.getTeamId()).eq("status", "1")));
        List<TeamMemberVO> members = loadUsersByIdsInOrder(memberIds).stream().map(user -> {
            TeamMemberVO member = new TeamMemberVO();
            member.setUserId(user.getUserId());
            member.setUserName(user.getUserName());
            member.setRealName(user.getRealName());
            member.setNumberId(user.getNumberId());
            if (user.getAvatarFileId() != null) member.setAvatarUrl("/api/file/image/" + user.getAvatarFileId());
            return member;
        }).collect(Collectors.toList());
        vo.setMembers(members);
        vo.setMembersContent(members.stream().map(TeamMemberVO::getRealName).filter(Objects::nonNull).collect(Collectors.joining(", ")));
        if (team.getTeamImageId() != null) {
            FileSystem fs = fileSystemMapper.selectById(team.getTeamImageId());
            if (fs != null) vo.setTeamAvatarUrl("/api" + fs.getUrl());
        }
        return vo;
    }

    private List<Long> parseLongList(Object raw) {
        if (!(raw instanceof List<?> rawList)) return new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) continue;
            try {
                ids.add(Long.valueOf(item.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private List<Long> collectTeamMemberIds(Team team, List<TeamMember> members) {
        List<Long> ids = new ArrayList<>();
        if (team != null && team.getLeaderId() != null) ids.add(team.getLeaderId());
        if (members != null) {
            for (TeamMember member : members) {
                if (member.getMemberId() != null && !ids.contains(member.getMemberId())) ids.add(member.getMemberId());
            }
        }
        return ids;
    }

    private List<Users> loadUsersByIdsInOrder(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        Map<Long, Users> map = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, u -> u, (a, b) -> a));
        List<Users> users = new ArrayList<>();
        for (Long id : userIds) {
            Users user = map.get(id);
            if (user != null) users.add(user);
        }
        return users;
    }

    private String resolvePersonalType(Event event, Users user, String inputType) {
        if ("LANQIAO".equalsIgnoreCase(event.getType())) {
            if (!StringUtils.hasText(inputType) || !LANQIAO_TYPES.contains(inputType)) throw new IllegalArgumentException(user.getRealName() + "：请选择有效蓝桥杯组别");
            return inputType;
        }
        if ("GPLT".equalsIgnoreCase(event.getType())) return inputType;
        if ("girl".equals(inputType)) {
            if (user.getGender() == null || user.getGender() != 2) throw new IllegalArgumentException(user.getRealName() + "：女生组要求选手为女性");
            return "girl";
        }
        if ("star".equals(inputType)) return "star";
        if (user.getGender() != null && user.getGender() == 2) return "girl";
        return "official";
    }

    private String resolveTeamType(Event event, List<Users> members, String inputType) {
        if ("GPLT".equalsIgnoreCase(event.getType())) {
            if (!StringUtils.hasText(inputType) || !GPLT_TYPES.contains(inputType)) throw new IllegalArgumentException("请选择有效天梯赛组别");
            return inputType;
        }
        String type = "star".equals(inputType) ? "star" : "official";
        if ("girl".equals(inputType) || ("official".equals(type) && isAllGirls(members))) {
            if (!isAllGirls(members)) throw new IllegalArgumentException("女生队伍要求所有成员为女性");
            return "girl";
        }
        return type;
    }

    private void validateTeamSignupMembers(Event event, List<Users> members, String teamName) {
        if (members == null || members.isEmpty()) throw new IllegalArgumentException(teamName + "：队伍成员为空");
        if (members.size() < event.getMinMember() || members.size() > event.getMaxMember()) {
            throw new IllegalArgumentException(teamName + "：队伍人数不符合要求 (" + event.getMinMember() + "-" + event.getMaxMember() + "人)");
        }
        for (Users member : members) {
            if (!"student".equals(member.getRole()) || !"normal".equals(member.getAccountStatus())) {
                throw new IllegalArgumentException(teamName + "：成员 " + member.getRealName() + " 不是正常状态学生");
            }
        }
    }

    private void requireIdPhoto(Users user) {
        if (user == null || user.getIdPhotoFileId() == null) {
            String name = user == null ? "未知学生" : user.getRealName();
            throw new IllegalArgumentException(name + "：未上传证件照");
        }
    }

    private void requireMembersIdPhoto(List<Users> members, String teamName) {
        for (Users member : members) {
            if (member == null || member.getIdPhotoFileId() == null) {
                String name = member == null ? "未知成员" : member.getRealName();
                throw new IllegalArgumentException(teamName + "：成员 " + name + " 未上传证件照");
            }
        }
    }

    private void checkTeamConflicts(Long eventId, List<Long> memberIds, String teamName) {
        String conflict = signupPolicy.memberConflict(eventId, memberIds, null, null);
        if (conflict != null) throw new IllegalArgumentException(teamName + "：" + conflict);
    }

    private boolean isAllGirls(List<Users> users) {
        if (users == null || users.isEmpty()) return false;
        for (Users user : users) {
            if (user == null || user.getGender() == null || user.getGender() != 2) return false;
        }
        return true;
    }

    private void replaceSignupTeamDetails(Long signupId, List<Long> memberIds) {
        signupTeamDetailMapper.delete(new QueryWrapper<SignupTeamDetail>().eq("signup_team_id", signupId));
        for (int i = 0; i < memberIds.size(); i++) {
            SignupTeamDetail detail = new SignupTeamDetail();
            detail.setSignupTeamId(signupId);
            detail.setUserId(memberIds.get(i));
            detail.setMemberOrder(i);
            signupTeamDetailMapper.insert(detail);
        }
    }

    private List<SignupTeamDetail> getSignupDetailsOrdered(Long signupId) {
        return signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>()
                .eq("signup_team_id", signupId)
                .orderByAsc("member_order", "detail_id"));
    }

    private List<Users> loadUsersForDetails(List<SignupTeamDetail> details) {
        return loadUsersByIdsInOrder(details.stream().map(SignupTeamDetail::getUserId).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    private boolean isSignupTeamInSchool(SignupTeam signup, Long schoolId) {
        List<Users> members = loadUsersForDetails(getSignupDetailsOrdered(signup.getSignupId()));
        return !members.isEmpty() && members.stream().allMatch(member -> isUserInUniversity(member, schoolId));
    }

    private Map<String, String> getSignupTeamEmailTargets(Long signupId) {
        Map<String, String> targets = new HashMap<>();
        for (Users user : loadUsersForDetails(getSignupDetailsOrdered(signupId))) {
            if (StringUtils.hasText(user.getEmail())) targets.put(user.getEmail(), user.getRealName());
        }
        return targets;
    }

    private void sendSignupMail(Users user, Event event, Long signupId, String status) {
        if (user == null || !StringUtils.hasText(user.getEmail())) return;
        Map<String, String> targets = new HashMap<>();
        targets.put(user.getEmail(), user.getRealName());
        signupNotifications.enqueue(event.getEventId(), "personal", signupId, targets, event.getName(), null, status, null);
    }

    private String normalizeTeamName(String teamName) {
        return teamName == null ? "" : teamName.trim();
    }

    private Page<EventSignupVO> paginate(List<EventSignupVO> all, Integer current, Integer size) {
        int page = Math.max(current == null ? 1 : current, 1);
        int pageSize = Math.max(size == null ? 10 : size, 1);
        int from = Math.min((page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        Page<EventSignupVO> result = new Page<>(page, pageSize);
        result.setTotal(all.size());
        result.setRecords(all.subList(from, to));
        return result;
    }

    private <T> void applySignupTypeFilter(QueryWrapper<T> wrapper, String teamType) {
        if (!StringUtils.hasText(teamType) || "all".equals(teamType)) return;
        if ("official".equals(teamType)) {
            wrapper.and(w -> w.in("type", "official", "girl").or().isNull("type").or().eq("type", ""));
        } else {
            wrapper.eq("type", teamType);
        }
    }

    private boolean matchesSignupKeyword(String keyword, Users user, String teamName, List<Users> members) {
        if (!StringUtils.hasText(keyword)) return true;
        String key = keyword.trim();
        if (StringUtils.hasText(teamName) && teamName.contains(key)) return true;
        if (user != null && ((StringUtils.hasText(user.getRealName()) && user.getRealName().contains(key))
                || (StringUtils.hasText(user.getNumberId()) && user.getNumberId().contains(key)))) return true;
        if (members != null) {
            for (Users member : members) {
                if ((StringUtils.hasText(member.getRealName()) && member.getRealName().contains(key))
                        || (StringUtils.hasText(member.getNumberId()) && member.getNumberId().contains(key))) return true;
            }
        }
        return false;
    }

    private EventSignupVO toPersonalSignupVO(SignupPersonal signup, Users user) {
        EventSignupVO vo = new EventSignupVO();
        vo.setSignupId(signup.getSignupId());
        vo.setEventId(signup.getEventId());
        vo.setUserId(user.getUserId());
        vo.setRealName(user.getRealName());
        vo.setGender(convertGender(user.getGender()));
        vo.setNumberId(user.getNumberId());
        vo.setClassName(user.getClassName());
        vo.setGradeYear(user.getGradeYear());
        vo.setSignupTime(signup.getSignupTime());
        vo.setStatus(signup.getStatus());
        vo.setAdvisor(signup.getAdvisor());
        vo.setIsRanking(signup.getIsRanking());
        vo.setTeamType(signup.getType());
        vo.setContestAccount(signup.getContestAccount());
        vo.setContestPassword(signup.getContestPassword());
        vo.setSeatNumber(signup.getSeatNumber());
        vo.setCreatedByUserId(signup.getCreatedByUserId());
        vo.setSignupSource(StringUtils.hasText(signup.getSignupSource()) ? signup.getSignupSource() : "self");
        fillSignupUserSchoolInfo(vo, user);
        fillCreator(vo);
        return vo;
    }

    private EventSignupVO toTeamSignupSummaryVO(SignupTeam signup, Users leader, List<Users> members) {
        EventSignupVO vo = baseTeamSignupVO(signup);
        fillSignupUserSchoolInfo(vo, leader);
        vo.setMembersContent(members.stream().map(Users::getRealName).collect(Collectors.joining(", ")));
        return vo;
    }

    private EventSignupVO toTeamSignupDetailVO(SignupTeam signup, Users member, int order) {
        EventSignupVO vo = baseTeamSignupVO(signup);
        vo.setUserId(member.getUserId());
        vo.setRealName(member.getRealName());
        vo.setGender(convertGender(member.getGender()));
        vo.setNumberId(member.getNumberId());
        vo.setClassName(member.getClassName());
        vo.setGradeYear(member.getGradeYear());
        vo.setMembersContent(order == 0 ? "队长" : "队员");
        fillSignupUserSchoolInfo(vo, member);
        return vo;
    }

    private EventSignupVO baseTeamSignupVO(SignupTeam signup) {
        EventSignupVO vo = new EventSignupVO();
        vo.setSignupId(signup.getSignupId());
        vo.setEventId(signup.getEventId());
        vo.setTeamName(signup.getTeamName());
        vo.setSignupTime(signup.getSignupTime());
        vo.setStatus(signup.getStatus());
        vo.setAdvisor(signup.getAdvisor());
        vo.setIsRanking(signup.getIsRanking());
        vo.setTeamType(signup.getType());
        vo.setContestAccount(signup.getContestAccount());
        vo.setContestPassword(signup.getContestPassword());
        vo.setSeatNumber(signup.getSeatNumber());
        vo.setCreatedByUserId(signup.getCreatedByUserId());
        vo.setSignupSource(StringUtils.hasText(signup.getSignupSource()) ? signup.getSignupSource() : "self");
        fillCreator(vo);
        return vo;
    }

    private void fillSignupUserSchoolInfo(EventSignupVO vo, Users user) {
        if (user == null || user.getMajorId() == null) return;
        Major major = majorMapper.selectById(user.getMajorId());
        if (major == null) return;
        vo.setMajorName(major.getMajorName());
        School school = schoolMapper.selectById(major.getSchoolId());
        if (school == null) return;
        vo.setSchoolName(school.getSchoolName());
        University university = universityMapper.selectById(school.getUniversityId());
        if (university != null) vo.setUniversityName(university.getUniversityName());
    }

    private void fillCreator(EventSignupVO vo) {
        if (vo.getCreatedByUserId() == null) return;
        Users creator = userMapper.selectById(vo.getCreatedByUserId());
        if (creator != null) vo.setCreatedByName(creator.getRealName());
    }

    private String convertGender(Integer gender) {
        if (gender == null) return "";
        if (gender == 1) return "男";
        if (gender == 2) return "女";
        return "未知";
    }
}
