package com.cwnupaa.backend.service;

import com.cwnupaa.backend.controller.TrackerController;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class TrackerAdminRefreshTests {
    private final TrackerService service = mock(TrackerService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TrackerController(service)).build();

    private UserProfileVO actor(String role, String status, List<String> permissions) {
        var user = new UserProfileVO();
        user.setUserId(1L); user.setRole(role); user.setAccountStatus(status);
        user.setUserAccount("operator"); user.setPermissions(permissions);
        return user;
    }

    @Test void anonymousStudentsAndAdminsWithoutPermissionCannotStartMemberJobs() throws Exception {
        mvc.perform(post("/tracker/admin/user/6/refresh")).andExpect(jsonPath("$.code").value(403));
        for (var user : List.of(actor("student", "normal", List.of("manage_user")),
                actor("stu_admin", "normal", List.of()), actor("stu_admin", "disabled", List.of("manage_user")))) {
            mvc.perform(post("/tracker/admin/user/6/refresh").requestAttr("user", user))
                    .andExpect(jsonPath("$.code").value(403));
        }
        verifyNoInteractions(service);
    }

    @Test void authorizedJobTargetsSelectedMemberAndOnlyRequestedPlatforms() throws Exception {
        mvc.perform(post("/tracker/admin/user/6/refresh")
                        .requestAttr("user", actor("stu_admin", "normal", List.of("manage_user")))
                        .param("platforms", "QOJ", "UCup"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).checkSyncRequest(6L, List.of("QOJ", "UCup"), null);
        verify(service).refreshMemberTrackerQuietly(6L, List.of("QOJ", "UCup"));
        verifyNoMoreInteractions(service);
    }

    @Test void missingBindingsOrInvalidPlatformDoNotQueueWork() throws Exception {
        when(service.checkSyncRequest(6L, List.of("invalid"), null)).thenReturn("请选择支持的 OJ 平台");
        mvc.perform(post("/tracker/admin/user/6/refresh")
                        .requestAttr("user", actor("stu_admin", "normal", List.of("manage_user")))
                        .param("platforms", "invalid"))
                .andExpect(jsonPath("$.code").value(500));
        verify(service).checkSyncRequest(6L, List.of("invalid"), null);
        verifyNoMoreInteractions(service);
    }
}
