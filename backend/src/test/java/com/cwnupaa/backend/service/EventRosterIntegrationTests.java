package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.mapper.EventMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Disposable local PostgreSQL only. Real SQL, constraints, matching and rollback. */
@EnabledIfSystemProperty(named="roster.audit", matches="true")
class EventRosterIntegrationTests {
    JdbcTemplate db; EventRosterService service; Event event; String schema; JdbcTemplate admin;
    EventRosterService.Entry entry(String number,String name) { return new EventRosterService.Entry("测试大学",number,name,"新生","01",1); }
    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("roster.audit.url");
        assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[A-Za-z_]+$");
        schema="roster_test_"+UUID.randomUUID().toString().replace("-","");
        var source=new DriverManagerDataSource(url,"roster_test","roster_test_local_only"); admin=new JdbcTemplate(source); admin.execute("CREATE SCHEMA "+schema);
        var testSource=new DriverManagerDataSource(url+"?currentSchema="+schema,"roster_test","roster_test_local_only"); db=new JdbcTemplate(testSource);
        db.execute("CREATE TABLE event(event_id bigint primary key,signup_end timestamp NOT NULL,max_signup int NOT NULL)");
        db.execute("CREATE TABLE university(university_id bigint primary key,university_name text)");
        db.execute("CREATE TABLE school(school_id bigint primary key,university_id bigint)");
        db.execute("CREATE TABLE major(major_id bigint primary key,school_id bigint)");
        db.execute("CREATE TABLE users(user_id bigint primary key,major_id bigint,number_id text,real_name text,account_status text)");
        db.execute("CREATE TABLE signup_personal(signup_id bigserial primary key,event_id bigint,user_id bigint,status text,signup_time timestamp,is_ranking int,type text,seat_number text,signup_source text,UNIQUE(event_id,user_id))");
        // Execute the actual deployment migration on a database with the old constraints.
        db.execute(Files.readString(Path.of("../SQL/migration_event_open_signup.sql")).replace("BEGIN;","").replace("COMMIT;",""));
        db.update("INSERT INTO event(event_id,signup_mode) VALUES(9,'roster')");
        db.update("INSERT INTO university VALUES(1,'测试大学'),(2,'其他大学')");
        db.update("INSERT INTO school VALUES(1,1),(2,2)"); db.update("INSERT INTO major VALUES(1,1),(2,2)");
        event=new Event();event.setEventId(9L);event.setSignupMode("roster");event.setIsTeam(0);event.setMaxSignup(61);
        var mapper=mock(EventMapper.class);when(mapper.selectForSignupUpdate(9L)).thenReturn(event);
        service=new EventRosterService(db,mapper,new TransactionTemplate(new DataSourceTransactionManager(testSource)));
    }
    @AfterEach void cleanup(){ if(admin!=null && schema!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    @Test void importsAllEntrantsButOnlyBindsExactVerifiedIdentityAndLaterApproval() {
        db.update("INSERT INTO users VALUES(1,1,'001','甲','normal'),(2,1,'002','乙','pending'),(3,2,'003','丙','normal'),(4,1,'004','同号不同名','normal')");
        var entries=List.of(entry("001","甲"),entry("002","乙"),entry("003","丙"),entry("004","丁"),entry("005","戊"));
        service.importRoster(9L,entries);service.importRoster(9L,entries);
        assertThat(service.count(9)).isEqualTo(5);assertThat(db.queryForObject("SELECT COUNT(*) FROM signup_personal",Long.class)).isEqualTo(1);
        assertThat(service.eligible(9,1)).isTrue();assertThat(service.eligible(9,2)).isFalse();assertThat(service.eligible(9,3)).isFalse();assertThat(service.eligible(9,4)).isFalse();
        db.update("UPDATE users SET account_status='normal' WHERE user_id=2");service.reconcileVerifiedAccounts();service.reconcileVerifiedAccounts();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM signup_personal",Long.class)).isEqualTo(2);
        assertThat(service.rows(9).stream().filter(row->row.userId()!=null)).hasSize(2);
        // A changed/unverified identity must never retrieve previously bound credentials.
        db.update("UPDATE users SET real_name='改名' WHERE user_id=1");assertThat(service.eligible(9,1)).isFalse();
    }
    @Test void capacityAndConflictingIdentityRollBackWholeImport() {
        event.setMaxSignup(1);service.importRoster(9L,List.of(entry("001","甲")));
        assertThatThrownBy(()->service.importRoster(9L,List.of(entry("002","乙")))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.count(9)).isEqualTo(1);
        assertThatThrownBy(()->service.importRoster(9L,List.of(entry("001","别人")))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.rows(9).getFirst().realName()).isEqualTo("甲");
        assertThatThrownBy(()->service.importRoster(9L,List.of(entry("001","甲"),entry("001","甲")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publicPageIsCompletePagedAndDoesNotExposeIdentityOrSeats() {
        service.importRoster(9L,List.of(entry("private-number","甲"),new EventRosterService.Entry("测试大学","second-number","乙","老生","private-seat",0)));
        var page=service.publicPage(9,1,1,null,null);assertThat(page.getTotal()).isEqualTo(2);assertThat(page.getRecords()).hasSize(1);
        var row=page.getRecords().getFirst();assertThat(row.getNumberId()).isNull();assertThat(row.getSeatNumber()).isNull();assertThat(row.getUserId()).isNull();assertThat(row.getContestPassword()).isNull();
        assertThat(service.publicPage(9,1,10,"private-number",null).getTotal()).isZero();
        assertThat(service.publicPage(9,1,10,null,"star").getRecords().getFirst().getRealName()).isEqualTo("乙");
    }
}
