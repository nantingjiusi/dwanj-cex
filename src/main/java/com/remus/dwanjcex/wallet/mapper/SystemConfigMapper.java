package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.SystemConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemConfigMapper {

    @Insert("INSERT INTO system_configs (config_key, config_value, description) " +
            "VALUES (#{configKey}, #{configValue}, #{description})")
    void insert(SystemConfig systemConfig);

    @Select("SELECT * FROM system_configs WHERE config_key = #{configKey}")
    SystemConfig findByKey(String configKey);
}
