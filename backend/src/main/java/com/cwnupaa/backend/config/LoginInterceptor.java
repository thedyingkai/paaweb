package com.cwnupaa.backend.config;

import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.controller.FileController;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private AdminPermissionsMapper adminPermissionsMapper;

    @Autowired
    private FileSystemMapper fileSystemMapper;

    @Autowired
    private FileController fileController;

    @Autowired
    private AuthStateChangeService authStateChangeService;

    @Autowired
    private com.cwnupaa.backend.service.AccountSecurityService accountSecurityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        // Spring can match an encoded path segment to a literal management
        // route (for example %61dmin to admin). Authorize using the matched
        // route, not just the undecoded request URI, whenever it is administrative.
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (matched != null && hasAdminSegment(matched.toString())) uri = matched.toString();
        String method = request.getMethod();
        HttpSession session = request.getSession(false);
        UserProfileVO user = (session != null) ? (UserProfileVO) session.getAttribute("user") : null;

        if (user != null) {
            Users dbUser = accountSecurityService.validateSession(request);
            if (dbUser == null) {
                user = null;
            } else {
                // Session 只保存登录时的快照。审核通过、角色或后台权限变更后，
                // 每次请求都从数据库同步完整用户信息，避免必须退出再登录才能生效。
                String previousRole = user.getRole();
                String previousStatus = user.getAccountStatus();
                List<String> previousPermissions = user.getPermissions() == null
                        ? new ArrayList<>() : new ArrayList<>(user.getPermissions());

                BeanUtils.copyProperties(dbUser, user, "password");
                user.setPassword(null);
                if (isAdminRole(dbUser.getRole()) || !previousPermissions.isEmpty()) {
                    user.setPermissions(loadPermissions(dbUser));
                } else {
                    user.setPermissions(new ArrayList<>());
                }
                session.setAttribute("user", user);

                // 兜底广播：任何代码路径（含运维直接改库）造成的身份状态、角色或
                // 权限变化，都会在这里被检出并推送给该用户，让前端立即重新拉取
                // /user/me，而不是继续使用旧快照直到重新登录。
                if (!java.util.Objects.equals(previousRole, user.getRole())
                        || !java.util.Objects.equals(previousStatus, user.getAccountStatus())
                        || !new java.util.HashSet<>(previousPermissions).equals(new java.util.HashSet<>(user.getPermissions()))) {
                    authStateChangeService.notifyChanged(user.getUserId());
                }
            }
        }

        if (user != null) {
            request.setAttribute("userId", user.getUserId());
            request.setAttribute("user", user);
        }

        if (user != null && "banned".equals(user.getAccountStatus())
                && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)
                && !"/user/logout".equals(uri)) {
            return sendJsonError(response, 403, "账号已被封禁，暂时无法进行此操作");
        }

        // 旧版本地文件 URL（/uploads/**）由静态资源处理器直接返回，不能绕过
        // /file/image/{fileId} 的访问控制。先按数据库记录还原文件，再复用同一权限判断。
        if (uri.startsWith("/uploads/")) {
            String storagePath = uri.substring("/uploads/".length());
            if (!StringUtils.hasText(storagePath) || storagePath.contains("..")) {
                response.setStatus(404);
                return false;
            }
            FileSystem file = fileSystemMapper.selectOne(new QueryWrapper<FileSystem>()
                    .eq("url", "/uploads/" + storagePath)
                    .or()
                    .eq("storage_key", storagePath)
                    .last("LIMIT 1"));
            if (file == null) {
                response.setStatus(404);
                return false;
            }
            if (!fileController.canAccess(file, user)) {
                return sendJsonError(response, 403, "无权限访问该文件");
            }
            return true;
        }

        // Resolver authentication is its per-board password, checked by the service.
        if ("POST".equalsIgnoreCase(method) && uri.matches("/leaderboard/[0-9]+/resolver")) return true;

        boolean isAdminPath = isAdministrativePath(uri);

        if ("GET".equalsIgnoreCase(method) && "/user/me".equals(uri)) return true;
        if ("GET".equalsIgnoreCase(method) && "/team-space/public-records".equals(uri)) return true;
        if ("GET".equalsIgnoreCase(method) && "/team-recruitment/windows".equals(uri)) return true;

        if ("GET".equalsIgnoreCase(method)) {
            if (!isAdminPath && (uri.contains("/notice") ||
                    uri.contains("/intro") ||
                    uri.contains("/user/register/config") ||
                    uri.contains("/rank") ||
                    uri.contains("/rating") ||
                    uri.contains("/leaderboard") ||
                    uri.contains("/file") ||
                    uri.contains("/user/profile") ||
                    uri.contains("/certificate/honor-wall") ||
                    uri.contains("/tracker") ||
                    // 注意：/team 不在此匿名白名单内，队伍接口（含 search-user）需登录
                    uri.contains("/association-member") ||
                    uri.contains("/oj/recent-contest"))) {
                return true;
            }

            // 业务路径匿名放行（/event /contest /training /board 等），管理子路径（含 /admin）不在此列
            if (!isAdminPath && (uri.contains("/event") ||
                    uri.contains("/contest") ||
                    uri.contains("/training") ||
                    uri.contains("/external-board") ||
                    uri.contains("/board"))) {
                return true;
            }
        }
        if (user == null) {
            // 带上可读原因，避免前端拿到空响应体后只能提示“网络错误/操作失败”。
            return sendJsonError(response, 401, "未登录或会话已失效，请重新登录后再试");
        }
        boolean needsNormalStatus = false;
        if (uri.contains("/certificate")) {
            needsNormalStatus = true;
        }

        if ((uri.contains("/signup") || uri.contains("/join")) && "POST".equalsIgnoreCase(method)) {
            needsNormalStatus = true;
        }

        if (needsNormalStatus && !"normal".equals(user.getAccountStatus())) {
            return sendJsonError(response, 403, "账号状态异常(需完成实名认证)，无法进行此操作");
        }

        if (isAdminPath) {
            String role = user.getRole();
            if (!"normal".equals(user.getAccountStatus())) {
                return sendJsonError(response, 403, "账号未完成身份认证或状态异常，无法进入控制面板");
            }

            if (!"supper_admin".equals(role) && !"super_admin".equals(role) && !"stu_admin".equals(role) && !"department".equals(role) && !"school_admin".equals(role)) {
                return sendJsonError(response, 403, "无后台访问权限");
            }

            if ("school_admin".equals(role)) {
                if (uri.startsWith("/admin/school-signup")) return true;
                return sendJsonError(response, 403, "学校报名管理员无权访问该模块");
            }

            // 实时刷新权限（每次请求读库，保证撤销即时生效）
            AdminPermissions perm = adminPermissionsMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdminPermissions>().eq("user_id", user.getUserId()));
            List<String> perms = new ArrayList<>();
            if (perm != null && StringUtils.hasText(perm.getPermissions())) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    perms = mapper.readValue(perm.getPermissions(), mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(LoginInterceptor.class).warn("操作失败，请检查请求参数和服务配置");
                }
            }

            if (uri.startsWith("/admin/dashboard") || uri.equals("/admin") || uri.equals("/admin/")) {
                return true;
            }

            if (uri.startsWith("/admin/data-panel")) {
                if (SecurityUtils.hasPermission(user, "manage_user")) return true;
                return sendJsonError(response, 403, "权限不足，无法查看用户数据");
            }

            if (isPathOrChild(uri, "/admin/qq-bot")) {
                if ("supper_admin".equals(role) || "super_admin".equals(role)) return true;
                return sendJsonError(response, 403, "仅超级管理员可管理QQ机器人");
            }

            // 内置 admin 是系统兜底管理员，应在细粒度路由判断前放行。
            // 原先该判断位于实名审核判断之后，导致 admin 在权限表尚未初始化时也收到 403。
            boolean isSuperAdmin = "supper_admin".equals(role) || "super_admin".equals(role);
            boolean isAdminUser = isSuperAdmin && "admin".equals(user.getUserAccount());
            if (isAdminUser) {
                if (!perms.contains("manage_permission")) perms.add("manage_permission");
                return true;
            }

            if (uri.contains("/user/auth-list") ||
                    uri.contains("/user/auth-history-list") ||
                    uri.contains("/user/audit-auth") ||
                    uri.contains("/user/latest-audit")) {
                // 实名审核属敏感管理操作：要求 manage_user 细粒度权限，而非仅凭角色
                if (perms.contains("manage_user")) return true;
                return sendJsonError(response, 403, "权限不足，无法操作实名审核模块");
            }

            if (uri.contains("/certificate/audit") || uri.contains("/admin/certificate/audit") || uri.contains("/admin/certificate-stats") || uri.contains("/admin/certificate/stats") || uri.contains("/admin/certificate/list") || uri.contains("/certificate/admin/")) {
                // 证书审核/统计/管理要求 manage_cert_grant 细粒度权限
                if (perms.contains("manage_cert_grant")) return true;
                return sendJsonError(response, 403, "权限不足，无法操作证书管理模块");
            }

            if (uri.startsWith("/admin/bulk-email")) {
                if ("supper_admin".equals(role) || "super_admin".equals(role)) return true;
                return sendJsonError(response, 403, "仅超级管理员可使用批量邮件发送功能");
            }
            
            if (uri.equals("/admin/org/info") || uri.startsWith("/admin/org/info/")) {
                if (isSuperAdmin) return true;
                return sendJsonError(response, 403, "仅超级管理员可管理组织标识");
            }

            // Route matching
            if (isPathOrChild(uri, "/team-recruitment/admin") && SecurityUtils.hasPermission(user, "manage_sys")) return true;
            if ((uri.contains("/admin/notice") || uri.contains("/notice/admin")) && perms.contains("manage_notice")) return true;
            if ((uri.contains("/admin/training") || uri.contains("/training/admin")) && perms.contains("manage_training")) return true;
            if (uri.contains("/admin/category") || uri.contains("/admin/contest/category")) {
                if (perms.contains("manage_category")) return true;
            }
            if ((uri.contains("/admin/contest") && !uri.contains("/admin/contest/category")) || uri.contains("/leaderboard/admin/") || uri.contains("/event/admin/")) {
                if (perms.contains("manage_contest")) return true;
            }
            if (uri.contains("/admin/user") && perms.contains("manage_user")) return true; 
            if (uri.contains("/admin/oj") && perms.contains("manage_oj")) return true;
            
            if ((uri.contains("/admin/intro") || uri.contains("/admin/system-config")) && perms.contains("manage_sys")) return true;
            if (uri.contains("/admin/app-key") && perms.contains("manage_api")) return true;
            if (uri.contains("/admin/certificate/grant") && perms.contains("manage_cert_grant")) return true;
            if (uri.contains("/admin/certificate-grant") && perms.contains("manage_cert_grant")) return true;
            if (uri.contains("/certificate/grant/") && perms.contains("manage_cert_grant")) return true;
            if (uri.contains("/admin/term") || uri.contains("/admin/org/term")) {
                if (perms.contains("manage_term")) return true;
            }
            if ((uri.contains("/admin/org") && !uri.contains("/admin/org/term")) || uri.contains("/admin/university") || uri.contains("/admin/school") || uri.contains("/admin/major")) {
                if (perms.contains("manage_org")) return true;
            }
            if (uri.contains("/admin/permission") && perms.contains("manage_permission")) return true;

            // If it's an API not explicitly listed above, but they are a super_admin, do we block them? 
            // Yes, because permissions are granular now.
            // But if they have the specific permission, we returned true.
            // If they reach here, it means they don't have the permission or it's an unmapped route.
            
            // Allow basic read-only access to some common endpoints if needed, but for now block.
            return sendJsonError(response, 403, "权限不足，无法操作此模块");
        }

        return true;
    }

    private boolean isPathOrChild(String uri, String path) {
        return uri.equals(path) || uri.startsWith(path + "/");
    }

    private boolean isAdministrativePath(String uri) {
        boolean certificateGrant = uri.startsWith("/certificate/grant/")
                && !isPathOrChild(uri, "/certificate/grant/public")
                && !isPathOrChild(uri, "/certificate/grant/my")
                && !isPathOrChild(uri, "/certificate/grant/download")
                && !isPathOrChild(uri, "/certificate/grant/preview");
        return hasAdminSegment(uri) || certificateGrant;
    }

    private boolean hasAdminSegment(String uri) {
        return uri.startsWith("/admin") || uri.contains("/admin/") || uri.endsWith("/admin");
    }

    private List<String> loadPermissions(Users dbUser) {
        List<String> permissions = new ArrayList<>();
        AdminPermissions record = adminPermissionsMapper.selectOne(
                new QueryWrapper<AdminPermissions>().eq("user_id", dbUser.getUserId()));
        if (record != null && StringUtils.hasText(record.getPermissions())) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                permissions = mapper.readValue(record.getPermissions(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                // 脏权限数据不能阻断普通用户请求，按无额外权限处理。
            }
        }
        if (("supper_admin".equals(dbUser.getRole()) || "super_admin".equals(dbUser.getRole()))
                && "admin".equals(dbUser.getUserAccount())
                && !permissions.contains("manage_permission")) {
            permissions.add("manage_permission");
        }
        return permissions;
    }

    private boolean isAdminRole(String role) {
        return "supper_admin".equals(role) || "super_admin".equals(role)
                || "stu_admin".equals(role)
                || "department".equals(role) || "school_admin".equals(role);
    }

    private boolean sendJsonError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ", \"msg\":\"" + msg + "\"}");
        return false;
    }
}
