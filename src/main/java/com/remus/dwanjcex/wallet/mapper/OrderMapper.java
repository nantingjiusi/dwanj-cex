package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.OrderEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO orders(user_id, market_symbol, side, type, price, quantity, filled, status, created_at, updated_at) " +
            "VALUES(#{userId}, #{marketSymbol}, #{side}, #{type}, #{price}, #{quantity}, #{filled}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OrderEntity order);

    @Update("<script>" +
            "UPDATE orders SET " +
            "filled = CASE id " +
            "<foreach collection='list' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.filled} " +
            "</foreach>" +
            "END, " +
            "status = CASE id " +
            "<foreach collection='list' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.status} " +
            "</foreach>" +
            "END, " +
            "updated_at = NOW() " +
            "WHERE id IN " +
            "<foreach collection='list' item='item' open='(' separator=',' close=')'>" +
            "#{item.id}" +
            "</foreach>" +
            "</script>")
    void updateBatch(List<OrderEntity> orders);

    @Select("SELECT * FROM orders WHERE id=#{id}")
    OrderEntity selectById(@Param("id") Long id);

    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY id DESC")
    List<OrderEntity> selectByUserId(@Param("userId") Long userId);
}
