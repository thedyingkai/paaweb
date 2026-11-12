package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.service.TeamMembershipService;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeamRecruitmentTests {
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final TeamMembershipService membership=mock(TeamMembershipService.class);
    final TeamRecruitmentController controller=new TeamRecruitmentController(db,membership);
    final Map<String,Object> window=new HashMap<>();
    final Map<String,Object> identity=new HashMap<>();
    final Map<String,Object> application=new HashMap<>();
    TeamRecruitmentTests() {
        window.putAll(Map.of("id",2L,"enabled",true,"opens_at",Timestamp.from(Instant.now().minusSeconds(3600)),
                "closes_at",Timestamp.from(Instant.now().plusSeconds(3600)),"term","2026-2027学年","member_start",LocalDate.now(),"member_end",LocalDate.now().plusYears(1),"version",1));
        identity.putAll(Map.of("user_id",6L,"account_status","normal","real_name","测试同学","number_id","20260001","school_name","北京电子科技学院"));
        application.putAll(Map.of("id",9L,"user_id",6L,"window_id",2L,"version",1,"status","pending","real_name","测试同学","student_number","20260001","school_name","北京电子科技学院"));
        when(db.queryForList(startsWith("SELECT * FROM besti_recruitment_window"),eq(2L))).thenReturn(List.of(window));
        when(db.queryForList(startsWith("SELECT u.user_id"),eq(6L))).thenReturn(List.of(identity));
        when(db.queryForList(startsWith("SELECT * FROM besti_team_application WHERE id="),eq(9L))).thenReturn(List.of(application));
    }
    MockHttpServletRequest request(long id,boolean admin) {
        var request=new MockHttpServletRequest(); var user=new UserProfileVO(); user.setUserId(id);user.setRole(admin?"super_admin":"student");
        user.setAccountStatus("normal");user.setUserAccount("member");user.setPermissions(admin?List.of("manage_sys"):List.of());request.setAttribute("user",user);return request;
    }
    TeamRecruitmentController.ApplicationInput form() { return new TeamRecruitmentController.ApplicationInput(2L,null,"学习算法","做过校赛","test@example.com","男","共青团员","test@example.com","校赛第 3 名","动态规划","无","Codeforces: tester / 100 题"); }
    TeamRecruitmentController.ReviewInput approve() { return new TeamRecruitmentController.ReviewInput(1,true,"",LocalDate.now(),LocalDate.now().plusMonths(10)); }
    @Test void openingIsInclusiveAndDeadlineExclusiveAndPauseWins() {
        Instant open=Instant.parse("2026-11-12T00:00:00Z"),close=open.plusSeconds(3600);
        assertThat(TeamRecruitmentController.windowStatus(true,open,close,open.minusNanos(1))).isEqualTo("upcoming");
        assertThat(TeamRecruitmentController.windowStatus(true,open,close,open)).isEqualTo("open");
        assertThat(TeamRecruitmentController.windowStatus(true,open,close,close.minusNanos(1))).isEqualTo("open");
        assertThat(TeamRecruitmentController.windowStatus(true,open,close,close)).isEqualTo("closed");
        assertThat(TeamRecruitmentController.windowStatus(false,open,close,open)).isEqualTo("paused");
    }
    @Test void submitStoresVerifiedIdentityButDoesNotGrantMembership() {
        assertThat(controller.submit(form(),request(6,false)).getCode()).isEqualTo(200);
        verify(db).update(startsWith("INSERT INTO besti_team_application"),eq(2L),eq(6L),eq("测试同学"),eq("20260001"),eq("北京电子科技学院"),eq("学习算法"),eq("做过校赛"),eq("test@example.com"),eq("男"),eq("共青团员"),eq("test@example.com"),eq("校赛第 3 名"),eq("动态规划"),eq("无"),eq("Codeforces: tester / 100 题"));
        verify(db,never()).queryForObject(startsWith("INSERT INTO association_member"),eq(Long.class),any(Object[].class));
    }
    @Test void upcomingExpiredPausedWindowsRejectSubmission() {
        window.put("opens_at",Timestamp.from(Instant.now().plusSeconds(100)));
        assertThatThrownBy(()->controller.submit(form(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        window.put("opens_at",Timestamp.from(Instant.now().minusSeconds(200)));window.put("closes_at",Timestamp.from(Instant.now().minusSeconds(1)));
        assertThatThrownBy(()->controller.submit(form(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        window.put("closes_at",Timestamp.from(Instant.now().plusSeconds(100)));window.put("enabled",false);
        assertThatThrownBy(()->controller.submit(form(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void identityAndCurrentMembershipAreCheckedOnServer() {
        identity.put("account_status","pending");
        assertThatThrownBy(()->controller.submit(form(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        identity.put("account_status","normal");identity.put("school_name","其他学校");
        assertThatThrownBy(()->controller.submit(form(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        identity.put("school_name","北京电子科技学院");when(membership.isCurrentMember(6L)).thenReturn(true);
        assertThatThrownBy(()->controller.submit(form(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void onlyAuthorizedAdministratorCanReadApplicationsOrReview() {
        assertThatThrownBy(()->controller.applications(2L,"all",1,request(6,false))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->controller.review(9L,approve(),request(6,false))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->controller.adminWindows(new MockHttpServletRequest())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
    }
    @Test void approvalCreatesMemberWithTermAndDoesNotElevateAccountRole() {
        when(db.queryForObject(startsWith("INSERT INTO association_member"),eq(Long.class),any(Object[].class))).thenReturn(88L);
        assertThat(controller.review(9L,approve(),request(1,true)).getCode()).isEqualTo(200);
        verify(db).queryForObject(startsWith("INSERT INTO association_member"),eq(Long.class),eq("2026-2027学年"),eq(6L),eq("测试同学"),eq("北京电子科技学院"),eq("20260001"),eq(LocalDate.now()),eq(LocalDate.now().plusMonths(10)));
        verify(db).update(startsWith("UPDATE besti_team_application SET status="),eq("approved"),eq(""),eq(1L),eq(88L),eq(9L));
        verify(db,never()).update(startsWith("UPDATE users"),any(Object[].class));
    }
    @Test void reviewRejectsStaleVersionsChangedIdentityAndDoubleApproval() {
        assertThatThrownBy(()->controller.review(9L,new TeamRecruitmentController.ReviewInput(0,true,"",null,null),request(1,true))).isInstanceOf(ResponseStatusException.class);
        identity.put("real_name","改名");
        assertThatThrownBy(()->controller.review(9L,approve(),request(1,true))).isInstanceOf(ResponseStatusException.class);
        identity.put("real_name","测试同学");application.put("status","approved");
        assertThatThrownBy(()->controller.review(9L,approve(),request(1,true))).isInstanceOf(ResponseStatusException.class);
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void ownApprovalAndAnotherUsersWithdrawalAreDenied() {
        assertThatThrownBy(()->controller.review(9L,approve(),request(6,true))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->controller.withdraw(9L,new TeamRecruitmentController.VersionInput(1),request(7,false))).isInstanceOf(ResponseStatusException.class);
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void rejectionRequiresReasonAndNeverCreatesMembership() {
        assertThatThrownBy(()->controller.review(9L,new TeamRecruitmentController.ReviewInput(1,false,"",null,null),request(1,true))).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.review(9L,new TeamRecruitmentController.ReviewInput(1,false,"请补充训练经历",null,null),request(1,true)).getCode()).isEqualTo(200);
        verify(db,never()).queryForObject(startsWith("INSERT INTO association_member"),eq(Long.class),any(Object[].class));
    }
    @Test void templateFieldsAreRequiredAndEmailIsValidatedBeforeWriting() {
        var f=form();
        for(String email:List.of("","not-email")) {
            var bad=new TeamRecruitmentController.ApplicationInput(f.windowId(),null,f.motivation(),f.experience(),f.contact(),f.gender(),f.politicalStatus(),email,f.campusRank(),f.learningContent(),f.studentWork(),f.platformAccounts());
            assertThatThrownBy(()->controller.submit(bad,request(6,false))).isInstanceOf(ResponseStatusException.class);
        }
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void identityFieldsAreTakenFromVerifiedAccountEvenIfRequestIsTampered() {
        identity.put("gender",2);identity.put("email","verified@example.com");
        assertThat(controller.submit(form(),request(6,false)).getCode()).isEqualTo(200);
        verify(db).update(startsWith("INSERT INTO besti_team_application"),eq(2L),eq(6L),eq("测试同学"),eq("20260001"),eq("北京电子科技学院"),eq("学习算法"),eq("做过校赛"),eq("test@example.com"),eq("女"),eq("共青团员"),eq("verified@example.com"),eq("校赛第 3 名"),eq("动态规划"),eq("无"),eq("Codeforces: tester / 100 题"));
    }
    @Test void exportsRequireAdministratorAndCannotIncludeOtherBatchesOrStaleSelection() {
        var body=new TeamRecruitmentController.ExportInput(2L,"pending",List.of(9L),"docx");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        assertThatThrownBy(()->controller.export(body,request(6,false),response)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
        assertThatThrownBy(()->controller.export(body,request(1,true),response)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("不属于当前筛选");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
