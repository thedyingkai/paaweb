package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.FileSystem;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public class EventVO {
    private Long eventId;
    private Long categoryId;
    private String categoryName;
    private Long termId;
    private String termName;
    private String name;

    private String type;
    private String signupMode;
    public String getSignupMode() { return signupMode; }
    public void setSignupMode(String signupMode) { this.signupMode = signupMode; }

    private Boolean externalOpen;
    private Boolean unfrozen;
    private Boolean admissionTicketOpen;
    private String admissionTicketRules;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime actualStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime actualEndTime;
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
    private String state; // 业务状态
    private Integer maxSignup;
    private String description;

    private Long signupCount;
    private List<FileSystem> attachments;

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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
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

    public Boolean getExternalOpen() {
        return externalOpen;
    }

    public void setExternalOpen(Boolean externalOpen) {
        this.externalOpen = externalOpen;
    }

    public Boolean getUnfrozen() {
        return unfrozen;
    }

    public void setUnfrozen(Boolean unfrozen) {
        this.unfrozen = unfrozen;
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

    public LocalDateTime getActualStartTime() {
        return actualStartTime;
    }

    public void setActualStartTime(LocalDateTime actualStartTime) {
        this.actualStartTime = actualStartTime;
    }

    public LocalDateTime getActualEndTime() {
        return actualEndTime;
    }

    public void setActualEndTime(LocalDateTime actualEndTime) {
        this.actualEndTime = actualEndTime;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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

    public Long getSignupCount() {
        return signupCount;
    }

    public void setSignupCount(Long signupCount) {
        this.signupCount = signupCount;
    }

    public List<FileSystem> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<FileSystem> attachments) {
        this.attachments = attachments;
    }
}
