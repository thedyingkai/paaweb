package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.*;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.service.SecurityAuditService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.vo.UserSearchVO;
import com.cwnupaa.backend.vo.PublicUserProfileVO;
import com.cwnupaa.backend.service.CertificateGrantService;
import com.cwnupaa.backend.service.FileStorageService;
import com.cwnupaa.backend.utils.Md5Util;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.utils.TextUtils;
import com.cwnupaa.backend.vo.ExternalUserInfoVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /** users 表 varchar 列的真实长度上限缓存（列名 → 上限）。 */
    private static final Map<String, Integer> USERS_COLUMN_LIMITS = new ConcurrentHashMap<>();

    private static final Map<String, String> codeCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> codeTimeCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> codeSendTimeCache = new ConcurrentHashMap<>();
    // 验证码失败次数与短暂锁定（防爆破）：邮箱验证码连续输错 5 次后立即作废并发送风险提醒
    private static final Map<String, Integer> codeFailCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> codeLockCache = new ConcurrentHashMap<>();
    // 激活身份验证失败锁定（防枚举/接管尝试）
    private static final Map<String, Integer> activateFailCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> activateLockCache = new ConcurrentHashMap<>();
    private static final long CODE_EXPIRE_MILLIS = 5 * 60 * 1000;      // 验证码有效期 5 分钟
    private static final int CODE_MAX_FAILS = 5;                       // 连续失败次数上限
    private static final long CODE_LOCK_MILLIS = 15 * 60 * 1000;       // 失败后短暂锁定 15 分钟，重新获取验证码会重置
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    
    @Autowired
    private SysIntroMapper sysIntroMapper;
    
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private TeamMemberMapper teamMemberMapper;
    @Autowired
    private RatingCompositionMapper ratingMapper;
    @Autowired
    private EmailService emailService;
    @Autowired
    private OjUserAccountMapper ojUserAccountMapper;
    @Autowired
    private AdminPermissionsMapper adminPermissionsMapper;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private ExternalAppMapper externalAppMapper;
    @Autowired
    private CertificateGrantService certificateGrantService;
    @Autowired
    private AuthStateChangeService authStateChangeService;
    @Autowired private AccountSecurityService accountSecurityService;
    @Autowired private SecurityAuditService securityAuditService;
    @Autowired private FileAccessPolicy fileAccessPolicy;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** PostgreSQL transaction-scoped lock prevents the check-then-insert race for同校同学号. */
    private void lockStudentNumber(Long universityId, String numberId) {
        if (universityId == null || !StringUtils.hasText(numberId)) return;
        lockUniqueValue("student:" + universityId + ":" + numberId.trim());
    }

    /** 同一唯一字段值的请求按数据库事务串行化，避免多实例部署时检查与写入之间的竞态。 */
    private void lockUniqueValue(String value) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                ps -> ps.setString(1, value),
                rs -> null);
    }

    /**
     * users 表 varchar 列的真实长度上限（按列缓存，查询失败则视为不限制）。
     *
     * <p>代码里的字段上限可能与线上库的实际列宽不一致（历史 schema 漂移），
     * 一旦写超长，数据库只会抛 22001（value too long），用户看到的是一句
     * “数据保存失败”，排查成本极高。这里读一次真实列宽，把问题在写入前
     * 变成一句能指明字段的提示。</p>
     */
    private int usersColumnLimit(String column) {
        Integer cached = USERS_COLUMN_LIMITS.get(column);
        if (cached != null) {
            return cached;
        }
        int resolved = Integer.MAX_VALUE;
        try {
            List<Integer> rows = jdbcTemplate.queryForList(
                    "SELECT character_maximum_length FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'users' AND column_name = ?",
                    Integer.class, column);
            if (!rows.isEmpty() && rows.get(0) != null && rows.get(0) > 0) {
                resolved = rows.get(0);
            }
        } catch (Exception e) {
            log.warn("读取 users.{} 列宽失败，跳过长度预校验 ({})", column, e.getClass().getSimpleName());
        }
        USERS_COLUMN_LIMITS.put(column, resolved);
        return resolved;
    }

    /** 注册写入前按真实列宽校验单个字段，超长时抛出可直接展示给用户的提示。 */
    private void assertFitsUsersColumn(String column, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        int limit = usersColumnLimit(column);
        if (value.length() > limit) {
            log.warn("注册字段超长：column={} limit={} length={}", column, limit, value.length());
            throw new IllegalArgumentException(
                    label + "长度超出系统限制（最多 " + limit + " 个字符，当前 " + value.length() + " 个），请缩短后重试");
        }
    }

    @PostMapping("/login")
    public Result<UserProfileVO> login(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        if (loginDTO.getUserName() == null) return Result.error("账号不能为空");

        String input = loginDTO.getUserName().trim();
        String lowerInput = input.toLowerCase();

        Users user = findUserByLoginIdentifier(input, lowerInput);
        if (user == null) {
            if (StudentNumberValidator.isStudentNumber(userMapper, input)) {
                return Result.error("学号登录已禁用，请使用账号或邮箱登录");
            }
            return Result.error("用户不存在");
        }

        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        String encryptedInput = Md5Util.encrypt(loginDTO.getPassword());
        if (encryptedInput == null || !encryptedInput.equals(user.getPassword())) return Result.error("密码错误");

        UserProfileVO userVO = assembleUserProfileVO(user);
        accountSecurityService.startSession(request, user, userVO);

        return Result.success(userVO);
    }

    @PostMapping("/external/login")
    public Result<ExternalUserInfoVO> externalLogin(@RequestBody @Validated ExternalLoginDTO dto) {
        ExternalApp app = externalAppMapper.selectOne(
                new QueryWrapper<ExternalApp>()
                        .eq("app_key", dto.getAppKey())
                        .eq("status", 1)
        );

        if (app == null) {
            return Result.error("Unauthorized: AppKey 无效、不存在或已被禁用");
        }
        if (!Boolean.TRUE.equals(app.getExternalLoginEnabled())) {
            return Result.error(403, "API Key 未被授予外部登录权限");
        }

        Users user = null;

        if ("password".equals(dto.getLoginType())) {
            String input = dto.getAccount().trim();
            String lowerInput = input.toLowerCase();

            user = findUserByLoginIdentifier(input, lowerInput);

            if (user == null) {
                if (StudentNumberValidator.isStudentNumber(userMapper, input)) {
                    return Result.error("学号登录已禁用，请使用账号或邮箱登录");
                }
                return Result.error("用户不存在");
            }

            String encryptedInput = Md5Util.encrypt(Md5Util.md5(dto.getCredential()));
            if (!user.getPassword().equals(encryptedInput)) {
                return Result.error("密码错误");
            }

        } else if ("code".equals(dto.getLoginType())) {
            String email = dto.getAccount().trim().toLowerCase();
            String code = dto.getCredential();

            if (!verifyEmailCode(email, code, "login", null)) {
                return Result.error("验证码错误或已失效，请重新获取");
            }

            user = userMapper.selectOne(new QueryWrapper<Users>().apply("LOWER(email) = {0}", email));
            if (user == null) return Result.error("用户不存在");

        } else {
            return Result.error("不支持的登录类型(loginType必须是password或code)");
        }

        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        ExternalUserInfoVO vo = new ExternalUserInfoVO();
        vo.setUserId(user.getUserId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setRealName(user.getRealName());
        vo.setNumberId(user.getNumberId());
        vo.setEmail(user.getEmail());

        return Result.success(vo);
    }

    /**
     * 优先按当前账号/邮箱查找；历史账号迁移后，旧账号仍可作为兼容别名登录。
     */
    private Users findUserByLoginIdentifier(String input, String lowerInput) {
        QueryWrapper<Users> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_account", input)
                .or().apply("LOWER(email) = {0}", lowerInput));
        Users user = userMapper.selectOne(queryWrapper);
        return user != null ? user : userMapper.selectByLegacyUserAccount(input);
    }

    @PostMapping("/external/send-code")
    public Result<String> externalSendCode(@RequestBody @Validated ExternalSendCodeDTO dto) {
        ExternalApp app = externalAppMapper.selectOne(
                new QueryWrapper<ExternalApp>()
                        .eq("app_key", dto.getAppKey())
                        .eq("status", 1)
        );
        if (app == null) {
            return Result.error("Unauthorized: AppKey 无效、不存在或已被禁用");
        }
        if (!Boolean.TRUE.equals(app.getExternalLoginEnabled())) {
            return Result.error(403, "API Key 未被授予外部登录权限");
        }

        String email = dto.getEmail().trim().toLowerCase();

        long count = userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(email) = {0}", email));
        if (count == 0) return Result.error("该邮箱未注册");

        if (codeSendTimeCache.containsKey(email) && System.currentTimeMillis() - codeSendTimeCache.get(email) < 60000) {
            return Result.error("发送太频繁，请稍后再试");
        }

        String code = String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
        cacheEmailCode(email, code, "login", null);

        try {
            emailService.sendCodeEmail(email, code, "login");
            return Result.success("验证码已发送");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(UserController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("邮件发送失败，请联系管理员");
        }
    }

    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestParam String email,
                                   @RequestParam(defaultValue = "register") String type,
                                   HttpServletRequest request) {
        String normEmail = email.trim().toLowerCase();
        if (normEmail.length() > 100 || !normEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return Result.error("邮箱格式不正确");
        boolean publicPurpose = "register".equals(type) || "login".equals(type);
        Long subjectUserId = null;
        Users current = null;
        if (!publicPurpose) {
            if (!List.of("update", "binding", "change-password", "change-email-current", "change-email-new").contains(type)) {
                return Result.error("非法的验证码类型");
            }
            current = accountSecurityService.validateSession(request);
            if (current == null) return Result.error(401, "未登录或会话已失效");
            subjectUserId = current.getUserId();
            if (!"change-email-new".equals(type) && (current.getEmail() == null || !normEmail.equalsIgnoreCase(current.getEmail()))) {
                return Result.error("只能向当前账号绑定邮箱发送该验证码");
            }
        }
        long count = userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(email) = {0}", normEmail));
        if (("register".equals(type) || "change-email-new".equals(type)) && count > 0) return Result.error("该邮箱已被注册");
        if ("login".equals(type) && count == 0) return Result.error("该邮箱未注册");
        if (codeSendTimeCache.containsKey(normEmail) && System.currentTimeMillis() - codeSendTimeCache.get(normEmail) < 60000) {
            return Result.error("发送太频繁，请稍后再试");
        }
        String code = String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
        cacheEmailCode(normEmail, code, type, subjectUserId);
        try {
            emailService.sendCodeEmail(normEmail, code, type);
            return Result.success("验证码已发送");
        } catch (Exception e) {
            invalidateEmailCode(emailCodeKey(normEmail, type, subjectUserId));
            log.warn("验证码邮件发送失败");
            return Result.error("邮件发送失败");
        }
    }

    /**
     * 统一校验邮箱验证码：5 分钟过期、连续失败 5 次自动作废，成功即作废。
     */
    private synchronized boolean verifyEmailCode(String email, String code, String purpose, Long subjectUserId) {
        if (email == null || code == null) return false;
        String key = emailCodeKey(email, purpose, subjectUserId);
        Long lockUntil = codeLockCache.get(key);
        if (lockUntil != null) {
            if (System.currentTimeMillis() < lockUntil) return false;
            codeLockCache.remove(key);
        }

        String serverCode = codeCache.get(key);
        if (serverCode == null) return false;
        Long sendTime = codeTimeCache.get(key);
        if (sendTime == null || System.currentTimeMillis() - sendTime > CODE_EXPIRE_MILLIS) {
            invalidateEmailCode(key);
            return false;
        }
        if (serverCode.equals(code.trim())) {
            invalidateEmailCode(key);
            return true;
        }
        int fails = codeFailCache.merge(key, 1, Integer::sum);
        if (fails >= CODE_MAX_FAILS) {
            codeCache.remove(key);
            codeFailCache.remove(key);
            codeLockCache.put(key, System.currentTimeMillis() + CODE_LOCK_MILLIS);
            sendCodeRiskEmail(email.trim().toLowerCase());
        }
        return false;
    }

    private static String emailCodeKey(String email, String purpose, Long userId) {
        return purpose + ":" + (userId == null ? "public" : userId) + ":" + email.trim().toLowerCase();
    }

    private synchronized void cacheEmailCode(String email, String code, String purpose, Long subjectUserId) {
        String key = emailCodeKey(email, purpose, subjectUserId);
        codeSendTimeCache.put(email.trim().toLowerCase(), System.currentTimeMillis());
        codeCache.put(key, code);
        codeTimeCache.put(key, System.currentTimeMillis());
        codeFailCache.remove(key);
        codeLockCache.remove(key);
    }

    private void invalidateEmailCode(String key) {
        codeCache.remove(key);
        codeTimeCache.remove(key);
        codeFailCache.remove(key);
    }

    private void sendCodeRiskEmail(String email) {
        try {
            emailService.sendCodeRiskEmail(email);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(UserController.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    @PostMapping("/login-by-code")
    public Result<UserProfileVO> loginByCode(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        String email = loginDTO.getUserName() == null ? "" : loginDTO.getUserName().trim().toLowerCase();
        String code = loginDTO.getPassword();

        if (!verifyEmailCode(email, code, "login", null)) return Result.error("验证码错误或已失效，请重新获取");

        Users user = userMapper.selectOne(new QueryWrapper<Users>().apply("LOWER(email) = {0}", email));
        if (user == null) return Result.error("用户不存在");

        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        UserProfileVO userVO = assembleUserProfileVO(user);
        accountSecurityService.startSession(request, user, userVO);

        return Result.success(userVO);
    }

    @GetMapping("/register/config")
    public Result<Boolean> getRegisterConfig() {
        QueryWrapper<SysIntro> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category", "system_config");
        SysIntro config = sysIntroMapper.selectOne(queryWrapper);
        if (config == null || !StringUtils.hasText(config.getContent())) {
            return Result.success(true); // Default open
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(config.getContent());
            if (node.has("allow_register")) {
                return Result.success(node.get("allow_register").asBoolean());
            }
        } catch (JsonProcessingException e) {
            org.slf4j.LoggerFactory.getLogger(UserController.class).warn("操作失败，请检查请求参数和服务配置");
        }
        return Result.success(true);
    }

    /** 注册页面每一步的即时唯一性校验；提交接口仍会再次校验。 */
    @PostMapping("/register/validate-step")
    public Result<Void> validateRegisterStep(@RequestBody RegisterAvailabilityDTO dto) {
        int step = dto.getStep() == null ? -1 : dto.getStep();
        if (step == 0) {
            if (StringUtils.hasText(dto.getUserAccount()) && userMapper.selectCount(new QueryWrapper<Users>()
                    .apply("LOWER(user_account) = {0}", dto.getUserAccount().trim().toLowerCase())) > 0) {
                return Result.error("用户名已存在");
            }
            if (StringUtils.hasText(dto.getEmail()) && userMapper.selectCount(new QueryWrapper<Users>()
                    .apply("LOWER(email) = {0}", dto.getEmail().trim().toLowerCase())) > 0) {
                return Result.error("邮箱已被注册");
            }
            // 手机号允许被多个账号共用，不做唯一性校验
            return Result.success(null);
        }
        if (step == 1 && dto.getUniversityId() != null && StringUtils.hasText(dto.getNumberId())) {
            Long majorId = dto.getMajorId();
            // 教师/部门/校外没有学院选择，使用该学校的同名预设专业解析大学范围。
            if (majorId == null || majorId == 0) {
                University uni = universityMapper.selectById(dto.getUniversityId());
                if (uni != null) {
                    School school = schoolMapper.selectOne(new QueryWrapper<School>()
                            .eq("university_id", uni.getUniversityId()).eq("school_name", uni.getUniversityName()));
                    if (school != null) {
                        Major major = majorMapper.selectOne(new QueryWrapper<Major>()
                                .eq("school_id", school.getSchoolId()).eq("major_name", uni.getUniversityName()));
                        if (major != null) majorId = major.getMajorId();
                    }
                }
            }
            if (majorId != null && StudentNumberValidator.existsInSameUniversity(
                    userMapper, majorMapper, schoolMapper, dto.getNumberId().trim(), majorId, null)) {
                return Result.error("该学校的学号已存在，请确认后登录或更换学号");
            }
            return Result.success(null);
        }
        return Result.error("当前步骤信息不完整");
    }

    @PostMapping("/activate")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> activate(@RequestBody @Validated UserActivateDTO dto, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return Result.error("未登录或会话已失效");
        }
        UserProfileVO sessionUser = (UserProfileVO) session.getAttribute("user");
        
        Users user = lockUser(sessionUser.getUserId());
        if (user == null) return Result.error("用户不存在");
        if (accountSecurityService.validateSession(request) == null) return Result.error(401, "未登录或会话已失效");
        
        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        if (!"pending_activation".equals(user.getAccountStatus())) {
            return Result.error("该账号无需激活");
        }

        // Verify Code
        String normEmail = dto.getEmail().trim().toLowerCase();
        if (!verifyEmailCode(normEmail, dto.getVerifyCode(), "register", null)) {
            return Result.error("验证码错误或已失效");
        }

        // Check duplicates
        if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(user_account) = {0}", dto.getUserAccount().trim().toLowerCase()).ne("user_id", user.getUserId())) > 0) {
            return Result.error("用户名已存在");
        }
        if (userMapper.selectCount(new QueryWrapper<Users>().eq("email", dto.getEmail()).ne("user_id", user.getUserId())) > 0) {
            return Result.error("邮箱已被注册");
        }
        // 手机号允许被多个账号共用，不做唯一性校验

        assertFitsUsersColumn("user_account", "账号", dto.getUserAccount());

        user.setUserName(dto.getNickName());
        user.setUserAccount(dto.getUserAccount());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPassword(Md5Util.encrypt(dto.getPassword()));
        user.setAccountStatus("normal");
        
        userMapper.updateById(user);
        
        // Activation changes the initial credential: existing sessions must lose access.
        accountSecurityService.revokeSessions(user, request, false);
        // Update session
        UserProfileVO freshVO = assembleUserProfileVO(user);
        accountSecurityService.startSession(request, user, freshVO);
        
        return Result.success("激活成功");
    }

    /**
     * 激活身份验证：输入学校（大学）+ 学号 + 初始密码，确认待激活账号。
     */
    @PostMapping("/activate/verify-identity")
    public Result<Map<String, Object>> verifyActivateIdentity(@RequestBody @Validated UserActivateIdentityDTO dto, HttpServletRequest request) {
        String lockKey = "act:" + dto.getUniversityId() + ":" + dto.getNumberId().trim();
        if (isActivateLocked(lockKey)) {
            return Result.error("尝试次数过多，请15分钟后再试");
        }
        Users user = findUserBySchoolAndNumber(dto.getUniversityId(), dto.getNumberId());
        if (user == null) {
            activateFail(lockKey);
            return Result.error("未找到该学校与学号对应的账号");
        }
        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        if (!"pending_activation".equals(user.getAccountStatus())) {
            return Result.error("该账号已激活或无需激活，请直接登录");
        }
        if (!user.getPassword().equals(Md5Util.encrypt(dto.getInitialPassword()))) {
            activateFail(lockKey);
            org.slf4j.LoggerFactory.getLogger(UserController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("初始密码错误");
        }
        activateSuccess(lockKey);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("realName", user.getRealName());
        data.put("numberId", user.getNumberId());
        return Result.success(data);
    }

    private boolean isActivateLocked(String key) {
        Long lockUntil = activateLockCache.get(key);
        return lockUntil != null && System.currentTimeMillis() < lockUntil;
    }

    private void activateFail(String key) {
        int fails = activateFailCache.merge(key, 1, Integer::sum);
        if (fails >= 5) {
            activateFailCache.remove(key);
            activateLockCache.put(key, System.currentTimeMillis() + 15 * 60 * 1000);
        }
    }

    private void activateSuccess(String key) {
        activateFailCache.remove(key);
        activateLockCache.remove(key);
    }

    /**
     * 通过“学校（大学）+ 学号 + 初始密码”激活账号，并补充注册流程中的缺失信息（昵称、登录账号、邮箱、手机号、性别、新密码）。
     */
    @PostMapping("/activate-by-identity")
    @Transactional(rollbackFor = Exception.class)
    public Result<UserProfileVO> activateByIdentity(@RequestBody @Validated UserActivateByIdentityDTO dto, HttpServletRequest request) {
        String lockKey = "act:" + dto.getUniversityId() + ":" + dto.getNumberId().trim();
        if (isActivateLocked(lockKey)) {
            return Result.error("尝试次数过多，请15分钟后再试");
        }
        Users user = findUserBySchoolAndNumber(dto.getUniversityId(), dto.getNumberId(), true);
        if (user == null) {
            activateFail(lockKey);
            return Result.error("未找到该学校与学号对应的账号");
        }
        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        if (!"pending_activation".equals(user.getAccountStatus())) {
            return Result.error("该账号已激活或无需激活，请直接登录");
        }
        if (!user.getPassword().equals(Md5Util.encrypt(dto.getInitialPassword()))) {
            activateFail(lockKey);
            org.slf4j.LoggerFactory.getLogger(UserController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("初始密码错误");
        }

        // 邮箱验证码校验
        String normEmail = dto.getEmail().trim().toLowerCase();
        if (!verifyEmailCode(normEmail, dto.getVerifyCode(), "register", null)) {
            return Result.error("验证码错误或已失效");
        }

        // 重复性校验
        if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(user_account) = {0}", dto.getUserAccount().trim().toLowerCase()).ne("user_id", user.getUserId())) > 0) {
            return Result.error("用户名已存在");
        }
        if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(email) = {0}", normEmail).ne("user_id", user.getUserId())) > 0) {
            return Result.error("邮箱已被注册");
        }
        // 手机号允许被多个账号共用，不做唯一性校验

        assertFitsUsersColumn("user_account", "账号", dto.getUserAccount());

        // 补充注册流程中的缺失信息
        user.setUserName(dto.getNickName());
        user.setUserAccount(dto.getUserAccount());
        user.setEmail(normEmail);
        user.setPhone(dto.getPhone());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        user.setPassword(Md5Util.encrypt(dto.getPassword()));
        user.setAccountStatus("normal");
        userMapper.updateById(user);


        accountSecurityService.revokeSessions(user, request, false);
        // 自动登录
        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        UserProfileVO userVO = assembleUserProfileVO(user);
        accountSecurityService.startSession(request, user, userVO);

        activateSuccess(lockKey);
        return Result.success(userVO);
    }

    /**
     * 按“学校（大学）ID + 学号”查找用户。
     */
    private Users findUserBySchoolAndNumber(Long universityId, String numberId) {
        return findUserBySchoolAndNumber(universityId, numberId, false);
    }

    private Users findUserBySchoolAndNumber(Long universityId, String numberId, boolean forUpdate) {
        if (universityId == null || !StringUtils.hasText(numberId)) return null;
        return userMapper.selectOne(new QueryWrapper<Users>()
                .eq("number_id", numberId.trim())
                .inSql("major_id",
                        "SELECT m.major_id FROM major m JOIN school s ON m.school_id = s.school_id WHERE s.university_id = " + universityId)
                .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"));
    }

    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    public Result<UserProfileVO> register(@RequestBody @Valid RegisterDTO dto, HttpServletRequest request) {
        // Check Config
        Result<Boolean> configRes = getRegisterConfig();
        if (configRes.getData() != null && !configRes.getData()) {
            return Result.error("系统已关闭注册功能");
        }
        
        // 所有文本字段统一去掉前后空格（Jackson 反序列化已 trim 一次，这里再兜一层，
        // 覆盖非 JSON 入口/内部调用；密码是 md5 十六进制串，不参与处理）
        dto.setUserAccount(TextUtils.trim(dto.getUserAccount()));
        dto.setUserName(TextUtils.trim(dto.getUserName()));
        dto.setEmail(TextUtils.trim(dto.getEmail()));
        dto.setPhone(TextUtils.trim(dto.getPhone()));
        dto.setEduBg(TextUtils.trim(dto.getEduBg()));
        dto.setRealName(TextUtils.trim(dto.getRealName()));
        dto.setNumberId(TextUtils.trim(dto.getNumberId()));
        dto.setClassName(TextUtils.trim(dto.getClassName()));
        dto.setGradeYear(TextUtils.trim(dto.getGradeYear()));
        dto.setVerifyCode(TextUtils.trim(dto.getVerifyCode()));

        String normEmail = dto.getEmail().toLowerCase();
        dto.setEmail(normEmail);

        lockUniqueValue("account:" + dto.getUserAccount().toLowerCase());
        lockUniqueValue("email:" + normEmail);

        if (!verifyEmailCode(normEmail, dto.getVerifyCode(), "register", null)) return Result.error("验证码错误或已失效");

        if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(user_account) = {0}", dto.getUserAccount().trim().toLowerCase())) > 0)
            return Result.error("账号已存在");
        if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(email) = {0}", normEmail)) > 0)
            return Result.error("邮箱已被注册");
        // 手机号允许被多个账号共用，不做唯一性校验

        // 写入前按 users 表的真实列宽校验，避免超长值让数据库抛 22001 而只回一句“数据保存失败”
        assertFitsUsersColumn("user_account", "账号", dto.getUserAccount());
        assertFitsUsersColumn("user_name", "昵称", dto.getUserName());
        assertFitsUsersColumn("email", "邮箱", dto.getEmail());
        assertFitsUsersColumn("phone", "手机号", dto.getPhone());
        assertFitsUsersColumn("edu_bg", "身份类型", dto.getEduBg());
        assertFitsUsersColumn("real_name", "真实姓名", dto.getRealName());
        assertFitsUsersColumn("number_id", "学号/工号", dto.getNumberId());
        assertFitsUsersColumn("class_name", "班级", dto.getClassName());
        assertFitsUsersColumn("grade_year", "年级/年份", dto.getGradeYear());

        Users user = new Users();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(Md5Util.encrypt(dto.getPassword()));

        if (!List.of("undergraduate", "postgraduate", "department", "off-campus").contains(dto.getEduBg())) return Result.error("身份类型不合法");
        if ("department".equals(dto.getEduBg())) {
            user.setRole("department");
        } else {
            user.setRole("student");
        }

        boolean isCollegeStudent = "undergraduate".equals(dto.getEduBg()) || "postgraduate".equals(dto.getEduBg());

        if (!isCollegeStudent) {
            if (dto.getUniversityId() == null) {
                return Result.error("注册信息不全：请选择所属学校/单位");
            }

            University uni = universityMapper.selectById(dto.getUniversityId());
            if (uni == null) return Result.error("所选学校不存在");
            String uniName = uni.getUniversityName();

            School school = schoolMapper.selectOne(new QueryWrapper<School>().eq("university_id", uni.getUniversityId()).eq("school_name", uniName));
            if (school == null) {
                school = new School();
                school.setUniversityId(uni.getUniversityId());
                school.setSchoolName(uniName);
                schoolMapper.insert(school);
            }

            Major major = majorMapper.selectOne(new QueryWrapper<Major>().eq("school_id", school.getSchoolId()).eq("major_name", uniName));
            if (major == null) {
                major = new Major();
                major.setSchoolId(school.getSchoolId());
                major.setMajorName(uniName);
                major.setSchoolingYears(0);
                majorMapper.insert(major);
            }

            user.setMajorId(major.getMajorId());
        } else {
            if (dto.getMajorId() == null || dto.getMajorId() == 0) {
                return Result.error("注册信息不全：请选择专业");
            }
        }

        user.setIsEnrolledStu(isCollegeStudent ? 1 : 0);
        user.setAccountStatus("register");
        user.setRegisterTime(LocalDateTime.now());

        // 同一学校（大学）内学号不能重复，不同学校（大学）之间的学号可以重复。
        // 若已存在同校同号账号，则提示已有账号，避免重复注册。
        Long registrationUniversityId = StudentNumberValidator.resolveUniversityId(majorMapper, schoolMapper, user.getMajorId());
        lockStudentNumber(registrationUniversityId, user.getNumberId());
        Users sameSchoolUser = StudentNumberValidator.findSameUniversityUser(userMapper, majorMapper, schoolMapper,
                user.getNumberId(), user.getMajorId(), null);
        if (sameSchoolUser != null) {
            if ("pending_activation".equals(sameSchoolUser.getAccountStatus())) {
                return Result.error("已有账号，请激活");
            }
            return Result.error("已有账号，请登录");
        }

        userMapper.insert(user);
        codeCache.remove(normEmail);


        if (!SecurityUtils.isAllowedRole(user.getRole())) return Result.error("账号角色已停用，请联系管理员");
        UserProfileVO userVO = assembleUserProfileVO(user);
        accountSecurityService.startSession(request, user, userVO);

        return Result.success(userVO);
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Result.success("退出成功");
    }

    @GetMapping("/me")
    public Result<UserProfileVO> getCurrentUser(HttpServletRequest request) {
        Users current = accountSecurityService.validateSession(request);
        if (current == null) return Result.success(null);
        UserProfileVO fresh = assembleUserProfileVO(current);
        request.getSession(false).setAttribute("user", fresh);
        return Result.success(fresh);
    }

    @PostMapping("/submit-auth")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> submitAuth(@RequestParam Long fileId, HttpServletRequest request) {
        if (fileId == null || fileId <= 0) return Result.error("认证照片无效，请重新上传");
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        Users user = lockUser(currentUserId);
        if (user == null) return Result.error("用户不存在");
        if (accountSecurityService.validateSession(request) == null) return Result.error(401, "未登录或会话已失效");
        if ("normal".equals(user.getAccountStatus())) {
            return Result.error("身份认证已通过，不可重新提交；如需更改请联系协会负责人");
        }
        if (!"restricted".equals(user.getAccountStatus()) && !"register".equals(user.getAccountStatus())) {
            return Result.error("当前账号状态不允许提交认证申请");
        }
        String fileError = fileAccessPolicy.validatePrivateFile(fileId, SecurityUtils.currentUser(request), true);
        if (fileError != null) return Result.error(fileError);

        if (userMapper.submitAuth(currentUserId, fileId) != 1) {
            throw new IllegalStateException("认证申请保存失败");
        }
        user.setStudentCardFileId(fileId);
        user.setAccountStatus("pending");
        refreshIdentitySessionAfterCommit(user, request);
        authStateChangeService.notifyChanged(currentUserId);

        return Result.success("认证提交成功，请等待审核");
    }

    /**
     * 待审核期间允许撤销认证申请，恢复到可修改身份资料并重新提交的状态。
     * 审核通过后身份信息锁定，不允许通过此接口撤销。
     */
    @PostMapping("/revoke-auth")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> revokeAuth(HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");

        Users user = lockUser(currentUserId);
        if (user == null) return Result.error("用户不存在");
        if (accountSecurityService.validateSession(request) == null) return Result.error(401, "未登录或会话已失效");
        if (!"pending".equals(user.getAccountStatus())) {
            if ("normal".equals(user.getAccountStatus())) {
                return Result.error("身份认证已通过，不可撤销；如需更改请联系协会负责人");
            }
            return Result.error("当前账号没有待审核的认证申请");
        }

        if (userMapper.revokeAuth(currentUserId) != 1) {
            throw new IllegalStateException("认证申请撤销失败");
        }
        user.setAccountStatus("restricted");
        refreshIdentitySessionAfterCommit(user, request);
        authStateChangeService.notifyChanged(currentUserId);

        return Result.success("认证申请已撤销，请重新上传认证材料");
    }

    private void refreshIdentitySessionAfterCommit(Users user, HttpServletRequest request) {
        // Identity submission must not depend on school, rating or OJ profile queries.
        // Keep display fields from the session and publish database changes only after commit.
        UserProfileVO profile = new UserProfileVO();
        BeanUtils.copyProperties(SecurityUtils.currentUser(request), profile);
        BeanUtils.copyProperties(user, profile);
        profile.setPassword(null);
        profile.setToken(null);
        if (user.getStudentCardFileId() != null) {
            profile.setStudentCardUrl("/api/file/image/" + user.getStudentCardFileId());
        }
        HttpSession session = request.getSession(false);
        Runnable refresh = () -> {
            if (session != null) {
                try { session.setAttribute("user", profile); }
                catch (IllegalStateException e) { log.debug("认证状态已保存，当前会话已结束"); }
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { refresh.run(); }
            });
        } else {
            refresh.run();
        }
    }

    @PostMapping("/id-photo")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateIdPhoto(@RequestParam Long fileId, HttpServletRequest request) {
        if (fileId == null || fileId <= 0) return Result.error("证件照文件无效，请重新上传");
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        Users user = lockUser(currentUserId);
        if (user == null) return Result.error("用户不存在");
        if (accountSecurityService.validateSession(request) == null) return Result.error(401, "未登录或会话已失效");

        String fileError = fileAccessPolicy.validatePrivateFile(fileId, SecurityUtils.currentUser(request), true);
        if (fileError != null) return Result.error(fileError);

        Long oldId = user.getIdPhotoFileId();
        LocalDateTime updatedAt = LocalDateTime.now();
        if (userMapper.updateIdPhoto(currentUserId, fileId, updatedAt) != 1) {
            throw new IllegalStateException("证件照保存失败");
        }

        // Updating a photo does not require rebuilding the profile from unrelated
        // school, rating and OJ tables. Publish the new snapshot only after commit.
        UserProfileVO profile = new UserProfileVO();
        BeanUtils.copyProperties(SecurityUtils.currentUser(request), profile);
        profile.setIdPhotoFileId(fileId);
        profile.setIdPhotoUpdatedAt(updatedAt);
        profile.setIdPhotoUrl("/api/user/id-photo/" + currentUserId);
        HttpSession session = request.getSession(false);
        Runnable refreshSession = () -> {
            if (session != null) {
                try { session.setAttribute("user", profile); }
                catch (IllegalStateException e) { log.debug("证件照已保存，当前会话已结束"); }
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { refreshSession.run(); }
            });
        } else {
            refreshSession.run();
        }
        if (oldId != null && !oldId.equals(fileId)) {
            fileAccessPolicy.deleteIfUnreferencedAfterCommit(oldId, SecurityUtils.currentUser(request));
        }
        return Result.success("证件照上传成功");
    }

    @GetMapping("/id-photo/{userId}")
    public ResponseEntity<?> getIdPhoto(@PathVariable Long userId, HttpServletRequest request) throws IOException {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.TEXT_PLAIN).body("未登录或会话已失效");
        }
        Users target = userMapper.selectById(userId);
        if (target == null || target.getIdPhotoFileId() == null) return ResponseEntity.notFound().build();
        UserProfileVO currentUser = SecurityUtils.currentUser(request);
        if (!canViewIdPhoto(currentUserId, target, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("无权限访问该证件照");
        }
        FileSystem fs = fileSystemMapper.selectById(target.getIdPhotoFileId());
        if (fs == null) return ResponseEntity.notFound().build();

        byte[] bytes;
        try { bytes = fileStorageService.readBytes(fs); }
        catch (Exception e) { return ResponseEntity.internalServerError().body("证件照读取失败"); }
        if (bytes == null) return ResponseEntity.notFound().build();

        String type = fs.getType() == null ? "" : fs.getType().toLowerCase();
        String mimeType = "png".equals(type) ? "image/png" : "image/jpeg";
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"id-photo\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(mimeType))
                .body(resource);
    }

    @GetMapping("/profile/{userId}")
    public Result<?> getProfile(@PathVariable Long userId, HttpServletRequest request) {
        Users user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        Users current = accountSecurityService.validateSession(request);
        UserProfileVO full = assembleUserProfileVO(user);
        if (current != null && current.getUserId().equals(userId)) return Result.success(full);
        PublicUserProfileVO publicProfile = new PublicUserProfileVO();
        BeanUtils.copyProperties(full, publicProfile);
        return Result.success(publicProfile);
    }

    private UserProfileVO assembleUserProfileVO(Users user) {
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(user, vo);
        vo.setPassword(null);

        if (user.getAvatarFileId() != null) {
            FileSystem fs = fileSystemMapper.selectById(user.getAvatarFileId());
            if (fs != null) vo.setAvatarUrl("/api" + fs.getUrl());
        }
        if (user.getBackgroundFileId() != null) {
            FileSystem fs = fileSystemMapper.selectById(user.getBackgroundFileId());
            if (fs != null) vo.setBackgroundUrl("/api" + fs.getUrl());
        }
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

        OjUserAccount oj = ojUserAccountMapper.selectById(user.getUserId());
        if (oj != null) {
            vo.setNewcoderId(oj.getNewcoderId());
            vo.setLuoguId(oj.getLuoguId());
            vo.setLuoguClientId(oj.getLuoguClientId());
            vo.setCodeforcesId(oj.getCodeforcesId());
            vo.setAtcoderId(oj.getAtcoderId());
            vo.setCwnuojId(oj.getCwnuojId());
        }

        RatingComposition rating = ratingMapper.selectById(user.getUserId());
        if (rating != null) {
            vo.setRating(rating.getRatingAll());
            if (user.getIsEnrolledStu() != null && user.getIsEnrolledStu() == 1) {
                vo.setRank(ratingMapper.getRankForEnrolled(rating.getRatingAll()));
            } else {
                vo.setRank(0);
            }
        } else {
            vo.setRating(0);
            vo.setRank(0);
        }

        AdminPermissions perm = adminPermissionsMapper.selectOne(new QueryWrapper<AdminPermissions>().eq("user_id", user.getUserId()));
        List<String> permsList = new ArrayList<>();
        if (perm != null && StringUtils.hasText(perm.getPermissions())) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                permsList = mapper.readValue(perm.getPermissions(), mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(UserController.class).warn("操作失败，请检查请求参数和服务配置");
            }
        }
        
        // Handle default permissions based on role
        if ("supper_admin".equals(user.getRole())) {
            if ("admin".equals(user.getUserAccount())) {
                if (!permsList.contains("manage_permission")) permsList.add("manage_permission");
            }
        }
        
        vo.setPermissions(permsList);

        vo.setToken(null);
        return vo;
    }

    @PostMapping("/update")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> update(@RequestBody @Validated UserUpdateDTO userDto, HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        // 资料修改的目标只能来自当前 session，忽略请求体中可能被篡改的 userId。
        userDto.setUserId(currentUserId);

        Users oldUser = lockUser(currentUserId);
        if (oldUser == null) return Result.error("用户不存在");
        if (accountSecurityService.validateSession(request) == null) return Result.error(401, "未登录或会话已失效");

        String currentEmail = oldUser.getEmail();
        boolean passwordVerified = StringUtils.hasText(userDto.getOldPassword())
                && passwordMatches(oldUser, userDto.getOldPassword());
        if (StringUtils.hasText(userDto.getOldPassword()) && !passwordVerified) return Result.error("当前密码错误");
        boolean emailChanged = StringUtils.hasText(userDto.getEmail()) && !userDto.getEmail().trim().equalsIgnoreCase(currentEmail);
        boolean passwordChanged = StringUtils.hasText(userDto.getPassword());
        OjUserAccount pendingOjUpdate = null;

        String requestedAccount = userDto.getUserAccount();
        boolean accountChanged = StringUtils.hasText(requestedAccount)
                && !requestedAccount.trim().equals(oldUser.getUserAccount());
        if (accountChanged) {
            if ("admin".equals(oldUser.getUserAccount())) return Result.error("内置管理员账号不可修改");
            lockUniqueValue("account:" + requestedAccount.trim().toLowerCase());
            if (!StringUtils.hasText(currentEmail)) return Result.error("当前账号未绑定邮箱，无法修改用户名");
            if (!verifyEmailCode(currentEmail, userDto.getVerifyCode(), "update", currentUserId)) return Result.error("邮箱验证码错误或已失效");
            String normalizedAccount = requestedAccount.trim();
            if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(user_account) = {0}", normalizedAccount.toLowerCase()).ne("user_id", currentUserId)) > 0) return Result.error("用户名已存在");
            assertFitsUsersColumn("user_account", "账号", normalizedAccount);
            oldUser.setUserAccount(normalizedAccount);
        }
        if (emailChanged) {
            if (!passwordVerified && !verifyEmailCode(currentEmail, userDto.getOldVerifyCode(), "change-email-current", currentUserId)) {
                return Result.error("修改邮箱需要验证当前密码或旧邮箱验证码");
            }
            String normNewEmail = userDto.getEmail().trim().toLowerCase();
            lockUniqueValue("email:" + normNewEmail);
            if (!verifyEmailCode(normNewEmail, userDto.getNewEmailVerifyCode(), "change-email-new", currentUserId)) return Result.error("新邮箱验证码错误或已失效");
            if (userMapper.selectCount(new QueryWrapper<Users>().apply("LOWER(email) = {0}", normNewEmail).ne("user_id", currentUserId)) > 0) return Result.error("邮箱已被注册");
            oldUser.setEmail(normNewEmail);
        }
        if (passwordChanged) {
            if (!passwordVerified && !verifyEmailCode(currentEmail, userDto.getVerifyCode(), "change-password", currentUserId)) {
                return Result.error("修改密码需要验证当前密码或当前邮箱验证码");
            }
            oldUser.setPassword(Md5Util.encrypt(userDto.getPassword()));
        }

        boolean isBindingUpdate = userDto.getNewcoderId() != null
                || userDto.getLuoguId() != null
                || userDto.getCodeforcesId() != null
                || userDto.getAtcoderId() != null
                || userDto.getCwnuojId() != null
                || StringUtils.hasText(userDto.getLuoguClientId());

        if (isBindingUpdate) {
            if (StringUtils.hasText(userDto.getNewcoderId()) && !userDto.getNewcoderId().matches("\\d+"))
                return Result.error("牛客账号请填写竞赛个人主页 /profile/ 后的数字 UID，不是昵称或学号");
            if (StringUtils.hasText(userDto.getLuoguId()) && !userDto.getLuoguId().matches("\\d+"))
                return Result.error("洛谷账号请填写个人主页 /user/ 后的数字 UID，不是昵称");
            if (!verifyEmailCode(currentEmail, userDto.getVerifyCode(), "binding", currentUserId)) return Result.error("绑定账号需要验证当前邮箱");

            OjUserAccount oj = ojUserAccountMapper.selectById(userDto.getUserId());
            if (oj == null) {
                oj = new OjUserAccount();
                oj.setUserId(userDto.getUserId());
            }

            if (userDto.getNewcoderId() != null) {
                if (StringUtils.hasText(oj.getNewcoderId()) && !oj.getNewcoderId().equals(userDto.getNewcoderId()))
                    return Result.error("牛客账号已绑定，不可修改");
                oj.setNewcoderId(userDto.getNewcoderId());
            }
            if (userDto.getLuoguId() != null) {
                if (StringUtils.hasText(oj.getLuoguId()) && !oj.getLuoguId().equals(userDto.getLuoguId()))
                    return Result.error("洛谷账号已绑定，不可修改");
                oj.setLuoguId(userDto.getLuoguId());
            }
            if (userDto.getCodeforcesId() != null) {
                if (StringUtils.hasText(oj.getCodeforcesId()) && !oj.getCodeforcesId().equals(userDto.getCodeforcesId()))
                    return Result.error("Codeforces账号已绑定，不可修改");
                oj.setCodeforcesId(userDto.getCodeforcesId());
            }
            if (userDto.getAtcoderId() != null) {
                if (StringUtils.hasText(oj.getAtcoderId()) && !oj.getAtcoderId().equals(userDto.getAtcoderId()))
                    return Result.error("AtCoder账号已绑定，不可修改");
                oj.setAtcoderId(userDto.getAtcoderId());
            }

            if (userDto.getCwnuojId() != null) {
                if (StringUtils.hasText(oj.getCwnuojId()) && !oj.getCwnuojId().equals(userDto.getCwnuojId()))
                    return Result.error("校内OJ账号已绑定，不可修改");
                oj.setCwnuojId(userDto.getCwnuojId());
            }

            if (StringUtils.hasText(userDto.getLuoguClientId())) {
                oj.setLuoguClientId(userDto.getLuoguClientId().trim());
            }

            pendingOjUpdate = oj;
        }

        boolean isIdentityChange = userDto.getRealName() != null || userDto.getNumberId() != null || userDto.getEduBg() != null || userDto.getGradeYear() != null || userDto.getClassName() != null || userDto.getPhone() != null || userDto.getGender() != null || userDto.getIsEnrolledStu() != null || userDto.getMajorId() != null;

        if (isIdentityChange) {
            String status = oldUser.getAccountStatus();
            if ("normal".equals(status) || "pending".equals(status) || "banned".equals(status)) {
                return Result.error("身份已认证、审核中或账号封禁状态，不可修改身份信息");
            }

            if (userDto.getRealName() != null) oldUser.setRealName(userDto.getRealName());
            if (userDto.getNumberId() != null || userDto.getMajorId() != null) {
                Long effectiveMajorId = userDto.getMajorId() != null ? userDto.getMajorId() : oldUser.getMajorId();
                String effectiveNumberId = userDto.getNumberId() != null ? userDto.getNumberId().trim() : oldUser.getNumberId();
                lockStudentNumber(StudentNumberValidator.resolveUniversityId(majorMapper, schoolMapper, effectiveMajorId), effectiveNumberId);
                if (StudentNumberValidator.existsInSameUniversity(userMapper, majorMapper, schoolMapper,
                        effectiveNumberId, effectiveMajorId, oldUser.getUserId())) {
                    return Result.error("该学号在本校已存在");
                }
                if (userDto.getNumberId() != null) oldUser.setNumberId(effectiveNumberId);
                if (userDto.getMajorId() != null) oldUser.setMajorId(userDto.getMajorId());
            }
            if (userDto.getEduBg() != null) {
                if (!List.of("undergraduate", "postgraduate", "department", "off-campus").contains(userDto.getEduBg())) return Result.error("身份类型不合法");
                oldUser.setEduBg(userDto.getEduBg());
            }
            if (userDto.getGender() != null) oldUser.setGender(userDto.getGender());
            if (userDto.getPhone() != null) oldUser.setPhone(userDto.getPhone());
            if (userDto.getClassName() != null) oldUser.setClassName(userDto.getClassName());
            if (userDto.getIsEnrolledStu() != null) oldUser.setIsEnrolledStu(userDto.getIsEnrolledStu());

            if (userDto.getGradeYear() != null) {
                String yearStr = String.valueOf(userDto.getGradeYear());
                if (!yearStr.matches("^\\d{4}$")) {
                    return Result.error("年级必须是4位年份，如2024");
                }
                oldUser.setGradeYear(userDto.getGradeYear());
            }
        }

        if (userDto.getUserName() != null) {
            oldUser.setUserName(userDto.getUserName());
        }
        if (userDto.getIntroduce() != null) {
            if (userDto.getIntroduce().length() > 80) {
                return Result.error("个人简介不能超过80字");
            }
            oldUser.setIntroduce(userDto.getIntroduce());
        }

        Long oldAvatarId = oldUser.getAvatarFileId();
        boolean needDeleteAvatar = false;
        if (userDto.getAvatarFileId() != null && !userDto.getAvatarFileId().equals(oldAvatarId)) {
            String fileError = fileAccessPolicy.validatePublicFile(userDto.getAvatarFileId(), SecurityUtils.currentUser(request), true);
            if (fileError != null) return Result.error(fileError);
            oldUser.setAvatarFileId(userDto.getAvatarFileId());
            needDeleteAvatar = (oldAvatarId != null);
        }

        Long oldBgId = oldUser.getBackgroundFileId();
        boolean needDeleteBg = false;
        if (userDto.getBackgroundFileId() != null && !userDto.getBackgroundFileId().equals(oldBgId)) {
            String fileError = fileAccessPolicy.validatePublicFile(userDto.getBackgroundFileId(), SecurityUtils.currentUser(request), true);
            if (fileError != null) return Result.error(fileError);
            oldUser.setBackgroundFileId(userDto.getBackgroundFileId());
            needDeleteBg = (oldBgId != null);
        }

        userMapper.updateById(oldUser);
        if (pendingOjUpdate != null) {
            if (ojUserAccountMapper.exists(new QueryWrapper<OjUserAccount>().eq("user_id", currentUserId))) ojUserAccountMapper.updateById(pendingOjUpdate);
            else ojUserAccountMapper.insert(pendingOjUpdate);
        }
        List<String> securityFields = new ArrayList<>();
        if (emailChanged) securityFields.add("email");
        if (passwordChanged) securityFields.add("password");
        if (accountChanged) securityFields.add("userAccount");
        if (!securityFields.isEmpty()) {
            accountSecurityService.revokeSessions(oldUser, request, true);
            securityAuditService.record(currentUserId, currentUserId, "self-account-security-change", securityFields);
            notifySecurityChange(currentEmail, securityFields);
            request.getSession(false).setAttribute("user", assembleUserProfileVO(oldUser));
        }
        if (needDeleteAvatar) fileAccessPolicy.deleteIfUnreferenced(oldAvatarId, SecurityUtils.currentUser(request));
        if (needDeleteBg) fileAccessPolicy.deleteIfUnreferenced(oldBgId, SecurityUtils.currentUser(request));

        return Result.success("更新成功");
    }

    @PostMapping("/update-bg")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateBg(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long newFileId = params.get("backgroundFileId");
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");

        Users user = lockUser(currentUserId);
        if (user == null) return Result.error("用户不存在");
        if (accountSecurityService.validateSession(request) == null) return Result.error(401, "未登录或会话已失效");
        String fileError = fileAccessPolicy.validatePublicFile(newFileId, SecurityUtils.currentUser(request), true);
        if (fileError != null) return Result.error(fileError);

        Long oldBgId = user.getBackgroundFileId();

        user.setBackgroundFileId(newFileId);
        userMapper.updateById(user);

        if (oldBgId != null && !oldBgId.equals(newFileId)) {
            fileAccessPolicy.deleteIfUnreferenced(oldBgId, SecurityUtils.currentUser(request));
        }

        return Result.success("更新成功");
    }

    private Users lockUser(Long userId) {
        return userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", userId).last("FOR UPDATE"));
    }

    private static boolean passwordMatches(Users user, String currentPassword) {
        String encrypted = Md5Util.encrypt(currentPassword);
        return encrypted != null && user.getPassword() != null && java.security.MessageDigest.isEqual(
                encrypted.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                user.getPassword().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private void notifySecurityChange(String previousEmail, List<String> fields) {
        if (!StringUtils.hasText(previousEmail)) return;
        Runnable send = () -> {
            try { emailService.sendAccountSecurityChangedEmail(previousEmail, fields); }
            catch (Exception e) { log.warn("账号安全变更通知发送失败"); }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }

    private boolean canViewIdPhoto(Long currentUserId, Users target, UserProfileVO currentUser) {
        if (currentUserId.equals(target.getUserId())) return true;
        if (SecurityUtils.hasPermission(currentUser, "manage_user") && (SecurityUtils.isRoot(currentUser) || SecurityUtils.roleRank(currentUser.getRole()) > SecurityUtils.roleRank(target.getRole()))) return true;
        return teamMapper.selectList(new QueryWrapper<Team>().eq("leader_id", currentUserId)).stream()
                .anyMatch(team -> team.getLeaderId().equals(target.getUserId())
                        || teamMemberMapper.selectCount(new QueryWrapper<TeamMember>()
                        .eq("team_id", team.getTeamId())
                        .eq("member_id", target.getUserId())
                        .eq("status", "1")) > 0);
    }

    @GetMapping("/search")
    public Result<List<UserSearchVO>> search(@RequestParam String keyword) {
        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 2 || keyword.length() > 100) return Result.success(List.of());
        String key = keyword.trim();
        List<Users> users = userMapper.selectList(new QueryWrapper<Users>()
                .eq("account_status", "normal")
                .like("user_name", key)
                .last("LIMIT 20"));
        return Result.success(users.stream().map(SecurityUtils::publicSearchUser).collect(Collectors.toList()));
    }
}
