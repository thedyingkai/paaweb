package com.cwnupaa.backend.dto;

import lombok.Data;

@Data
public class UserPracticeStatsDTO {
    private Long userId;
    private String userName;
    private String realName;
    private String numberId;
    private String collegeName;
    private String majorName;
    private String className;
    private Integer luoguSolved;
    private Integer nowcoderSolved;
    private Integer codeforcesSolved;
    private Integer atcoderSolved;
    private Integer cwnuSolved;
    private Integer qojSolved;
    private Integer totalSolved;
    private Integer isEnrolledStu;
    private String gradeYear;
    private String eduBg;
}
