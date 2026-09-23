package com.mall.service;

import com.mall.entity.AiMessage;
import java.util.List;

public interface AiMessageService {
    AiMessage saveMessage(Long sessionId, String role, String content);
    List<AiMessage> getSessionMessages(Long sessionId);
}
