package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@TableName("event")
public class Event {
    @TableId(type = IdType.AUTO)
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime signupEnd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime eventStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime eventEnd;

    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer maxSignup;
    private String description;


    @TableField("is_external_open")
    private Boolean externalOpen;

    @TableField("is_unfrozen")
    private Boolean unfrozen;

    @TableField("admission_ticket_open")
    private Boolean admissionTicketOpen;

    @TableField("admission_ticket_rules")
    private String admissionTicketRules;

    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private String medalConfig;

    private LocalDateTime freezeTime;

    public LocalDateTime getFreezeTime() {
        return freezeTime;
    }

    public void setFreezeTime(LocalDateTime freezeTime) {
        this.freezeTime = freezeTime;
    }

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

    public String getMedalConfig() {
        return medalConfig;
    }

    public void setMedalConfig(String medalConfig) {
        this.medalConfig = medalConfig;
    }

    public Long getCreatorId() {
        return null;
    }

    public void setCreatorId(Long creatorId) {
    }

    public LocalDateTime getCreateTime() {
        return null;
    }

    public void setCreateTime(LocalDateTime t) {
    }

    public LocalDateTime getUpdateTime() {
        return null;
    }

    public void setUpdateTime(LocalDateTime t) {
    }
}
