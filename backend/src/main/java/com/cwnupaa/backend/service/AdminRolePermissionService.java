package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminRolePermissionService {
    public static final List<String> ALLOWED_PERMISSIONS = List.of(
            "manage_notice", "manage_training", "manage_category", "manage_contest",
            "manage_user", "manage_user_email", "manage_user_role", "manage_user_status",
            "manage_oj", "manage_sys", "manage_api", "manage_cert_grant", "manage_org",
            "manage_term", "manage_permission");
    private final AdminPermissionsMapper permissions;
    private final ObjectMapper json = new ObjectMapper();

    public AdminRolePermissionService(AdminPermissionsMapper permissions) {
        this.permissions = permissions;
    }

    /** Called within the role update transaction, with the target user row already locked. */
    @Transactional(rollbackFor = Exception.class)
    public void initializeForPromotion(Users target, String previousRole) {
        if (!isSuper(target.getRole()) || isSuper(previousRole)) return;
        AdminPermissions current = permissions.selectOne(new QueryWrapper<AdminPermissions>()
                .eq("user_id", target.getUserId()));
        try {
            if (current != null && StringUtils.hasText(current.getPermissions())) {
                List<String> configured = json.readValue(current.getPermissions(), new TypeReference<>() {});
                // Preserve a deliberately configured permission set. Re-saving an existing
                // super administrator must also never restore permissions that were revoked.
                if (configured != null && !configured.isEmpty()) return;
            }
            List<String> defaults = ALLOWED_PERMISSIONS.stream()
                    .filter(p -> "admin".equals(target.getUserAccount()) || !"manage_permission".equals(p))
                    .toList();
            boolean insert = current == null;
            if (insert) {
                current = new AdminPermissions();
                current.setUserId(target.getUserId());
            }
            current.setPermissions(json.writeValueAsString(defaults));
            current.setUpdateTime(LocalDateTime.now());
            if (insert) permissions.insert(current);
            else permissions.updateById(current);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("超级管理员初始权限设置失败", e);
        }
    }

    private static boolean isSuper(String role) {
        return "supper_admin".equals(role) || "super_admin".equals(role);
    }
}
