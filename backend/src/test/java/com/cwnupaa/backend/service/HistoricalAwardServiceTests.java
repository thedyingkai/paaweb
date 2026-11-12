package com.cwnupaa.backend.service;

import com.cwnupaa.backend.controller.HistoricalAwardController;
import com.cwnupaa.backend.controller.TeamSpaceController;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HistoricalAwardServiceTests {
    AnnotationConfigApplicationContext context;
    HistoricalAwardService service;
    JdbcTemplate db;
    ObjectMapper json=new ObjectMapper();
    MockMvc mvc;
    final String school=HistoricalAwardService.SCHOOL;
    List<Map<String,Object>> source;

    @BeforeEach void setup() throws Exception {
        context=new AnnotationConfigApplicationContext(Config.class);db=context.getBean(JdbcTemplate.class);service=context.getBean(HistoricalAwardService.class);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,real_name VARCHAR(100),number_id VARCHAR(50),major_id BIGINT,account_status VARCHAR(30),user_name VARCHAR(100))");
        db.execute("CREATE TABLE university(university_id BIGINT PRIMARY KEY,university_name VARCHAR(100))");
        db.execute("CREATE TABLE school(school_id BIGINT PRIMARY KEY,university_id BIGINT)");
        db.execute("CREATE TABLE major(major_id BIGINT PRIMARY KEY,school_id BIGINT)");
        db.execute("CREATE TABLE sys_intro(category VARCHAR(50) PRIMARY KEY,content TEXT)");
        db.execute("CREATE TABLE association_member(member_id BIGINT PRIMARY KEY,user_id BIGINT,term VARCHAR(50),department VARCHAR(50),position VARCHAR(50),start_date DATE,end_date DATE)");
        db.execute("CREATE TABLE certificate_level(level_id BIGINT PRIMARY KEY,level_type VARCHAR(30),rank_type VARCHAR(30),base_rating INTEGER)");
        db.update("INSERT INTO certificate_level VALUES(1,'国家级','二等奖',700),(2,'国家级','优秀奖',400)");
        db.update("INSERT INTO university VALUES (1,?),(2,'其他学校')",school);
        db.update("INSERT INTO school VALUES(1,1),(2,2)");db.update("INSERT INTO major VALUES(1,1),(2,2)");
        db.update("INSERT INTO users VALUES(1,'管理员','000',1,'normal','管理员'),(2,'张三','20260001',1,'normal','甲'),(3,'张三','20260002',1,'normal','乙'),(4,'张三','20260001',2,'normal','外校'),(9,'李四','20260009',1,'pending','未认证')");
        new ResourceDatabasePopulator(new FileSystemResource("../SQL/migration_besti_team_space.sql")).execute(context.getBean(DataSource.class));
        for(String column:List.of("award_level VARCHAR(20)","award_grade VARCHAR(20)","contest_stage VARCHAR(30)","contest_award VARCHAR(20)")) db.execute("ALTER TABLE besti_member_record ADD "+column);
        new ResourceDatabasePopulator(new FileSystemResource("../SQL/migration_besti_history_claims.sql")).execute(context.getBean(DataSource.class));
        source=new ArrayList<>();source.add(honor("formal",true,"regional","银牌","",List.of("张三","李四")));
        source.add(honor("star",false,"regional","","铜牌",List.of("张三","李四")));
        source.add(honor("prelim",true,"preliminary","","",List.of("张三","李四")));
        source.add(honor("missing",true,"regional","优胜奖","",List.of()));
        db.update("INSERT INTO sys_intro VALUES('honors',?)",json.writeValueAsString(source));
        mvc=MockMvcBuilders.standaloneSetup(new HistoricalAwardController(service)).build();
    }
    @AfterEach void close(){context.close();}
    Map<String,Object> honor(String uid,boolean official,String tier,String award,String equivalent,List<String> members) {
        var h=new LinkedHashMap<String,Object>();h.put("uid",uid);h.put("event","2026 ICPC 测试站 "+uid);h.put("title",h.get("event"));h.put("family","ICPC");h.put("date","2026-10-18");h.put("tier",tier);h.put("team","测试队");h.put("members",members);h.put("official",official);h.put("contestAward",award);h.put("equivalentContestAward",equivalent);h.put("awardLevel",award.isBlank()?null:"国家级");h.put("awardGrade",BestiRecordPolicyForTest(award));h.put("sources",List.of(Map.of("name","官方榜单","url","https://example.org/board/"+uid)));return h;
    }
    String BestiRecordPolicyForTest(String award){return Map.of("银牌","二等奖","优胜奖","优秀奖").get(award);}
    UserProfileVO actor(long id) {var u=new UserProfileVO();u.setUserId(id);u.setUserAccount(id==1?"admin":"student"+id);u.setRole(id==1?"super_admin":"student");u.setAccountStatus("normal");u.setPermissions(List.of());u.setRealName(id==1?"管理员":"张三");return u;}
    @SuppressWarnings("unchecked") String fingerprint(String uid){return ((List<Map<String,Object>>)service.list(actor(1),"",uid,false).get("items")).getFirst().get("sourceHash").toString();}
    HistoricalAwardService.ClaimInput input(String uid,int index,Integer version){return new HistoricalAwardService.ClaimInput(uid,index,fingerprint(uid),"我是该队的实际参赛成员，报名记录和证书可供核实","https://example.org/proof",version);}
    long submit(long user,String uid,int index){return ((Number)service.claim(actor(user),input(uid,index,null)).get("id")).longValue();}
    int count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    HistoricalAwardService.RosterRow row(String uid,int index,String number,String name){return new HistoricalAwardService.RosterRow(uid,index,school,number,name);}
    void importRows(List<HistoricalAwardService.RosterRow> rows){var preview=service.preview(actor(1),rows);assertThat(preview.get("errors")).isEqualTo(0);service.importRoster(actor(1),new HistoricalAwardService.ImportInput(rows,preview.get("token").toString()));}
    Map<String,Object> record(long user,String uid){return db.queryForMap("SELECT r.* FROM besti_member_record r JOIN besti_honor_link l ON l.id=r.history_link_id WHERE r.user_id=? AND l.honor_uid=?",user,uid);}

    @Test void claimIsPendingUntilReviewedAndPreservesNativeMedalAndSchoolGrade() {
        long id=submit(2,"formal",1);assertThat(count("besti_member_record")).isZero();
        service.review(actor(1),id,new HistoricalAwardService.Decision(1,true,"已核对报名名单"));
        assertThat(record(2,"formal")).containsEntry("kind","award").containsEntry("contest_award","银牌").containsEntry("award_level","国家级").containsEntry("award_grade","二等奖").containsEntry("status","verified");
        assertThat(count("besti_honor_audit")).isEqualTo(3);
    }
    @Test void onlyVerifiedFormalAwardsWithMatchingIdentityContributeRating() {
        var ratings=new MemberAwardRatingService(db);
        long claim=submit(2,"formal",1);
        assertThat(ratings.approvedAwards()).isEmpty();
        service.review(actor(1),claim,new HistoricalAwardService.Decision(1,true,"核验通过"));
        importRows(List.of(row("star",1,"20260001","张三"),row("prelim",1,"20260001","张三")));
        assertThat(ratings.approvedAwards()).hasSize(1);
        assertThat(ratings.approvedAwards().getFirst().points()).isEqualTo(700);
        db.update("UPDATE users SET real_name='修改后姓名' WHERE user_id=2");
        assertThat(ratings.approvedAwards()).isEmpty();
        db.update("UPDATE users SET real_name='张三' WHERE user_id=2");
        var link=db.queryForMap("SELECT * FROM besti_honor_link WHERE honor_uid='formal'");
        service.revoke(actor(1),((Number)link.get("id")).longValue(),new HistoricalAwardService.Revoke(((Number)link.get("version")).intValue(),"归属修正"));
        assertThat(ratings.approvedAwards()).isEmpty();
    }
    @Test void officialRolesAppearAlongsidePersonalRecordsWithExpiryAndVisibility() {
        db.update("INSERT INTO association_member VALUES(1,2,'2024-2025学年','ACM 校队','校队队长','2024-09-01','2025-08-31')");
        var controller=new com.cwnupaa.backend.controller.TeamSpaceController(db,mock(TeamMembershipService.class));
        var result=(Map<?,?>)controller.publicRecords(2L,1).getData();
        var positions=(List<Map<String,Object>>)result.get("positions");
        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst()).containsEntry("kind","position").containsEntry("position_status","former").containsEntry("source_type","official_roster");
        assertThat(positions.getFirst().get("display_title").toString()).contains("前").contains("校队队长");
        db.update("UPDATE users SET account_status='pending' WHERE user_id=2");
        assertThat((List<?>)((Map<?,?>)controller.publicRecords(2L,1).getData()).get("positions")).isEmpty();
    }
    @Test void rejectsWrongNameOtherSchoolAndUnverifiedIdentity() {
        assertThatThrownBy(()->submit(2,"formal",2)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("姓名");
        assertThatThrownBy(()->submit(4,"formal",1)).hasMessageContaining("学校身份");
        assertThatThrownBy(()->submit(9,"formal",2)).hasMessageContaining("身份认证");
        assertThat(count("besti_honor_link")).isZero();
    }
    @Test void sameNamedClaimantsAreNotAutomaticallyAssignedAndOnlyOneCanBeApproved() {
        long first=submit(2,"formal",1),second=submit(3,"formal",1);
        assertThat(count("besti_member_record")).isZero();
        service.review(actor(1),first,new HistoricalAwardService.Decision(1,true,"核对学号后通过"));
        assertThatThrownBy(()->service.review(actor(1),second,new HistoricalAwardService.Decision(1,true,""))).hasMessageContaining("已审核或已更新");
        assertThat(count("besti_member_record")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM besti_honor_claim WHERE id=?",String.class,second)).isEqualTo("rejected");
    }
    @Test void doubleSubmissionAndMultipleSlotsForOnePersonAreRejected() {
        submit(2,"missing",1);
        assertThatThrownBy(()->submit(2,"missing",2)).hasMessageContaining("请勿重复");
        assertThat(count("besti_honor_claim")).isEqualTo(1);
    }
    @Test void withdrawAndResubmitRequireCurrentVersionAndOwner() {
        long id=submit(2,"formal",1);
        assertThatThrownBy(()->service.withdraw(actor(3),id,1)).hasMessageContaining("不存在");
        service.withdraw(actor(2),id,1);
        assertThatThrownBy(()->service.claim(actor(2),input("formal",1,1))).hasMessageContaining("已变化");
        service.claim(actor(2),input("formal",1,2));
        assertThat(count("besti_honor_claim")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM besti_honor_claim WHERE id=?",String.class,id)).isEqualTo("pending");
    }
    @Test void sourceChangesPreventApprovalOfAnOldClaim() throws Exception {
        long id=submit(2,"formal",1);source.getFirst().put("contestAward","铜牌");db.update("UPDATE sys_intro SET content=?",json.writeValueAsString(source));
        assertThatThrownBy(()->service.review(actor(1),id,new HistoricalAwardService.Decision(1,true,""))).hasMessageContaining("战绩已变化");
        assertThat(count("besti_member_record")).isZero();
    }
    @Test void previewDoesNotWriteAndImportMatchesAllThreeIdentityFields() {
        var rows=List.of(row("formal",1,"20260002","张三"),row("formal",2,"20260009","李四"));
        var preview=service.preview(actor(1),rows);assertThat(preview).containsEntry("matched",1).containsEntry("waiting",1);assertThat(count("besti_honor_link")).isZero();
        importRows(rows);assertThat(count("besti_member_record")).isEqualTo(1);assertThat(record(3,"formal")).containsEntry("user_id",3L);
        importRows(rows);assertThat(count("besti_member_record")).isEqualTo(1);
    }
    @Test void verifiedIdentityAutoLinksTrustedRosterAndChangedIdentityStopsDisplay() {
        importRows(List.of(row("formal",2,"20260009","李四")));
        service.linkVerifiedUser(9);assertThat(count("besti_member_record")).isZero();
        db.update("UPDATE users SET account_status='normal' WHERE user_id=9");service.linkVerifiedUser(9);service.linkVerifiedUser(9);
        assertThat(count("besti_member_record")).isEqualTo(1);
        db.update("UPDATE users SET number_id='20269999' WHERE user_id=9");
        assertThat(db.queryForObject("SELECT count(*) FROM besti_member_record r WHERE "+HistoricalAwardService.visibleRecordSql("r"),Long.class)).isZero();
        service.linkVerifiedUser(9);
        assertThat(db.queryForObject("SELECT status FROM besti_honor_link",String.class)).isEqualTo("waiting");
    }
    @Test void starredMedalsAndPreliminaryAppearWithoutBecomingOfficialAwards() {
        importRows(List.of(row("formal",1,"20260001","张三"),row("star",1,"20260001","张三"),row("prelim",1,"20260001","张三")));
        assertThat(record(2,"star")).containsEntry("is_starred",true).containsEntry("award_level",null).containsEntry("award_grade",null).containsEntry("contest_award","铜牌");
        assertThat(record(2,"prelim")).containsEntry("kind","registration");
        var controller=new TeamSpaceController(db,mock(TeamMembershipService.class));
        var result=(Map<?,?>)controller.publicRecords(2L,1).getData();
        assertThat(result.get("awards")).isEqualTo(1L);assertThat(result.get("starred")).isEqualTo(1L);assertThat(result.get("total")).isEqualTo(3L);
    }
    @Test void stalePreviewIsRejectedAndDifferentNumberNeverMatchesByNameAlone() {
        var rows=List.of(row("formal",1,"20269999","张三"));var preview=service.preview(actor(1),rows);
        assertThat(preview.get("waiting")).isEqualTo(1);
        db.update("UPDATE users SET number_id='20269999' WHERE user_id=2");
        assertThatThrownBy(()->service.importRoster(actor(1),new HistoricalAwardService.ImportInput(rows,preview.get("token").toString()))).hasMessageContaining("重新预检");
        assertThat(count("besti_honor_link")).isZero();
    }
    @Test void malformedOrConflictingBatchWritesNothing() {
        var rows=List.of(row("formal",1,"20260001","张三"),row("formal",2,"20260001","李四"));var preview=service.preview(actor(1),rows);
        assertThat(preview.get("errors")).isEqualTo(1);
        assertThatThrownBy(()->service.importRoster(actor(1),new HistoricalAwardService.ImportInput(rows,preview.get("token").toString()))).hasMessageContaining("未写入");assertThat(count("besti_honor_link")).isZero();
    }
    @Test void databaseFailureRollsBackEarlierRowsAndAudit() {
        db.execute("ALTER TABLE besti_member_record ADD CONSTRAINT reject_fixture CHECK(user_id<>3)");
        var rows=List.of(row("formal",1,"20260001","张三"),row("star",1,"20260002","张三"));
        assertThatThrownBy(()->importRows(rows)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("besti_member_record")).isZero();assertThat(count("besti_honor_link")).isZero();assertThat(count("besti_honor_audit")).isZero();
    }
    @Test void revokeKeepsSourceAndAuditAndAllowsCorrectedOwnershipWithoutDuplicates() {
        long first=submit(2,"formal",1);service.review(actor(1),first,new HistoricalAwardService.Decision(1,true,"已核对"));
        var link=db.queryForMap("SELECT * FROM besti_honor_link");String original=db.queryForObject("SELECT content FROM sys_intro",String.class);
        service.revoke(actor(1),((Number)link.get("id")).longValue(),new HistoricalAwardService.Revoke(((Number)link.get("version")).intValue(),"更正同名归属"));
        assertThat(db.queryForObject("SELECT content FROM sys_intro",String.class)).isEqualTo(original);
        long corrected=submit(3,"formal",1);service.review(actor(1),corrected,new HistoricalAwardService.Decision(1,true,"已核对实际队员学号"));
        assertThat(count("besti_member_record")).isEqualTo(1);assertThat(record(3,"formal")).containsEntry("user_id",3L);
    }
    @Test void simultaneousApprovalsCannotAssignOneSlotTwice() throws Exception {
        long a=submit(2,"formal",1),b=submit(3,"formal",1);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Boolean>>();
            for(long id:List.of(a,b)) futures.add(pool.submit(()->{go.await();try{service.review(actor(1),id,new HistoricalAwardService.Decision(1,true,"并发核验"));return true;}catch(ResponseStatusException e){return false;}}));
            go.countDown();int success=0;for(var f:futures) if(f.get(10,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);assertThat(count("besti_member_record")).isEqualTo(1);
        }
    }
    @Test void privateClaimDetailsAndManagementRoutesAreNotExposedToOtherMembers() throws Exception {
        long id=submit(3,"formal",1);
        assertThat(service.claims(actor(2),false)).isEmpty();
        String catalog=json.writeValueAsString(service.list(actor(2),"","",false));assertThat(catalog).doesNotContain("20260002").doesNotContain("claimant_number");
        mvc.perform(get("/team-space/history/catalog")).andExpect(status().isUnauthorized());
        for(String path:List.of("/team-space/history/claims?review=true","/team-space/history/links","/team-space/history/links/1/audit")) mvc.perform(get(path).requestAttr("user",actor(2))).andExpect(status().isForbidden());
        mvc.perform(post("/team-space/history/claims/"+id+"/review").requestAttr("user",actor(2)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"approved\":true}")).andExpect(status().isForbidden());
        var pending=actor(9);pending.setAccountStatus("pending");mvc.perform(get("/team-space/history/catalog").requestAttr("user",pending)).andExpect(status().isForbidden());
        assertThat(count("besti_member_record")).isZero();
    }

    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource source(){return new DriverManagerDataSource("jdbc:h2:mem:history;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=6000","sa","");}
        @Bean JdbcTemplate jdbc(DataSource data){return new JdbcTemplate(data);}
        @Bean PlatformTransactionManager transactions(DataSource data){return new DataSourceTransactionManager(data);}
        @Bean HistoricalAwardService historical(JdbcTemplate db){return new HistoricalAwardService(db);}
    }
}
