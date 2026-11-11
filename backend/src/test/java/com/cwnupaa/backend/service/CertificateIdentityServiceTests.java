package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CertificateIdentityServiceTests {
    private Users user(String status) {
        Users user = new Users(); user.setUserId(7L); user.setAccountStatus(status);
        user.setRealName("张三"); user.setNumberId("001"); user.setMajorId(1L); return user;
    }
    private CertificateGrant grant(String school, String name) {
        CertificateGrant grant = new CertificateGrant(); grant.setCertificateDownloadId(4L);
        grant.setUserAccount("001"); grant.setSchoolName(school); grant.setRealName(name); return grant;
    }
    @Test void requiresVerifiedStatusAndEveryIdentityField() {
        assertThat(CertificateIdentityService.matches(user("normal"), "学校A", "学校A", "001", "张三")).isTrue();
        for (String status : List.of("pending", "banned", "external", "rejected"))
            assertThat(CertificateIdentityService.matches(user(status), "学校A", "学校A", "001", "张三")).isFalse();
        assertThat(CertificateIdentityService.matches(user("normal"), "学校A", null, "001", "张三")).isFalse();
        assertThat(CertificateIdentityService.matches(user("normal"), "学校A", "学校B", "001", "张三")).isFalse();
        assertThat(CertificateIdentityService.matches(user("normal"), "学校A", "学校A", "001", "李四")).isFalse();
        assertThat(CertificateIdentityService.matches(user("normal"), "学校A", "学校A", "002", "张三")).isFalse();
    }
    @Test void approvedBindingUsesDatabaseIdentityAndSkipsLegacyOrWrongRecipients() {
        CertificateGrantService service = new CertificateGrantService();
        HistoricalAwardService history=mock(HistoricalAwardService.class);
        ReflectionTestUtils.setField(service,"historicalAwardService",history);
        UserMapper users = mock(UserMapper.class);
        CertificateIdentityService identities = spy(new CertificateIdentityService());
        ReflectionTestUtils.setField(identities,"userMapper",users);
        when(users.selectById(7L)).thenReturn(user("normal"));
        doReturn("学校A").when(identities).schoolName(any());
        CertificateGrantMapper grants = mock(CertificateGrantMapper.class);
        CertificateIssueMemberMapper members = mock(CertificateIssueMemberMapper.class);
        CertificateGrant valid = grant("学校A","张三");
        CertificateGrant legacy = grant(null,"张三");
        CertificateGrant differentName = grant("学校A","李四");
        when(grants.selectList(any())).thenReturn(List.of(valid,legacy,differentName));
        when(members.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service,"certificateIdentityService",identities);
        ReflectionTestUtils.setField(service,"grantMapper",grants);
        ReflectionTestUtils.setField(service,"issueMemberMapper",members);
        service.linkCertificatesToUser(7L,"forged","学校B");
        verify(history).linkVerifiedUser(7L);
        assertThat(valid.getUserId()).isEqualTo(7L);
        assertThat(legacy.getUserId()).isNull(); assertThat(differentName.getUserId()).isNull();
        verify(grants).updateById(valid); verify(grants,never()).updateById(legacy);
    }
    @Test void pendingAccountsNeverTriggerBindingQueries() {
        CertificateGrantService service = new CertificateGrantService();
        CertificateIdentityService identities = mock(CertificateIdentityService.class);
        CertificateGrantMapper grants = mock(CertificateGrantMapper.class);
        ReflectionTestUtils.setField(service,"certificateIdentityService",identities);
        ReflectionTestUtils.setField(service,"grantMapper",grants);
        service.linkCertificatesToUser(7L,"001","学校A"); verifyNoInteractions(grants);
    }
    @Test void manualConfirmationRequiresVerifiedTripleAndAuditsSuccessfulLegacyBinding() {
        CertificateGrantService service = new CertificateGrantService();
        CertificateIdentityService identities = mock(CertificateIdentityService.class);
        Users target = user("normal");
        when(identities.verifiedUser(7L)).thenReturn(target);
        when(identities.matches(target,"学校A","001","张三")).thenReturn(true);
        CertificateGrantMapper grants = mock(CertificateGrantMapper.class);
        CertificateGrant legacy = grant(null,"张三"); when(grants.selectOne(any())).thenReturn(legacy);
        SecurityAuditService audit = mock(SecurityAuditService.class);
        ReflectionTestUtils.setField(service,"certificateIdentityService",identities);
        ReflectionTestUtils.setField(service,"grantMapper",grants);
        ReflectionTestUtils.setField(service,"securityAuditService",audit);
        assertThat(service.confirmIdentity(4L,7L,"学校B","001","张三",1L).getCode()).isNotEqualTo(200);
        verifyNoInteractions(grants);
        assertThat(service.confirmIdentity(4L,7L,"学校A","001","张三",1L).getCode()).isEqualTo(200);
        assertThat(legacy.getSchoolName()).isEqualTo("学校A"); assertThat(legacy.getUserId()).isEqualTo(7L);
        verify(audit).record(1L,7L,"certificate_identity_confirm:4",List.of("school_name","user_id"));
    }
}
