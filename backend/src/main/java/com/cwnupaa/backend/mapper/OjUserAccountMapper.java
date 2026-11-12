package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cwnupaa.backend.entity.OjUserAccount;
import com.cwnupaa.backend.vo.UserOjAccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OjUserAccountMapper extends BaseMapper<OjUserAccount> {
    @Select("SELECT * FROM oj_user_account WHERE user_id = #{userId} FOR UPDATE")
    OjUserAccount selectForTrackerUpdate(@Param("userId") Long userId);

    @Select("<script>" + "SELECT u.user_id, u.user_name, u.real_name, u.number_id, " + "o.codeforces_id, o.atcoder_id, o.luogu_id, o.newcoder_id, o.cwnuoj_id, o.qoj_id, o.ucup_id " + "FROM users u " + "LEFT JOIN oj_user_account o ON u.user_id = o.user_id " + "WHERE 1=1 " + "<if test='keyword != null and keyword != \"\"'>" + "AND (u.user_name LIKE CONCAT('%',#{keyword},'%') " + "OR u.real_name LIKE CONCAT('%',#{keyword},'%') " + "OR u.number_id LIKE CONCAT('%',#{keyword},'%') " + "OR u.email LIKE CONCAT('%',#{keyword},'%') " + "OR u.phone LIKE CONCAT('%',#{keyword},'%')) " + "</if>" + "ORDER BY u.user_id ASC" + "</script>")
    IPage<UserOjAccountVO> selectUserOjAccounts(IPage<UserOjAccountVO> page, @Param("keyword") String keyword);
}