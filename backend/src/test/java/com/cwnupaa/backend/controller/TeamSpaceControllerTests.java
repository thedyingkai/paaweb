package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.vo.UserProfileVO;
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
    private final TeamSpaceController controller=new TeamSpaceController(db);
    private MockHttpServletRequest request(String role,String status) {
        var request=new MockHttpServletRequest();var user=new UserProfileVO();
        user.setUserId(7L);user.setUserAccount("student".equals(role)?"member":"admin");user.setUserName("测试成员");
        user.setRole(role);user.setAccountStatus(status);user.setPermissions(List.of());request.setAttribute("user",user);return request;
    }
    private TeamSpaceController.MemberRecord record(String kind,String family,String url) {
        return new TeamSpaceController.MemberRecord(null,null,kind,family,"测试记录","","","",null,null,url,"");
    }
    @Test void anonymousCannotReadForum() {
        var error=assertThrows(ResponseStatusException.class,()->controller.topics(new MockHttpServletRequest(),1,"",""));
        assertEquals(HttpStatus.UNAUTHORIZED,error.getStatusCode());verifyNoInteractions(db);
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
        var r=new TeamSpaceController.MemberRecord(42L,1,"award","ICPC","测试","","","",null,null,"","");
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
        var r=new TeamSpaceController.MemberRecord(null,null,"position","","队长","","","",LocalDate.of(2026,11,10),LocalDate.of(2026,1,1),"","");
        assertThrows(ResponseStatusException.class,()->TeamSpaceController.validate(r));
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
