package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("INSERT INTO users(username, email, password_hash, status, created_at, updated_at) " +
            "VALUES(#{username}, #{email}, #{passwordHash}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE users SET username=#{username}, email=#{email}, password_hash=#{passwordHash}, " +
            "status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int update(User user);
}
