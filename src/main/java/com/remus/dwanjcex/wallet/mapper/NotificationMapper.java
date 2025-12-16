package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notifications (user_id, title, content, type, is_read, created_at) " +
            "VALUES (#{userId}, #{title}, #{content}, #{type}, #{isRead}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Notification notification);

    @Select("SELECT * FROM notifications WHERE user_id = #{userId} ORDER BY id DESC")
    List<Notification> findByUserId(Long userId);
}
