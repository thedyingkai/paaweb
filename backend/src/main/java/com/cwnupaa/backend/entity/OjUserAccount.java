package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("oj_user_account")
public class OjUserAccount {
    @TableId
    private Long userId;
    private String newcoderId;
    private String luoguId;
    private String luoguClientId;
    private String codeforcesId;
    private String atcoderId;
    private String cwnuojId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String qojCookie;
    private String qojId;
    private String ucupId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getLuoguClientId() {
        return luoguClientId;
    }

    public void setLuoguClientId(String luoguClientId) {
        this.luoguClientId = luoguClientId;
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
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getQojCookie() { return qojCookie; }
    public void setQojCookie(String value) { qojCookie = value; }

    public String getQojId() { return qojId; }
    public void setQojId(String value) { qojId = value; }
    public String getUcupId() { return ucupId; }
    public void setUcupId(String value) { ucupId = value; }
}