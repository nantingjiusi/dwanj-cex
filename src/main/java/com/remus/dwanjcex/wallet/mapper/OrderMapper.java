package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.OrderEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderMapper {
    @Insert(" INSERT INTO orders(user_id, symbol, price, amount, filled, side, status, created_at) VALUES(#{userId}, #{symbol}, #{price}, #{amount}, #{filled}, #{side}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OrderEntity order);
    @Update("  UPDATE orders\n" +
            "        SET price = #{price},\n" +
            "            amount = #{amount},\n" +
            "            filled = #{filled},\n" +
            "            status = #{status},\n" +
            "            updated_at = NOW(),\n" +
            "            version = version + 1\n" +
            "        WHERE id = #{id} AND version = #{version}")
    void update(OrderEntity order); // 支持乐观锁，可在xml中加 version
    @Select(" SELECT * FROM orders WHERE id=#{id}")
    OrderEntity selectById(@Param("id") Long id);
//    List<OrderEntity> selectByUserId(Long userId);
}
