package com.cwnupaa.backend.config;

import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.service.AdminRolePermissionService;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPermissionRefreshTests {
    final LoginInterceptor interceptor = new LoginInterceptor();
    final Users user = new Users();
    final AdminPermissions grant = new AdminPermissions();
    final MockHttpSession session = new MockHttpSession();

    AdminPermissionRefreshTests() {
        user.setUserId(6L); user.setUserAccount("owner");
        user.setRole("supper_admin"); user.setAccountStatus("normal");
        grant.setUserId(6L); grant.setPermissions("[]");
        var security = mock(AccountSecurityService.class);
        when(security.validateSession(any())).thenReturn(user);
        var permissions = mock(AdminPermissionsMapper.class);
        when(permissions.selectOne(any())).thenReturn(grant);
        ReflectionTestUtils.setField(interceptor,"accountSecurityService",security);
        ReflectionTestUtils.setField(interceptor,"adminPermissionsMapper",permissions);
        ReflectionTestUtils.setField(interceptor,"authStateChangeService",mock(AuthStateChangeService.class));
        var snapshot = new UserProfileVO(); snapshot.setUserId(6L); snapshot.setRole("supper_admin");
        snapshot.setAccountStatus("normal"); session.setAttribute("user",snapshot);
    }

    boolean allowed(String path) throws Exception {
        var request = new MockHttpServletRequest("GET",path); request.setSession(session);
        return interceptor.preHandle(request,new MockHttpServletResponse(),new Object());
    }

    void grantDefaults() throws Exception {
        grant.setPermissions(new ObjectMapper().writeValueAsString(AdminRolePermissionService.ALLOWED_PERMISSIONS
                .stream().filter(p -> !"manage_permission".equals(p)).toList()));
    }

    @ParameterizedTest @ValueSource(strings={"/admin/user/list","/admin/user/auth-list","/admin/intro/honors",
            "/admin/term/list","/admin/certificate/grant/list","/admin/oj/list","/admin/org/list",
            "/admin/app-key/list","/admin/notice/list","/admin/training/list","/admin/category/list",
            "/admin/contest/list","/admin/data-panel"})
    void permissionsTakeEffectAndRevocationIsStillImmediate(String path) throws Exception {
        assertThat(allowed(path)).isFalse();
        grantDefaults();
        assertThat(allowed(path)).isTrue();
        grant.setPermissions("[]");
        assertThat(allowed(path)).isFalse();
    }

    @Test void defaultPermissionsDoNotGrantRootOnlyAdministration() throws Exception {
        grantDefaults();
        assertThat(allowed("/admin/permission/get/6")).isFalse();
    }

    @Test void studentCannotEnterAdministrationEvenWithStalePermissionRows() throws Exception {
        grantDefaults(); user.setRole("student");
        assertThat(allowed("/admin/user/list")).isFalse();
    }
}
