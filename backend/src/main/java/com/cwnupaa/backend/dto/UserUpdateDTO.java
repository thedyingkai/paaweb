package com.cwnupaa.backend.dto;

import com.cwnupaa.backend.entity.Users;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.cwnupaa.backend.utils.UserAccountRules;

public class UserUpdateDTO extends Users {
    private String verifyCode;
    private String oldVerifyCode;
    private String oldPassword;
    private String newEmailVerifyCode;

    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "当前密码格式错误")
    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewEmailVerifyCode() { return newEmailVerifyCode; }
    public void setNewEmailVerifyCode(String value) { newEmailVerifyCode = value; }

    private String newcoderId;
    private String luoguId;
    private String codeforcesId;
    private String atcoderId;
    private String cwnuojId;
    private String qojId;
    private String ucupId;
    private String luoguClientId;

    @Override
    @Pattern(regexp = UserAccountRules.REGEXP, message = UserAccountRules.MESSAGE)
    public String getUserAccount() {
        return super.getUserAccount();
    }

    @Override
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "密码格式错误")
    public String getPassword() {
        return super.getPassword();
    }

    @Override
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    @Email(message = "邮箱格式不正确")
    public String getEmail() {
        return super.getEmail();
    }

    @Override
    @Pattern(regexp = "^(undergraduate|postgraduate|department|off-campus)$", message = "身份类型不合法")
    public String getEduBg() { return super.getEduBg(); }

    @Override
    @Size(max = 80, message = "个人简介不能超过80字")
    public String getIntroduce() {
        return super.getIntroduce();
    }

    @Override
    @Pattern(regexp = "^\\d+$", message = "班级必须为纯数字")
    public String getClassName() {
        return super.getClassName();
    }

    @Override
    @Pattern(regexp = "^\\d{4}$", message = "年级/年份格式必须为4位数字，如：2024")
    public String getGradeYear() {
        return super.getGradeYear();
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }

    public String getOldVerifyCode() {
        return oldVerifyCode;
    }

    public void setOldVerifyCode(String oldVerifyCode) {
        this.oldVerifyCode = oldVerifyCode;
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

    public String getLuoguClientId() {
        return luoguClientId;
    }

    public void setLuoguClientId(String luoguClientId) {
        this.luoguClientId = luoguClientId;
    }
    public String getQojId() { return qojId; }
    public void setQojId(String value) { qojId = value; }
    public String getUcupId() { return ucupId; }
    public void setUcupId(String value) { ucupId = value; }
}
