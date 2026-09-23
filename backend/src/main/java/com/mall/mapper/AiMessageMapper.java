package com.mall.mapper;

import com.mall.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AiMessageMapper {
    AiMessage selectById(Long id);
    List<AiMessage> selectBySessionId(Long sessionId);
    int insert(AiMessage message);
    int deleteBySessionId(Long sessionId);
}
