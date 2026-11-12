package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPromotionTests {
    final UserMapper users = mock(UserMapper.class);
    final AdminPermissionsMapper permissions = mock(AdminPermissionsMapper.class);
    final AccountSecurityService sessions = mock(AccountSecurityService.class);
    final AuthStateChangeService changes = mock(AuthStateChangeService.class);
    final SecurityAuditService audit = mock(SecurityAuditService.class);
    final AdminRolePermissionService defaults = new AdminRolePermissionService(permissions);

    Users target() {
        var user = new Users(); user.setUserId(6L); user.setUserAccount("owner");
        user.setRole("student"); user.setAccountStatus("normal");
        when(users.selectOne(any())).thenReturn(user);
        return user;
    }

    MockHttpServletRequest request(String account) {
        var actor = new UserProfileVO(); actor.setUserId(1L); actor.setUserAccount(account);
        actor.setRole("super_admin"); actor.setAccountStatus("normal");
        actor.setPermissions(List.of("manage_user", "manage_user_role"));
        var request = new MockHttpServletRequest(); request.setAttribute("user", actor);
        return request;
    }

    <T> T wire(T controller) {
        ReflectionTestUtils.setField(controller, "userMapper", users);
        ReflectionTestUtils.setField(controller, "accountSecurityService", sessions);
        ReflectionTestUtils.setField(controller, "authStateChangeService", changes);
        ReflectionTestUtils.setField(controller, "securityAuditService", audit);
        ReflectionTestUtils.setField(controller, "adminRolePermissionService", defaults);
        return controller;
    }

    @Test void administratorManagementInitializesPermissionsAndRevokesOldSession() {
        var target = target(); var request = request("admin");
        var controller = wire(new AdminPermissionsController());
        ReflectionTestUtils.setField(controller, "adminPermissionsMapper", permissions);
        assertThat(controller.setRole(Map.of("userId",6L,"role","super_admin"),request).getCode()).isEqualTo(200);
        assertThat(target.getRole()).isEqualTo("supper_admin");
        verify(permissions).insert(any(AdminPermissions.class));
        verify(sessions).revokeSessions(target,request,false);
        verify(changes).notifyChanged(6L);
    }

    @Test void userManagementPromotionAlsoInitializesPermissions() {
        var target = target(); var request = request("admin");
        var controller = wire(new AdminUserController());
        var update = new Users(); update.setUserId(6L); update.setRole("supper_admin");
        assertThat(controller.updateUserInfo(update,request).getCode()).isEqualTo(200);
        verify(permissions).insert(any(AdminPermissions.class));
        verify(sessions).revokeSessions(target,request,false);
    }

    @Test void nonRootCannotPromoteThroughEitherEntryPoint() {
        var target = target(); var request = request("limited-super-admin");
        var roles = wire(new AdminPermissionsController());
        ReflectionTestUtils.setField(roles, "adminPermissionsMapper", permissions);
        assertThat(roles.setRole(Map.of("userId",6L,"role","supper_admin"),request).getCode()).isNotEqualTo(200);
        var update = new Users(); update.setUserId(6L); update.setRole("supper_admin");
        assertThat(wire(new AdminUserController()).updateUserInfo(update,request).getCode()).isEqualTo(403);
        assertThat(target.getRole()).isEqualTo("student");
        verifyNoInteractions(permissions,sessions,changes,audit);
    }
}
