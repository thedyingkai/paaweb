package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.service.HonorShowcaseService;
import com.cwnupaa.backend.vo.HonorWallVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HonorShowcaseControllerTests {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final CertificateController controller = spy(new CertificateController());
    HonorShowcaseControllerTests() {
        ReflectionTestUtils.setField(controller, "db", db);
        ReflectionTestUtils.setField(controller, "honorShowcase", new HonorShowcaseService(db, new ObjectMapper()));
        var certificate = new HonorWallVO(); certificate.setId("G-2");
        doReturn(Result.success(List.of(certificate))).when(controller).getHonorWall(6L);
        when(db.queryForList(anyString(), eq(6L))).thenReturn(List.of(Map.of("id", 11L)));
    }
    private MockHttpServletRequest request(String status) {
        var request = new MockHttpServletRequest();
        var user = new UserProfileVO(); user.setUserId(6L); user.setRole("student"); user.setAccountStatus(status);
        request.setAttribute("user", user); return request;
    }
    @Test void ordinaryVerifiedUserCanChooseOwnRecordsAndCertificates() {
        var result = controller.saveShowcase(new CertificateController.ShowcaseRequest(List.of("certificate-G-2", "record-11")), request("normal"));
        assertThat(result.getCode()).isEqualTo(200);
        verify(db).update(contains("ON CONFLICT"), eq(6L), eq("[\"certificate-G-2\",\"record-11\"]"));
    }
    @Test void anonymousAndUnverifiedUsersCannotSaveOrReset() {
        var body = new CertificateController.ShowcaseRequest(List.of());
        assertThat(controller.saveShowcase(body, new MockHttpServletRequest()).getCode()).isEqualTo(401);
        assertThat(controller.resetShowcase(new MockHttpServletRequest()).getCode()).isEqualTo(401);
        assertThat(controller.saveShowcase(body, request("pending")).getCode()).isEqualTo(403);
        assertThat(controller.resetShowcase(request("banned")).getCode()).isEqualTo(403);
        verifyNoInteractions(db);
    }
    @Test void anotherMembersRecordCannotBeSelected() {
        assertThat(controller.saveShowcase(new CertificateController.ShowcaseRequest(List.of("record-12")), request("normal")).getCode()).isEqualTo(400);
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void showcaseIncludesAllRecordsBeyondFirstPublicPageAndUsesVerifiedOwnershipQuery() {
        List<Map<String,Object>> records = new ArrayList<>();
        for (long i=1; i<=70; i++) records.add(Map.of("id", i));
        when(db.queryForList(anyString(), eq(6L))).thenReturn(records);
        when(db.queryForList(anyString(), eq(String.class), eq(6L))).thenReturn(List.of());
        var result = controller.getShowcase(6L).getData();
        assertThat(result.records()).hasSize(70);
        assertThat(result.preferences().customized()).isFalse();
        verify(db).queryForList(argThat(sql -> sql.contains("r.user_id=?") && sql.contains("r.status='verified'")
                && sql.contains("hl.status='linked'") && !sql.contains("LIMIT")), eq(6L));
    }
}
