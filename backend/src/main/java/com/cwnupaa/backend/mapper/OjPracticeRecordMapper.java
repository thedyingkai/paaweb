package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.OjPracticeRecord;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OjPracticeRecordMapper extends BaseMapper<OjPracticeRecord> {
    @Select("select practice_problem_id, problem_id, problem_difficulty from oj_practice_record where user_id = #{userId} and source_id = #{sourceId}")
    List<OjPracticeRecord> selectSolvedProblems(@Param("userId") Long userId, @Param("sourceId") Long sourceId);

    @Select("select problem_id from oj_practice_record where user_id = #{userId} and source_id = #{sourceId}")
    List<String> selectSolvedProblemIds(@Param("userId") Long userId, @Param("sourceId") Long sourceId);

    @Select("select max(ac_time) from oj_practice_record where user_id = #{userId} and source_id = #{sourceId}")
    LocalDateTime selectMaxAcTime(@Param("userId") Long userId, @Param("sourceId") Long sourceId);

    @MapKey("user_id")
    @Select("SELECT user_id, COUNT(DISTINCT problem_id) as solved_count " + "FROM oj_practice_record " + "WHERE ac_time >= #{start} AND ac_time <= #{end} " + "GROUP BY user_id")
    List<Map<String, Object>> selectDailySolvedStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT user_id, TO_CHAR(ac_time, 'YYYY-MM-DD') as date_str, COUNT(DISTINCT (source_id,problem_id)) as solved_count " + "FROM oj_practice_record " + "WHERE ac_time > #{minTime} AND ac_time <= #{maxTime} " + "GROUP BY user_id, TO_CHAR(ac_time, 'YYYY-MM-DD')")
    List<Map<String, Object>> selectAllUserDailyStatsAfter(@Param("minTime") LocalDateTime minTime,@Param("maxTime") LocalDateTime maxTime);

    @Select("SELECT count(1) FROM oj_practice_record opr LEFT JOIN users u ON opr.user_id = u.user_id WHERE u.is_enrolled_stu = 1")
    Long countSolvedByEnrolledUsers();
}
