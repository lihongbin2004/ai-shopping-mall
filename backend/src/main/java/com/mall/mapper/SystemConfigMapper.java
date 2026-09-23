package com.mall.mapper;

import com.mall.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SystemConfigMapper {
    List<SystemConfig> selectAll();
    SystemConfig selectByKey(String key);
    int insert(SystemConfig config);
    int update(SystemConfig config);
    int upsert(SystemConfig config);
}
