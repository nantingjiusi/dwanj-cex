package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.UserProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper {

    @Insert("INSERT INTO user_profiles (user_id, full_name, country, phone, kyc_level, created_at, updated_at) " +
            "VALUES (#{userId}, #{fullName}, #{country}, #{phone}, #{kycLevel}, NOW(), NOW())")
    void insert(UserProfile userProfile);

    @Select("SELECT * FROM user_profiles WHERE user_id = #{userId}")
    UserProfile findByUserId(Long userId);

    @Update("UPDATE user_profiles SET full_name=#{fullName}, country=#{country}, phone=#{phone}, " +
            "kyc_level=#{kycLevel}, updated_at=NOW() WHERE user_id=#{userId}")
    int update(UserProfile userProfile);
}
