package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    // 根据ID查询用户
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(Long id);

    // 根据用户名查询用户
    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(String username);

    // 查询所有用户
    @Select("SELECT * FROM user")
    List<User> selectAll();

    // 插入新用户
    @Insert("INSERT INTO user(username, password, email, phone, status, created_at, updated_at) " +
            "VALUES(#{username}, #{password}, #{email}, #{phone}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    // 更新用户信息
    @Update("UPDATE user SET username=#{username}, password=#{password}, email=#{email}, phone=#{phone}, " +
            "status=#{status}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(User user);

    // 删除用户
    @Delete("DELETE FROM user WHERE id=#{id}")
    int delete(Long id);
}