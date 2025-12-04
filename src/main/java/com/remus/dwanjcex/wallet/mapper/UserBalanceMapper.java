package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.UserBalance;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserBalanceMapper {
    @Select("  SELECT * FROM user_balance WHERE user_id = #{userId} AND asset = #{asset}")
    UserBalance selectByUserIdAndAsset(@Param("userId") Long userId, @Param("asset") String asset);

    @Insert("INSERT INTO user_balance(user_id, asset, available, frozen)\n" +
            "        VALUES(#{userId}, #{asset}, #{available}, #{frozen})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(UserBalance userBalance);

    @Update("  UPDATE user_balance\n" +
            "        SET available=#{available}, frozen=#{frozen}\n" +
            "        WHERE id=#{id}")
    void update(UserBalance userBalance);
    @Select("SELECT * FROM user_balance WHERE user_id=#{userId}")
    List<UserBalance> selectByUserId(@Param("userId") Long userId);
}
