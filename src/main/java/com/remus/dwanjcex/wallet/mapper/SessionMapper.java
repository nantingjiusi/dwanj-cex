package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Session;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface SessionMapper {

    @Insert("INSERT INTO sessions (user_id, session_token, ip, user_agent, created_at, expired_at) " +
            "VALUES (#{userId}, #{sessionToken}, #{ip}, #{userAgent}, NOW(), #{expiredAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Session session);
}
