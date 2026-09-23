package com.mall.mapper;

import com.mall.entity.AiSession;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AiSessionMapper {
    AiSession selectById(Long id);
    List<AiSession> selectByUserId(Long userId);
    int insert(AiSession session);
    int update(AiSession session);
    int deleteById(Long id);
    int deleteByUserId(Long userId);
}
