package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;

/** Call under the event row lock; pending applications do not reserve approved places. */
@Service
public class EventSignupPolicy {
    private final SignupPersonalMapper personal;
    private final SignupTeamMapper teams;

    public EventSignupPolicy(SignupPersonalMapper personal, SignupTeamMapper teams) {
        this.personal = personal;
        this.teams = teams;
    }

    public long approvedCount(Event event) {
        return event.getIsTeam() == 1 ? teams.countApproved(event.getEventId()) : personal.countApproved(event.getEventId());
    }

    public boolean exceedsCapacity(Event event, long proposedApprovedCount) {
        return event.getMaxSignup() != null && proposedApprovedCount > event.getMaxSignup();
    }

    /** A null deadline is open-ended; a configured exact cutoff is already closed. */
    public boolean isCancellationClosed(Event event, LocalDateTime now) {
        return event.getSignupEnd() != null && !now.isBefore(event.getSignupEnd());
    }

    public String memberConflict(Long eventId, List<Long> memberIds, Long personalId, Long teamId) {
        if (memberIds.isEmpty()) return "报名成员不能为空";
        var query = new QueryWrapper<SignupPersonal>().eq("event_id", eventId)
                .in("user_id", memberIds).in("status", List.of("approved", "pending"));
        if (personalId != null) query.ne("signup_id", personalId);
        if (personal.selectCount(query) > 0) return "成员已以个人身份报名此比赛";
        if (teams.selectActiveByMembers(eventId, memberIds).stream()
                .anyMatch(team -> !java.util.Objects.equals(team.getSignupId(), teamId))) {
            return "成员已随其他队伍报名此比赛";
        }
        return null;
    }
}
