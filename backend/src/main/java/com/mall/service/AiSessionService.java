package com.mall.service;

import com.mall.entity.AiSession;
import java.util.List;

public interface AiSessionService {
    AiSession createSession(Long userId, String title);
    List<AiSession> getUserSessions(Long userId);
    void deleteSession(Long id);
    void deleteUserSessions(Long userId);
}
