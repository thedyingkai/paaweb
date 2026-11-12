package com.cwnupaa.backend.dto;

public class OjAccountUpdateDTO {
    private Long userId;
    private String codeforcesId;
    private String atcoderId;
    private String luoguId;
    private String newcoderId;
    private String cwnuojId;
    private String qojId;
    private String ucupId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getLuoguId() {
        return luoguId;
    }

    public void setLuoguId(String luoguId) {
        this.luoguId = luoguId;
    }

    public String getNewcoderId() {
        return newcoderId;
    }

    public void setNewcoderId(String newcoderId) {
        this.newcoderId = newcoderId;
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