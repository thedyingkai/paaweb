package com.cwnupaa.backend.vo;

public class AssociationMemberVO {
    private Long memberId;
    private String term;
    private String department;
    private Long userId;
    private String realName;
    private String schoolName;
    private String numberId;
    private Long avatarFileId;
    private String avatarUrl;
    private String position;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;

    public java.time.LocalDate getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDate value) { startDate = value; }
    public java.time.LocalDate getEndDate() { return endDate; }
    public void setEndDate(java.time.LocalDate value) { endDate = value; }
    private String introduction;
    private Integer sortOrder;
    private String displayPosition;
    private String positionStatus;
    public String getDisplayPosition() { return displayPosition; }
    public void setDisplayPosition(String value) { displayPosition = value; }
    public String getPositionStatus() { return positionStatus; }
    public void setPositionStatus(String value) { positionStatus = value; }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getNumberId() {
        return numberId;
    }

    public void setNumberId(String numberId) {
        this.numberId = numberId;
    }

    public Long getAvatarFileId() {
        return avatarFileId;
    }

    public void setAvatarFileId(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
