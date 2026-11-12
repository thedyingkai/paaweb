package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.apache.ibatis.annotations.Select;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RatingRecalculationTests {
    RatingService service;
    final Map<Long,RatingComposition> saved=new HashMap<>();
    final Map<Long,RatingTermRecord> snapshots=new HashMap<>();
    List<Term> terms=new ArrayList<>();
    List<Map<String,Object>> practice=new ArrayList<>();
    Users user;
    final LocalDate date=LocalDate.now().minusDays(20);
    @BeforeEach void setup() throws Exception {
        service=new RatingService();
        for(var f:RatingService.class.getDeclaredFields()) if(f.getType().getPackageName().endsWith(".mapper")) {f.setAccessible(true);f.set(service,mock(f.getType()));}
        ReflectionTestUtils.setField(service,"memberAwardRatingService",mock(MemberAwardRatingService.class));
        ReflectionTestUtils.setField(service,"certificateIdentityService",mock(CertificateIdentityService.class));
        ReflectionTestUtils.setField(service,"externalBoardService",mock(ExternalBoardService.class));
        user=new Users();user.setUserId(2L);user.setRealName("张三");user.setNumberId("001");user.setAccountStatus("normal");user.setMajorId(1L);
        when(get(UserMapper.class,"userMapper").selectList(null)).thenReturn(List.of(user));
        var major=new Major();major.setMajorId(1L);major.setSchoolId(1L);
        var school=new School();school.setSchoolId(1L);school.setUniversityId(1L);
        var uni=new University();uni.setUniversityId(1L);uni.setUniversityName("北京电子科技学院");
        when(get(MajorMapper.class,"majorMapper").selectBatchIds(any())).thenReturn(List.of(major));
        when(get(SchoolMapper.class,"schoolMapper").selectBatchIds(any())).thenReturn(List.of(school));
        when(get(UniversityMapper.class,"universityMapper").selectBatchIds(any())).thenReturn(List.of(uni));
        var level=new CertificateLevel();level.setLevelId(1L);level.setLevelType("国家级");level.setRankType("二等奖");level.setBaseRating(700);
        when(get(CertificateLevelMapper.class,"certificateLevelMapper").selectList(null)).thenReturn(List.of(level));
        when(get(TermMapper.class,"termMapper").selectList(any())).thenAnswer(x->{var result=new ArrayList<>(terms);Collections.reverse(result);return result;});
        when(get(OjPracticeRecordMapper.class,"ojPracticeRecordMapper").selectAllUserDailyStatsAfter(any(),any())).thenAnswer(inv->{LocalDateTime from=inv.getArgument(0),to=inv.getArgument(1);return practice.stream().filter(r->{var d=LocalDate.parse(r.get("date_str").toString()).atStartOfDay();return d.isAfter(from)&&!d.isAfter(to);}).toList();});
        var compositions=get(RatingCompositionMapper.class,"ratingCompositionMapper");
        when(compositions.selectList(null)).thenAnswer(x->new ArrayList<>(saved.values()));
        when(compositions.insert(any(RatingComposition.class))).thenAnswer(x->{RatingComposition r=x.getArgument(0);saved.put(r.getUserId(),r);return 1;});
        when(compositions.updateById(any(RatingComposition.class))).thenAnswer(x->{RatingComposition r=x.getArgument(0);saved.put(r.getUserId(),r);return 1;});
        var history=get(RatingTermRecordMapper.class,"ratingTermRecordMapper");
        when(history.insert(any(RatingTermRecord.class))).thenAnswer(x->{RatingTermRecord r=x.getArgument(0);snapshots.put(r.getTermId(),r);return 1;});
    }
    <T>T get(Class<T> cls,String field){return cls.cast(ReflectionTestUtils.getField(service,field));}
    MemberAwardRatingService.Award award(LocalDate date,int score){return new MemberAwardRatingService.Award(2,date,score,MemberAwardRatingService.key(2,"测试区域赛",date,"国家级","二等奖"));}
    void awards(MemberAwardRatingService.Award... awards){when(get(MemberAwardRatingService.class,"memberAwardRatingService").approvedAwards()).thenReturn(List.of(awards));}
    void practice(LocalDate date){practice.add(Map.of("user_id",2L,"date_str",date.toString(),"solved_count",9L));}
    Term term(long id,LocalDate from,LocalDate to){var t=new Term();t.setTermId(id);t.setStartTime(from);t.setEndTime(to);return t;}
    @Test void noTermsCalculatesAwardsAndPracticeWithoutCreatingFakeTermsAndIsIdempotent() {
        awards(award(date,700),award(date,700));practice(date);
        service.refreshAllRatings();service.refreshAllRatings();
        assertThat(saved.get(2L).getRatingAll()).isEqualTo(710);
        assertThat(saved.get(2L).getRatingCertificate()).isEqualTo(700);
        assertThat(saved.get(2L).getRatingPractice()).isEqualTo(10);
        assertThat(snapshots).isEmpty();
        verify(get(TermMapper.class,"termMapper"),never()).insert(any(Term.class));
    }
    @Test void revokedAwardsAreRemovedOnRecalculationAndFutureAwardsAreNotCredited() {
        awards(award(date,700),award(LocalDate.now().plusDays(1),999));practice(date);
        service.refreshAllRatings();assertThat(saved.get(2L).getRatingAll()).isEqualTo(710);
        awards();service.refreshAllRatings();assertThat(saved.get(2L).getRatingAll()).isEqualTo(10);
    }
    @Test void configuredTermsPreserveDecayAndDoNotRepeatAwardsAcrossTerms() {
        int year=LocalDate.now().getYear()-1;
        terms.add(term(1,LocalDate.of(year,1,1),LocalDate.of(year,6,30)));
        terms.add(term(2,LocalDate.of(year,7,1),LocalDate.of(year,12,31)));
        var first=LocalDate.of(year,3,1);var second=LocalDate.of(year,8,1);
        awards(award(first,700),award(second,400));practice(first);
        service.refreshAllRatings();
        assertThat(snapshots.get(1L).getRatingAll()).isEqualTo(710);
        assertThat(snapshots.get(2L).getRatingCertificate()).isEqualTo(540);
        assertThat(saved.get(2L).getRatingAll()).isEqualTo(542);
    }
    @Test void matchingVerifiedCertificateAndHistoricalAwardReceiveOneAwardScore() {
        var cert=new CertificateVerify();cert.setCertificateVerifyId(4L);cert.setUserId(2L);cert.setLevelId(1L);cert.setStatus("approved");cert.setEventName("测试区域赛");cert.setTime(date);
        when(get(CertificateVerifyMapper.class,"certificateVerifyMapper").selectList(any())).thenReturn(List.of(cert));
        when(get(CertificateIdentityService.class,"certificateIdentityService").verifiedUser(2L)).thenReturn(user);
        awards(award(date,700));service.refreshAllRatings();
        assertThat(saved.get(2L).getRatingCertificate()).isEqualTo(700);
    }
    @Test void officialCertificateWithoutTermIsIncludedInCurrentScore() {
        var type=new CertificateType();type.setCertificateTypeId(10L);type.setTypeId(1L);type.setTime(date);type.setTypeName("测试区域赛");
        var grant=new CertificateGrant();grant.setUserId(2L);grant.setCertificateTypeId(10L);grant.setSchoolName("北京电子科技学院");grant.setUserAccount("001");grant.setRealName("张三");
        when(get(CertificateTypeMapper.class,"certificateTypeMapper").selectList(null)).thenReturn(List.of(type));
        when(get(CertificateGrantMapper.class,"certificateGrantMapper").selectList(null)).thenReturn(List.of(grant));
        awards(award(date,700));service.refreshAllRatings();
        assertThat(saved.get(2L).getRatingCertificate()).isEqualTo(700);
    }
    @Test void practiceQueryDistinguishesPlatformsDeduplicatesProblemsAndBoundsDates() throws Exception {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:rating_practice;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE oj_practice_record(user_id BIGINT,source_id BIGINT,problem_id VARCHAR(100),ac_time TIMESTAMP)");
        db.update("INSERT INTO oj_practice_record VALUES(2,1,'A','2025-06-01 12:00:00'),(2,2,'A','2025-06-01 12:01:00'),(2,1,'A','2025-06-01 12:02:00'),(2,1,'FUTURE','2025-06-02 12:00:00')");
        String sql=OjPracticeRecordMapper.class.getMethod("selectAllUserDailyStatsAfter",LocalDateTime.class,LocalDateTime.class).getAnnotation(Select.class).value()[0].replace("#{minTime}","?").replace("#{maxTime}","?");
        var rows=db.queryForList(sql,LocalDateTime.of(2025,6,1,0,0),LocalDateTime.of(2025,6,1,23,59,59));
        assertThat(rows).hasSize(1);assertThat(((Number)rows.getFirst().get("solved_count")).intValue()).isEqualTo(2);
    }
}
