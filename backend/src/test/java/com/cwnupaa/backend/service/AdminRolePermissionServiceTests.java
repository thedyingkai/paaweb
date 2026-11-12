package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminRolePermissionServiceTests {
    final AdminPermissionsMapper mapper = mock(AdminPermissionsMapper.class);
    final AdminRolePermissionService service = new AdminRolePermissionService(mapper);

    Users target(String role) {
        var user = new Users();
        user.setUserId(6L); user.setUserAccount("owner"); user.setRole(role);
        return user;
    }

    AdminPermissions configured(String value) {
        var row = new AdminPermissions(); row.setId(10); row.setUserId(6L); row.setPermissions(value);
        when(mapper.selectOne(any())).thenReturn(row);
        return row;
    }

    @ParameterizedTest @ValueSource(strings={"supper_admin", "super_admin"})
    void newlyPromotedUnconfiguredAccountReceivesSiteManagementPermissions(String role) throws Exception {
        service.initializeForPromotion(target(role), "student");
        var saved = ArgumentCaptor.forClass(AdminPermissions.class);
        verify(mapper).insert(saved.capture());
        List<String> actual = new ObjectMapper().readValue(saved.getValue().getPermissions(), new TypeReference<>() {});
        assertThat(saved.getValue().getUserId()).isEqualTo(6L);
        assertThat(actual).hasSize(14).contains("manage_user", "manage_sys", "manage_cert_grant", "manage_term")
                .doesNotContain("manage_permission");
    }

    @Test void explicitEmptyRowIsInitializedWhenPromoted() {
        var existing = configured("[]");
        service.initializeForPromotion(target("supper_admin"), "stu_admin");
        verify(mapper).updateById(existing);
        assertThat(existing.getPermissions()).contains("manage_user", "manage_term");
        verify(mapper, never()).insert(any(AdminPermissions.class));
    }

    @Test void existingCustomPermissionsArePreservedOnPromotion() {
        var existing = configured("[\"manage_notice\"]");
        service.initializeForPromotion(target("supper_admin"), "stu_admin");
        assertThat(existing.getPermissions()).isEqualTo("[\"manage_notice\"]");
        verify(mapper, never()).updateById(any(AdminPermissions.class));
        verify(mapper, never()).insert(any(AdminPermissions.class));
    }

    @ParameterizedTest @ValueSource(strings={"supper_admin", "super_admin"})
    void resavingExistingSuperAdminDoesNotRestoreRevokedPermissions(String previous) {
        service.initializeForPromotion(target("supper_admin"), previous);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest @ValueSource(strings={"student", "school_admin", "stu_admin", "department"})
    void otherRoleChangesDoNotGrantSuperAdminPermissions(String role) {
        service.initializeForPromotion(target(role), "student");
        verifyNoInteractions(mapper);
    }

    @Test void malformedConfigurationFailsRatherThanOverwritingIt() {
        var existing = configured("invalid-json");
        assertThatThrownBy(() -> service.initializeForPromotion(target("supper_admin"), "student"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(existing.getPermissions()).isEqualTo("invalid-json");
        verify(mapper, never()).updateById(any(AdminPermissions.class));
    }
}
