package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.dto.SubmissionQueryDTO;
import com.cwnupaa.backend.service.TrackerService;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrackerSubmissionAccessTests {
    @Test void userCannotReadAnotherUsersDetailedHistory() {
        var service=mock(TrackerService.class); var controller=new TrackerController(service);
        var query=new SubmissionQueryDTO(); query.setUserId(6L);
        assertThat(controller.searchSubmissions(1L,null,query).getCode()).isEqualTo(403);
        verifyNoInteractions(service);
    }
    @Test void administratorWithPermissionQueriesTheSelectedUser() {
        var service=mock(TrackerService.class); var controller=new TrackerController(service);
        var admin=new UserProfileVO();admin.setUserId(1L);admin.setRole("supper_admin");admin.setAccountStatus("normal");admin.setPermissions(List.of("manage_user"));
        var query=new SubmissionQueryDTO(); query.setUserId(6L);
        controller.searchSubmissions(1L,admin,query);
        verify(service).searchSubmissions(query);
        assertThat(query.getUserId()).isEqualTo(6L);
    }
    @Test void removedPermissionCannotBeBypassedByTheAdministratorLabel() {
        var service=mock(TrackerService.class); var controller=new TrackerController(service);
        var admin=new UserProfileVO();admin.setRole("supper_admin");admin.setAccountStatus("normal");admin.setPermissions(List.of());
        var query=new SubmissionQueryDTO();query.setUserId(6L);
        assertThat(controller.searchSubmissions(1L,admin,query).getCode()).isEqualTo(403);
        verifyNoInteractions(service);
    }
    @Test void ordinaryUserCanQueryTheirOwnMergedRecords() {
        var service=mock(TrackerService.class);var controller=new TrackerController(service);
        var query=new SubmissionQueryDTO();controller.searchSubmissions(6L,null,query);
        verify(service).searchSubmissions(query);assertThat(query.getUserId()).isEqualTo(6L);
    }
}
