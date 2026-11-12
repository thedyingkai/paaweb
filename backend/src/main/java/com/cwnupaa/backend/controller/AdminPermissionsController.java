package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.service.SecurityAuditService;
import com.cwnupaa.backend.service.AdminRolePermissionService;
import com.cwnupaa.backend.utils.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/permission")
public class AdminPermissionsController {
    private static final List<String> ALLOWED_PERMISSIONS = AdminRolePermissionService.ALLOWED_PERMISSIONS;

    @Autowired
    private AdminPermissionsMapper adminPermissionsMapper;
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthStateChangeService authStateChangeService;
    @Autowired private AccountSecurityService accountSecurityService;
    @Autowired private SecurityAuditService securityAuditService;
    @Autowired private AdminRolePermissionService adminRolePermissionService;

    private boolean checkIsAdminUser(HttpServletRequest request) {
        return SecurityUtils.isRoot(SecurityUtils.currentUser(request))
                && SecurityUtils.hasPermission(SecurityUtils.currentUser(request), "manage_permission");
    }

    @GetMapping("/get/{userId}")
    public Result<List<String>> getPermissions(@PathVariable Long userId, HttpServletRequest request) {
        if (!checkIsAdminUser(request)) return Result.error("只有超级管理员admin可以查看权限");
        
        AdminPermissions perm = adminPermissionsMapper.selectOne(new QueryWrapper<AdminPermissions>().eq("user_id", userId));
        List<String> permsList = new ArrayList<>();
        if (perm != null && StringUtils.hasText(perm.getPermissions())) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                permsList = mapper.readValue(perm.getPermissions(), mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AdminPermissionsController.class).warn("操作失败，请检查请求参数和服务配置");
            }
        }
        return Result.success(permsList);
    }

    @PostMapping("/set")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> setPermissions(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        if (!checkIsAdminUser(request)) return Result.error("只有超级管理员admin可以设置权限");
        
        Long userId = Long.valueOf(params.get("userId").toString());
        List<String> suppliedPerms = (List<String>) params.get("permissions");
        List<String> perms = suppliedPerms == null ? new ArrayList<>() : new ArrayList<>(suppliedPerms);
        perms.removeIf(p -> !ALLOWED_PERMISSIONS.contains(p));
        
        Users targetUser = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", userId).last("FOR UPDATE"));
        if (targetUser == null) return Result.error("用户不存在");
        if (!SecurityUtils.canManageTarget(SecurityUtils.currentUser(request), targetUser)) return Result.error(403, "无权管理该账号");
        
        // Validation: Normal admins can't have super permissions
        boolean isTargetSuper = "supper_admin".equals(targetUser.getRole()) || "super_admin".equals(targetUser.getRole());
        
        // Remove super perms if not super admin
        if (!isTargetSuper) {
            String[] superPerms = {"manage_sys", "manage_api", "manage_cert_grant", "manage_org", "manage_term", "manage_permission"};
            perms.removeIf(p -> {
                for (String sp : superPerms) {
                    if (sp.equals(p)) return true;
                }
                return false;
            });
        }
        
        if ("admin".equals(targetUser.getUserAccount())) {
            if (!perms.contains("manage_permission")) {
                perms.add("manage_permission"); // ensure admin always has it
            }
        } else {
            perms.remove("manage_permission"); // nobody else can have it
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            String permsJson = mapper.writeValueAsString(perms);
            
            AdminPermissions perm = adminPermissionsMapper.selectOne(new QueryWrapper<AdminPermissions>().eq("user_id", userId));
            if (perm == null) {
                perm = new AdminPermissions();
                perm.setUserId(userId);
                perm.setPermissions(permsJson);
                perm.setUpdateTime(LocalDateTime.now());
                adminPermissionsMapper.insert(perm);
            } else {
                perm.setPermissions(permsJson);
                perm.setUpdateTime(LocalDateTime.now());
                adminPermissionsMapper.updateById(perm);
            }
            securityAuditService.record(SecurityUtils.currentUserId(request), userId, "admin-permission-update", List.of("permissions"));
            accountSecurityService.revokeSessions(targetUser, request, userId.equals(SecurityUtils.currentUserId(request)));
            authStateChangeService.notifyChanged(userId);
            return Result.success("权限设置成功");
        } catch (Exception e) {
            throw new IllegalStateException("权限设置失败", e);
        }
    }

    @PostMapping("/set-role")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> setRole(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        if (!checkIsAdminUser(request)) return Result.error("只有超级管理员admin可以修改管理员级别");

        Long userId = Long.valueOf(params.get("userId").toString());
        String role = String.valueOf(params.get("role"));
        if ("super_admin".equals(role)) role = "supper_admin";
        if (!List.of("supper_admin", "stu_admin").contains(role)) {
            return Result.error("管理员级别不合法");
        }

        Users targetUser = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", userId).last("FOR UPDATE"));
        if (targetUser == null) return Result.error("用户不存在");
        if (!SecurityUtils.canManageTarget(SecurityUtils.currentUser(request), targetUser)) return Result.error(403, "无权管理该账号");
        if ("admin".equals(targetUser.getUserAccount()) && !"supper_admin".equals(role)) {
            return Result.error("admin 账号必须保留超级管理员");
        }

        String previousRole = targetUser.getRole();
        targetUser.setRole(role);
        userMapper.updateById(targetUser);
        adminRolePermissionService.initializeForPromotion(targetUser, previousRole);

        if (!"supper_admin".equals(role)) {
            AdminPermissions perm = adminPermissionsMapper.selectOne(new QueryWrapper<AdminPermissions>().eq("user_id", userId));
            if (perm != null && StringUtils.hasText(perm.getPermissions())) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<String> perms = mapper.readValue(perm.getPermissions(), mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    String[] superPerms = {"manage_sys", "manage_api", "manage_cert_grant", "manage_org", "manage_term", "manage_permission"};
                    perms.removeIf(p -> List.of(superPerms).contains(p));
                    perm.setPermissions(mapper.writeValueAsString(perms));
                    perm.setUpdateTime(LocalDateTime.now());
                    adminPermissionsMapper.updateById(perm);
                } catch (Exception e) {
                    throw new IllegalStateException("管理员权限清理失败", e);
                }
            }
        }

        securityAuditService.record(SecurityUtils.currentUserId(request), userId, "admin-role-update", List.of("role", "permissions"));
        accountSecurityService.revokeSessions(targetUser, request, userId.equals(SecurityUtils.currentUserId(request)));
        authStateChangeService.notifyChanged(userId);
        return Result.success("管理员级别修改成功");
    }

    @PostMapping("/cancel-admin")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelAdmin(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        if (!checkIsAdminUser(request)) return Result.error("只有超级管理员admin可以取消管理员权限");

        Object rawIds = params.get("userIds");
        if (!(rawIds instanceof List<?> rawList) || rawList.isEmpty()) {
            return Result.error("请选择需要取消管理员权限的用户");
        }

        List<Long> userIds = rawList.stream().map(item -> Long.valueOf(item.toString())).distinct().sorted().toList();
        for (Long userId : userIds) {
            Users targetUser = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", userId).last("FOR UPDATE"));
            if (targetUser == null) continue;
            if (!SecurityUtils.canManageTarget(SecurityUtils.currentUser(request), targetUser)) return Result.error(403, "无权管理该账号");
            if ("admin".equals(targetUser.getUserAccount())) {
                return Result.error("不能取消 admin 账号的超级管理员权限");
            }
        }

        for (Long userId : userIds) {
            Users targetUser = userMapper.selectOne(new QueryWrapper<Users>().eq("user_id", userId).last("FOR UPDATE"));
            if (targetUser == null) continue;
            targetUser.setRole("student");
            userMapper.updateById(targetUser);
            adminPermissionsMapper.delete(new QueryWrapper<AdminPermissions>().eq("user_id", userId));
            securityAuditService.record(SecurityUtils.currentUserId(request), userId, "admin-cancel-role", List.of("role", "permissions"));
            accountSecurityService.revokeSessions(targetUser, request, false);
            authStateChangeService.notifyChanged(userId);
        }

        return Result.success("已取消管理员权限");
    }
}
