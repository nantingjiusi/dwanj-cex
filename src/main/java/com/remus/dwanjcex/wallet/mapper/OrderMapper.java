package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.OrderEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

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
    void update(OrderEntity order);

    @Select(" SELECT * FROM orders WHERE id=#{id}")
    OrderEntity selectById(@Param("id") Long id);

    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY id DESC")
    List<OrderEntity> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询所有出现过的交易对名称。
     * @return 交易对名称列表
     */
    @Select("SELECT DISTINCT symbol FROM orders")
    List<String> findAllSymbols();
}
