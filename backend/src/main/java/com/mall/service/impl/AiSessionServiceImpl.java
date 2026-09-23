package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.AiSession;
import com.mall.mapper.AiSessionMapper;
import com.mall.mapper.AiMessageMapper;
import com.mall.service.AiSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class AiSessionServiceImpl implements AiSessionService {

    @Autowired
    private AiSessionMapper aiSessionMapper;

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Override
    public AiSession createSession(Long userId, String title) {
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setTitle(title == null ? "新会话" : title);
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());
        aiSessionMapper.insert(session);
        return session;
    }

    @Override
    public List<AiSession> getUserSessions(Long userId) {
        return aiSessionMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        aiSessionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserSessions(Long userId) {
        // 先删除该用户所有 session 关联的 messages，再删 sessions
        List<AiSession> sessions = aiSessionMapper.selectByUserId(userId);
        if (sessions != null) {
            for (AiSession s : sessions) {
                try { aiMessageMapper.deleteBySessionId(s.getId()); } catch (Exception ignore) {}
            }
        }
        aiSessionMapper.deleteByUserId(userId);
    }
}
