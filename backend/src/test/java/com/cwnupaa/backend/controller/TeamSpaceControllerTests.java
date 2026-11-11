package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.vo.UserProfileVO;
import com.cwnupaa.backend.service.TeamMembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamSpaceControllerTests {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final TeamMembershipService membership=mock(TeamMembershipService.class);
    private final TeamSpaceController controller=new TeamSpaceController(db,membership);
    private MockHttpServletRequest request(String role,String status) {
        var request=new MockHttpServletRequest();var user=new UserProfileVO();
        user.setUserId(7L);user.setUserAccount("student".equals(role)?"member":"admin");user.setUserName("测试成员");
        user.setRole(role);user.setAccountStatus(status);user.setPermissions(List.of());request.setAttribute("user",user);return request;
    }
    private TeamSpaceController.MemberRecord record(String kind,String family,String url) {
        return new TeamSpaceController.MemberRecord(null,null,kind,family,
            "position".equals(kind)?"校队队长":"测试记录", "position".equals(kind)?"ACM 校队":"",
            "","",LocalDate.of(2026,9,1),LocalDate.of(2027,8,31),url,"","国家级","一等奖","regional","金牌");
    }
    @Test void anonymousCannotReadForum() {
        var error=assertThrows(ResponseStatusException.class,()->controller.topics(new MockHttpServletRequest(),1,"",""));
        assertEquals(HttpStatus.UNAUTHORIZED,error.getStatusCode());verifyNoInteractions(db);
    }
    @Test void identityVerificationDoesNotGrantTeamEligibility() {
        var data=(Map<?,?>)controller.me(request("student","normal")).getData();
        assertEquals(false,data.get("teamMember"));
        assertEquals(false,data.get("communityAccess"));
        assertEquals(false,data.get("moderator"));
        verifyNoInteractions(db);
    }
    @Test void ordinaryParticipantsAreRejectedByEveryPrivateForumEndpoint() {
        var req=request("student","normal");
        var topic=new TeamSpaceController.Topic("discussion","测试","内容");
        var reply=new TeamSpaceController.Reply("内容");
        List<org.junit.jupiter.api.function.Executable> attempts=List.of(
            ()->controller.topics(req,1,"",""),
            ()->controller.topic(req,1),
            ()->controller.createTopic(req,topic),
            ()->controller.editTopic(req,1,topic),
            ()->controller.deleteTopic(req,1),
            ()->controller.moderate(req,1,new TeamSpaceController.Moderation(true,false)),
            ()->controller.replies(req,1,1),
            ()->controller.reply(req,1,reply),
            ()->controller.editReply(req,1,reply),
            ()->controller.deleteReply(req,1)
        );
        for(var attempt:attempts) {
            assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,attempt).getStatusCode());
        }
        verifyNoInteractions(db);
    }
    @Test void currentMembershipGrantsForumAccessWithoutAdministrativePrivileges() {
        when(membership.isCurrentMember(7L)).thenReturn(true);
        var req=request("student","normal");
        var data=(Map<?,?>)controller.me(req).getData();
        assertEquals(true,data.get("teamMember"));
        assertEquals(true,data.get("communityAccess"));
        assertEquals(false,data.get("moderator"));
        when(db.queryForList(anyString(),eq(1L))).thenReturn(List.of(Map.of("id",1L,"content","内部内容")));
        assertNotNull(controller.topic(req,1).getData());
        assertThrows(ResponseStatusException.class,()->controller.moderate(req,1,new TeamSpaceController.Moderation(true,false)));
    }
    @Test void ordinaryParticipantsCanStillMaintainTheirOwnAwards() {
        when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(19L);
        assertNotNull(controller.saveRecord(request("student","normal"),record("award","ICPC","")).getData());
        verifyNoInteractions(membership);
    }
    @Test void rootCanModerateWithoutBeingDisplayedAsATeamMember() {
        var req=request("super_admin","normal");
        var data=(Map<?,?>)controller.me(req).getData();
        assertEquals(false,data.get("teamMember"));
        assertEquals(true,data.get("communityAccess"));
        when(db.update(anyString(),eq(true),eq(false),eq(1L))).thenReturn(1);
        assertDoesNotThrow(()->controller.moderate(req,1,new TeamSpaceController.Moderation(true,false)));
    }
    @Test void pendingOrBannedAccountsCannotEnterTeamSpace() {
        for(String status:List.of("pending","banned","pending_activation")) {
            assertThrows(ResponseStatusException.class,()->controller.me(request("student",status)));
        }
        verifyNoInteractions(db);
    }
    @Test void allVoluntaryFamiliesRejectCaptainBatchEvenForRoot() {
        for(String family:TeamSpaceController.VOLUNTARY) {
            var body=new TeamSpaceController.Roster(record("registration",family,""),List.of(8L));
            var error=assertThrows(ResponseStatusException.class,()->controller.roster(request("super_admin","normal"),body));
            assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        }
        verifyNoInteractions(db);
    }
    @Test void ordinaryMemberCannotBatchRegisterOtherMembers() {
        var body=new TeamSpaceController.Roster(record("registration","ICPC",""),List.of(8L));
        assertThrows(ResponseStatusException.class,()->controller.roster(request("student","normal"),body));verifyNoInteractions(db);
    }
    @Test void selfDeclaredCaptainDoesNotGrantPrivileges() {
        var req=request("student","normal");
        when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(19L);
        controller.saveRecord(req,record("position","",""));
        var u=(UserProfileVO)req.getAttribute("user");assertEquals("student",u.getRole());
        assertThrows(ResponseStatusException.class,()->controller.review(req,1L,new TeamSpaceController.Review(1,true,"")));
    }
    @Test void memberCannotOverwriteAnotherOwner() {
        when(db.queryForList("SELECT user_id,version FROM besti_member_record WHERE id=? FOR UPDATE",42L)).thenReturn(List.of(Map.of("user_id",8L,"version",1)));
        var r=new TeamSpaceController.MemberRecord(42L,1,"award","ICPC","测试","","","",null,null,"","","省部级","二等奖","invitational","银牌");
        var error=assertThrows(ResponseStatusException.class,()->controller.saveRecord(request("student","normal"),r));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void rejectsOutOfScopeFamilyAndUnsafeSource() {
        assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(record("award","其他","")));
        for(String url:List.of("javascript:alert(1)","data:text/html,test","https://user:secret@example.org/"))
            assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(record("award","ICPC",url)));
        assertDoesNotThrow(()->TeamSpaceController.validate(record("award","ICPC","https://board.xcpcio.com/icpc/51st/xian")));
    }
    @Test void rejectsReversedTenureDates() {
        var r=new TeamSpaceController.MemberRecord(null,null,"position","","校队队长","ACM 校队","","",LocalDate.of(2026,11,10),LocalDate.of(2026,1,1),"","",null,null,null,null);
        assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(r));
    }
    @Test void allAwardLevelsAndGradesAreAcceptedButFreeTextIsRejected() {
        for (String level : List.of("省部级", "国家级", "校级")) {
            for (String grade : List.of("一等奖", "二等奖", "三等奖", "特等奖", "优秀奖")) {
                var r = new TeamSpaceController.MemberRecord(null,null,"award","联盟主办","比赛","","","",null,null,"","",level,grade,null,null);
                assertDoesNotThrow(() -> TeamSpaceController.validate(r));
            }
        }
        var r = new TeamSpaceController.MemberRecord(null,null,"award","ICPC","比赛","","金牌","",null,null,"","","国际级","金奖","regional","金牌");
        assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(r));
    }
    @Test void positionRequiresMatchingOrganizationAndExplicitTenure() {
        var wrongOrganization = new TeamSpaceController.MemberRecord(null,null,"position","","主要负责人","计算机工程师联盟","","",LocalDate.of(2026,9,1),LocalDate.of(2027,8,31),"","",null,null,null,null);
        assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(wrongOrganization));
        var noEnd = new TeamSpaceController.MemberRecord(null,null,"position","","队员","ACM 校队","","",LocalDate.of(2026,9,1),null,"","",null,null,null,null);
        assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(noEnd));
    }
    @Test void xcpcRecognitionMatchesCompetitionAwardAndStage() {
        String[][] pairs = {{"金牌","一等奖"},{"银牌","二等奖"},{"铜牌","三等奖"},{"优胜奖","优秀奖"}};
        for (String family : List.of("ICPC","CCPC")) for (String stage : List.of("regional","final","invitational")) for (String[] pair : pairs) {
            String level = "invitational".equals(stage) ? "省部级" : "国家级";
            var valid = new TeamSpaceController.MemberRecord(null,null,"award",family,"比赛","","","",null,null,"","",level,pair[1],stage,pair[0]);
            assertDoesNotThrow(() -> TeamSpaceController.validate(valid));
            var wrongGrade = new TeamSpaceController.MemberRecord(null,null,"award",family,"比赛","","","",null,null,"","",level,"特等奖",stage,pair[0]);
            assertThrows(ResponseStatusException.class,() -> TeamSpaceController.validate(wrongGrade));
            var wrongLevel = new TeamSpaceController.MemberRecord(null,null,"award",family,"比赛","","","",null,null,"","","校级",pair[1],stage,pair[0]);
            assertThrows(ResponseStatusException.class,() -> TeamSpaceController.validate(wrongLevel));
        }
    }
    @Test void xcpcRejectsMissingOrRecognizedGradeAsOriginalAward() {
        for (String original : new String[]{null,"","一等奖","优秀奖"}) {
            var r = new TeamSpaceController.MemberRecord(null,null,"award","ICPC","比赛","","","",null,null,"","","国家级","优秀奖","regional",original);
            assertThrows(ResponseStatusException.class,() -> TeamSpaceController.validate(r));
        }
    }
    @Test void ordinaryMemberCannotPublishOfficialAnnouncement() {
        assertThrows(ResponseStatusException.class,()->controller.createTopic(request("student","normal"),new TeamSpaceController.Topic("announcement","标题","内容")));
        verifyNoInteractions(db);
    }
    @Test void lockPreventsReplyEvenFromAuthor() {
        when(db.queryForList("SELECT locked FROM besti_forum_topic WHERE id=? FOR UPDATE",42L)).thenReturn(List.of(Map.of("locked",true)));
        assertThrows(ResponseStatusException.class,()->controller.reply(request("student","normal"),42L,new TeamSpaceController.Reply("回复")));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
}
