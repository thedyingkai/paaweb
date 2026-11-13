package com.cwnupaa.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public class EventDTO {
    private Long eventId;
    private Long categoryId;
    private Long termId;
    private String name;

    private String type;
    private String signupMode;
    public String getSignupMode() { return signupMode; }
    public void setSignupMode(String signupMode) { this.signupMode = signupMode; }


    private Integer isTeam;
    private Integer minMember;
    private Integer maxMember;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime signupStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime signupEnd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime eventStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime eventEnd;

    private Integer status;
    private Integer maxSignup;
    private String description;
    private List<Long> attachmentIds;
    private Long operatorId;
    private Boolean notifyParticipants;
    private Boolean admissionTicketOpen;
    private String admissionTicketRules;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getIsTeam() {
        return isTeam;
    }

    public void setIsTeam(Integer isTeam) {
        this.isTeam = isTeam;
    }

    public Integer getMinMember() {
        return minMember;
    }

    public void setMinMember(Integer minMember) {
        this.minMember = minMember;
    }

    public Integer getMaxMember() {
        return maxMember;
    }

    public void setMaxMember(Integer maxMember) {
        this.maxMember = maxMember;
    }

    public LocalDateTime getSignupStart() {
        return signupStart;
    }

    public void setSignupStart(LocalDateTime signupStart) {
        this.signupStart = signupStart;
    }

    public LocalDateTime getSignupEnd() {
        return signupEnd;
    }

    public void setSignupEnd(LocalDateTime signupEnd) {
        this.signupEnd = signupEnd;
    }

    public LocalDateTime getEventStart() {
        return eventStart;
    }

    public void setEventStart(LocalDateTime eventStart) {
        this.eventStart = eventStart;
    }

    public LocalDateTime getEventEnd() {
        return eventEnd;
    }

    public void setEventEnd(LocalDateTime eventEnd) {
        this.eventEnd = eventEnd;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getMaxSignup() {
        return maxSignup;
    }

    public void setMaxSignup(Integer maxSignup) {
        this.maxSignup = maxSignup;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Long> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Boolean getNotifyParticipants() {
        return notifyParticipants;
    }

    public void setNotifyParticipants(Boolean notifyParticipants) {
        this.notifyParticipants = notifyParticipants;
    }

    public Boolean getAdmissionTicketOpen() {
        return admissionTicketOpen;
    }

    public void setAdmissionTicketOpen(Boolean admissionTicketOpen) {
        this.admissionTicketOpen = admissionTicketOpen;
    }

    public String getAdmissionTicketRules() {
        return admissionTicketRules;
    }

    public void setAdmissionTicketRules(String admissionTicketRules) {
        this.admissionTicketRules = admissionTicketRules;
    }
}
