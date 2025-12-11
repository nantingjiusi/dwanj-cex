package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.OrderEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO orders(user_id, symbol, type, side, price, amount, quote_amount, filled, quote_filled, status, created_at, updated_at, version) " +
            "VALUES(#{userId}, #{symbol}, #{type}, #{side}, #{price}, #{amount}, #{quoteAmount}, #{filled}, #{quoteFilled}, #{status}, #{createdAt}, #{updatedAt}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OrderEntity order);

    @Update("UPDATE orders SET " +
            "price = #{price}, " +
            "amount = #{amount}, " +
            "quote_amount = #{quoteAmount}, " +
            "filled = #{filled}, " +
            "quote_filled = #{quoteFilled}, " +
            "status = #{status}, " +
            "updated_at = NOW(), " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    void update(OrderEntity order);

    /**
     * 批量更新订单状态和成交信息
     */
    @Update("<script>" +
            "UPDATE orders SET " +
            "filled = CASE id " +
            "<foreach collection='list' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.filled} " +
            "</foreach>" +
            "END, " +
            "quote_filled = CASE id " +
            "<foreach collection='list' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.quoteFilled} " +
            "</foreach>" +
            "END, " +
            "status = CASE id " +
            "<foreach collection='list' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.status} " +
            "</foreach>" +
            "END, " +
            "price = CASE id " +
            "<foreach collection='list' item='item' separator=' '>" +
            "WHEN #{item.id} THEN #{item.price} " +
            "</foreach>" +
            "END, " +
            "updated_at = NOW(), " +
            "version = version + 1 " +
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

    @Select("SELECT DISTINCT symbol FROM orders")
    List<String> findAllSymbols();
}
