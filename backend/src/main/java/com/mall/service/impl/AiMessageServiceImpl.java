package com.mall.service.impl;

import com.mall.entity.AiMessage;
import com.mall.mapper.AiMessageMapper;
import com.mall.service.AiMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class AiMessageServiceImpl implements AiMessageService {

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Override
    public AiMessage saveMessage(Long sessionId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(new Date());
        aiMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<AiMessage> getSessionMessages(Long sessionId) {
        return aiMessageMapper.selectBySessionId(sessionId);
    }
}
