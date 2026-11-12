package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cwnupaa.backend.dto.UserPracticeStatsDTO;
import com.cwnupaa.backend.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

import com.cwnupaa.backend.dto.UserAggregatedStatsDTO;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<Users> {
    @Select("<script>" +
            "SELECT u.user_id, u.user_account, u.user_name, u.real_name, u.number_id, u.email, v.university_name " +
            "FROM users u JOIN major m ON u.major_id = m.major_id " +
            "JOIN school s ON m.school_id = s.school_id JOIN university v ON s.university_id = v.university_id " +
            "WHERE u.number_id IN <foreach collection='numbers' item='number' open='(' separator=',' close=')'>#{number}</foreach> " +
            "AND v.university_name IN <foreach collection='universities' item='university' open='(' separator=',' close=')'>#{university}</foreach> " +
            "ORDER BY u.user_id</script>")
    List<com.cwnupaa.backend.vo.BulkEmailUserIdentityVO> selectBulkEmailIdentities(
            @Param("universities") List<String> universities, @Param("numbers") List<String> numbers);

    @Update("UPDATE users SET id_photo_file_id = #{fileId}, id_photo_updated_at = #{updatedAt} WHERE user_id = #{userId}")
    int updateIdPhoto(@Param("userId") Long userId, @Param("fileId") Long fileId,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE users SET student_card_file_id = #{fileId}, account_status = 'pending' " +
            "WHERE user_id = #{userId} AND account_status IN ('register', 'restricted')")
    int submitAuth(@Param("userId") Long userId, @Param("fileId") Long fileId);

    @Update("UPDATE users SET account_status = 'restricted' WHERE user_id = #{userId} AND account_status = 'pending'")
    int revokeAuth(@Param("userId") Long userId);

    @Select("SELECT u.* FROM users u " +
            "JOIN user_account_legacy_alias a ON a.user_id = u.user_id " +
            "WHERE a.legacy_user_account = #{legacyUserAccount} LIMIT 1")
    Users selectByLegacyUserAccount(@Param("legacyUserAccount") String legacyUserAccount);

    @Select("<script>" +
            "SELECT u.user_id, u.user_name, u.real_name, u.number_id, u.class_name, u.grade_year, u.is_enrolled_stu, u.edu_bg, " +
            "m.major_name, s.school_name as college_name, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 1 THEN opr.problem_id END) as codeforces_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 2 THEN opr.problem_id END) as atcoder_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 3 THEN opr.problem_id END) as nowcoder_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 4 THEN opr.problem_id END) as luogu_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 5 THEN opr.problem_id END) as cwnu_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 6 THEN opr.problem_id END) as qoj_solved, " +
            "COUNT(opr.practice_problem_id) as total_solved " +
            "FROM users u " +
            "LEFT JOIN major m ON u.major_id = m.major_id " +
            "LEFT JOIN school s ON m.school_id = s.school_id " +
            "LEFT JOIN oj_effective_practice_record opr ON u.user_id = opr.user_id " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (u.user_name LIKE CONCAT('%', #{keyword}, '%') OR u.real_name LIKE CONCAT('%', #{keyword}, '%') OR u.number_id LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "<if test='majorId != null'> AND u.major_id = #{majorId} </if> " +
            "<if test='schoolId != null'> AND s.school_id = #{schoolId} </if> " +
            "<if test='universityId != null'> AND s.university_id = #{universityId} </if> " +
            "<if test='className != null and className != \"\"'> AND u.class_name LIKE CONCAT('%', #{className}, '%') </if> " +
            "<if test='gradeYear != null and gradeYear != \"\"'> AND u.grade_year = #{gradeYear} </if> " +
            "<if test='isEnrolledStu != null'> AND u.is_enrolled_stu = #{isEnrolledStu} </if> " +
            "<if test='eduBg != null and eduBg != \"\"'> " +
            "<choose>" +
            "<when test='eduBg == \"other\"'> AND (u.edu_bg IS NULL OR u.edu_bg NOT IN ('undergraduate', 'postgraduate')) </when>" +
            "<otherwise> AND u.edu_bg = #{eduBg} </otherwise>" +
            "</choose>" +
            "</if> " +
            "GROUP BY u.user_id, u.user_name, u.real_name, u.number_id, u.class_name, u.grade_year, u.is_enrolled_stu, u.edu_bg, m.major_name, s.school_name " +
            "</script>")
    IPage<UserPracticeStatsDTO> selectUserPracticeStats(IPage<UserPracticeStatsDTO> page, 
                                                        @Param("keyword") String keyword, 
                                                        @Param("majorId") Long majorId, 
                                                        @Param("schoolId") Long schoolId,
                                                        @Param("universityId") Long universityId,
                                                        @Param("className") String className,
                                                        @Param("gradeYear") String gradeYear,
                                                        @Param("isEnrolledStu") Integer isEnrolledStu,
                                                        @Param("eduBg") String eduBg);

    @Select("<script>" +
            "SELECT u.user_id, u.user_name, u.real_name, u.number_id, u.class_name, u.grade_year, u.is_enrolled_stu, u.edu_bg, " +
            "m.major_name, s.school_name as college_name, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 1 THEN opr.problem_id END) as codeforces_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 2 THEN opr.problem_id END) as atcoder_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 3 THEN opr.problem_id END) as nowcoder_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 4 THEN opr.problem_id END) as luogu_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 5 THEN opr.problem_id END) as cwnu_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.source_id = 6 THEN opr.problem_id END) as qoj_solved, " +
            "COUNT(opr.practice_problem_id) as total_solved " +
            "FROM users u " +
            "LEFT JOIN major m ON u.major_id = m.major_id " +
            "LEFT JOIN school s ON m.school_id = s.school_id " +
            "LEFT JOIN oj_effective_practice_record opr ON u.user_id = opr.user_id " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (u.user_name LIKE CONCAT('%', #{keyword}, '%') OR u.real_name LIKE CONCAT('%', #{keyword}, '%') OR u.number_id LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "<if test='majorId != null'> AND u.major_id = #{majorId} </if> " +
            "<if test='schoolId != null'> AND s.school_id = #{schoolId} </if> " +
            "<if test='universityId != null'> AND s.university_id = #{universityId} </if> " +
            "<if test='className != null and className != \"\"'> AND u.class_name LIKE CONCAT('%', #{className}, '%') </if> " +
            "<if test='gradeYear != null and gradeYear != \"\"'> AND u.grade_year = #{gradeYear} </if> " +
            "<if test='isEnrolledStu != null'> AND u.is_enrolled_stu = #{isEnrolledStu} </if> " +
            "<if test='eduBg != null and eduBg != \"\"'> " +
            "<choose>" +
            "<when test='eduBg == \"other\"'> AND (u.edu_bg IS NULL OR u.edu_bg NOT IN ('undergraduate', 'postgraduate')) </when>" +
            "<otherwise> AND u.edu_bg = #{eduBg} </otherwise>" +
            "</choose>" +
            "</if> " +
            "GROUP BY u.user_id, u.user_name, u.real_name, u.number_id, u.class_name, u.grade_year, u.is_enrolled_stu, u.edu_bg, m.major_name, s.school_name " +
            "</script>")
    List<UserPracticeStatsDTO> selectUserPracticeStatsList(@Param("keyword") String keyword, 
                                                        @Param("majorId") Long majorId, 
                                                        @Param("schoolId") Long schoolId,
                                                        @Param("universityId") Long universityId,
                                                        @Param("className") String className,
                                                        @Param("gradeYear") String gradeYear,
                                                        @Param("isEnrolledStu") Integer isEnrolledStu,
                                                        @Param("eduBg") String eduBg);

    @Select("<script>" +
            "SELECT " +
            "COUNT(DISTINCT u.user_id) as user_count, " +
            "COUNT(DISTINCT CASE WHEN opr.problem_id IS NOT NULL THEN CONCAT(u.user_id, '-', opr.source_id, '-', opr.problem_id) END) as total_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.ac_time >= (NOW() - INTERVAL '30 day') AND opr.problem_id IS NOT NULL THEN CONCAT(u.user_id, '-', opr.source_id, '-', opr.problem_id) END) as last_30_days_solved, " +
            "COUNT(DISTINCT CASE WHEN opr.ac_time >= (NOW() - INTERVAL '7 day') AND opr.problem_id IS NOT NULL THEN CONCAT(u.user_id, '-', opr.source_id, '-', opr.problem_id) END) as last_7_days_solved " +
            "FROM users u " +
            "LEFT JOIN major m ON u.major_id = m.major_id " +
            "LEFT JOIN school s ON m.school_id = s.school_id " +
            "LEFT JOIN oj_effective_practice_record opr ON u.user_id = opr.user_id " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (u.user_name LIKE CONCAT('%', #{keyword}, '%') OR u.real_name LIKE CONCAT('%', #{keyword}, '%') OR u.number_id LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "<if test='majorId != null'> AND u.major_id = #{majorId} </if> " +
            "<if test='schoolId != null'> AND s.school_id = #{schoolId} </if> " +
            "<if test='universityId != null'> AND s.university_id = #{universityId} </if> " +
            "<if test='className != null and className != \"\"'> AND u.class_name LIKE CONCAT('%', #{className}, '%') </if> " +
            "<if test='gradeYear != null and gradeYear != \"\"'> AND u.grade_year = #{gradeYear} </if> " +
            "<if test='isEnrolledStu != null'> AND u.is_enrolled_stu = #{isEnrolledStu} </if> " +
            "<if test='eduBg != null and eduBg != \"\"'> " +
            "<choose>" +
            "<when test='eduBg == \"other\"'> AND (u.edu_bg IS NULL OR u.edu_bg NOT IN ('undergraduate', 'postgraduate')) </when>" +
            "<otherwise> AND u.edu_bg = #{eduBg} </otherwise>" +
            "</choose>" +
            "</if> " +
            "</script>")
    UserAggregatedStatsDTO selectUserAggregatedStats(@Param("keyword") String keyword, 
                                                  @Param("majorId") Long majorId, 
                                                  @Param("schoolId") Long schoolId,
                                                  @Param("universityId") Long universityId,
                                                  @Param("className") String className,
                                                  @Param("gradeYear") String gradeYear,
                                                  @Param("isEnrolledStu") Integer isEnrolledStu,
                                                  @Param("eduBg") String eduBg);
}
