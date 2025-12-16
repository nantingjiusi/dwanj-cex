package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Audit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AuditMapper {

    @Insert("INSERT INTO audits (user_id, action, target, before_text, after_text, created_at) " +
            "VALUES (#{userId}, #{action}, #{target}, #{beforeText}, #{afterText}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Audit audit);
}
