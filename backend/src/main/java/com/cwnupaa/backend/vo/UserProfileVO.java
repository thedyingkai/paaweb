package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.Users;

public class UserProfileVO extends Users {
    private String avatarUrl;
    private String backgroundUrl;
    private String idPhotoUrl;
    private String studentCardUrl;
    private Long universityId;
    private Long schoolId;
    private String universityName;
    private String schoolName;
    private String majorName;
    private Integer rating;
    private Integer rank;
    private String newcoderId;
    private String luoguId;
    private String codeforcesId;
    private String atcoderId;
    private String cwnuojId;
    private String qojId;
    private String ucupId;
    private String luoguClientId;
    private String token;
    private java.util.List<String> permissions;

    public java.util.List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(java.util.List<String> permissions) {
        this.permissions = permissions;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getLuoguClientId() {
        return luoguClientId;
    }

    public void setLuoguClientId(String luoguClientId) {
        this.luoguClientId = luoguClientId;
    }

    public String getStudentCardUrl() {
        return studentCardUrl;
    }

    public void setStudentCardUrl(String studentCardUrl) {
        this.studentCardUrl = studentCardUrl;
    }

    public Long getUniversityId() {
        return universityId;
    }

    public void setUniversityId(Long universityId) {
        this.universityId = universityId;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
    }

    public String getIdPhotoUrl() {
        return idPhotoUrl;
    }

    public void setIdPhotoUrl(String idPhotoUrl) {
        this.idPhotoUrl = idPhotoUrl;
    }

    public String getUniversityName() {
        return universityName;
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getMajorName() {
        return majorName;
    }

    public void setMajorName(String majorName) {
        this.majorName = majorName;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public String getNewcoderId() {
        return newcoderId;
    }

    public void setNewcoderId(String newcoderId) {
        this.newcoderId = newcoderId;
    }

    public String getLuoguId() {
        return luoguId;
    }

    public void setLuoguId(String luoguId) {
        this.luoguId = luoguId;
    }

    public String getCodeforcesId() {
        return codeforcesId;
    }

    public void setCodeforcesId(String codeforcesId) {
        this.codeforcesId = codeforcesId;
    }

    public String getAtcoderId() {
        return atcoderId;
    }

    public void setAtcoderId(String atcoderId) {
        this.atcoderId = atcoderId;
    }

    public String getCwnuojId() {
        return cwnuojId;
    }

    public void setCwnuojId(String cwnuojId) {
        this.cwnuojId = cwnuojId;
    }
    public String getQojId() { return qojId; }
    public void setQojId(String value) { qojId = value; }
    public String getUcupId() { return ucupId; }
    public void setUcupId(String value) { ucupId = value; }
}
