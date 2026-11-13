package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.dto.EventDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EventSignupPolicy;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.cwnupaa.backend.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventOpenSignupTests {
    EventController controller;
    Event event;
    Users user;
    MockHttpServletRequest request;
    @SuppressWarnings("unchecked") <T> T dep(String name) { return (T)ReflectionTestUtils.getField(controller,name); }
    @BeforeEach void setup() {
        controller=new EventController();
        for(var field:EventController.class.getDeclaredFields())if(field.isAnnotationPresent(Autowired.class))ReflectionTestUtils.setField(controller,field.getName(),mock(field.getType()));
        event=new Event();event.setEventId(9L);event.setCategoryId(1L);event.setTermId(1L);event.setName("固定名单报名确认");event.setIsTeam(0);event.setType("XCPC");event.setStatus(1);
        event.setSignupStart(LocalDateTime.now().minusDays(1));event.setEventStart(LocalDateTime.now().plusDays(15));event.setEventEnd(LocalDateTime.now().plusDays(15).plusHours(5));
        EventMapper events=dep("eventMapper");when(events.selectForSignupUpdate(9L)).thenReturn(event);when(events.selectById(9L)).thenReturn(event);
        user=new Users();user.setUserId(6L);user.setRealName("合成测试成员");user.setEmail("synthetic@example.invalid");user.setGender(1);user.setAccountStatus("normal");user.setIdPhotoFileId(1L);
        UserMapper users=dep("userMapper");when(users.selectById(6L)).thenReturn(user);
        request=new MockHttpServletRequest();var snapshot=new UserProfileVO();snapshot.setUserId(6L);request.setAttribute("user",snapshot);
    }
    Map<String,Object> form() { return Map.of("eventId",9,"teamType","official","isRanking",1); }
    @Test void fixedRosterRejectsSelfSubmissionPreviewCancellationAndLegacyImport() {
        event.setSignupMode("roster");
        assertThat(controller.signup(form(),request).getCode()).isEqualTo(403);
        assertThat(controller.signupPreview(form(),request).getCode()).isEqualTo(403);
        assertThat(controller.cancel(Map.of("eventId",9L),request).getCode()).isEqualTo(403);
        assertThat(controller.batchImport(Map.of("eventId",9L,"content","not-a-member")).getCode()).isEqualTo(403);
        assertThat(controller.detail(9L).getData().getState()).isEqualTo("waiting");
        SignupPersonalMapper signups=dep("signupPersonalMapper");verify(signups,never()).insert(any(SignupPersonal.class));
        verify(signups,never()).updateById(any(SignupPersonal.class));
    }
    @Test void noDeadlineOrQuotaAllowsNormalApplicantAndKeepsManualReview() {
        assertThat(controller.detail(9L).getData().getState()).isEqualTo("registering");
        assertThat(controller.signup(form(),request).getCode()).isEqualTo(200);
        var saved=ArgumentCaptor.forClass(SignupPersonal.class);
        SignupPersonalMapper signups=dep("signupPersonalMapper");verify(signups).insert(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("pending");assertThat(saved.getValue().getUserId()).isEqualTo(6L);
    }
    @Test void noDeadlineDoesNotBypassStartIdentityPhotoOrQuotaChecks() {
        event.setSignupStart(LocalDateTime.now().plusDays(1));assertThat(controller.signup(form(),request).getMsg()).contains("未开始");
        event.setSignupStart(LocalDateTime.now().minusDays(1));user.setAccountStatus("pending");assertThat(controller.signup(form(),request).getCode()).isNotEqualTo(200);
        user.setAccountStatus("normal");user.setIdPhotoFileId(null);assertThat(controller.signup(form(),request).getMsg()).contains("证件照");
        user.setIdPhotoFileId(1L);event.setMaxSignup(61);SignupPersonalMapper signups=dep("signupPersonalMapper");when(signups.countApproved(9L)).thenReturn(61L);
        assertThat(controller.signup(form(),request).getMsg()).contains("名额已满");verify(signups,never()).insert(any(SignupPersonal.class));
    }
    @Test void configuredDeadlineStillStopsSubmissionAndCancellationAtCutoff() {
        event.setSignupEnd(LocalDateTime.now().minusSeconds(1));assertThat(controller.signup(form(),request).getMsg()).contains("截止");
        var policy=new EventSignupPolicy(mock(SignupPersonalMapper.class),mock(SignupTeamMapper.class));
        var cutoff=LocalDateTime.of(2026,11,26,23,59);event.setSignupEnd(cutoff);
        assertThat(policy.isCancellationClosed(event,cutoff.minusNanos(1))).isFalse();assertThat(policy.isCancellationClosed(event,cutoff)).isTrue();
        event.setSignupEnd(null);assertThat(policy.isCancellationClosed(event,cutoff.plusYears(1))).isFalse();
    }
    @Test void publishingAcceptsExplicitlyAbsentDeadlineButRejectsInvalidProvidedDates() {
        var dto=new EventDTO();dto.setName("测试赛");dto.setCategoryId(1L);dto.setTermId(1L);dto.setIsTeam(0);
        dto.setSignupStart(event.getSignupStart());dto.setEventStart(event.getEventStart());dto.setEventEnd(event.getEventEnd());
        Result<?> result=ReflectionTestUtils.invokeMethod(controller,"validateEventDTO",dto);assertThat(result).isNull();
        dto.setSignupEnd(dto.getSignupStart());result=ReflectionTestUtils.invokeMethod(controller,"validateEventDTO",dto);assertThat(result.getCode()).isNotEqualTo(200);
    }
}
