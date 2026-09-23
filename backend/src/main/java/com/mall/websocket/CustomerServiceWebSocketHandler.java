package com.mall.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mall.entity.ChatMessage;
import com.mall.entity.ChatSession;
import com.mall.entity.User;
import com.mall.mapper.ChatMessageMapper;
import com.mall.mapper.ChatSessionMapper;
import com.mall.mapper.UserMapper;
import com.mall.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 在线客服 WebSocket Handler
 * 客户侧和客服侧共用同一个 endpoint，通过 query 参数区分角色
 *
 * 连接方式：ws://host/ws/kf?token=xxx&role=0|1
 *   - role=0 → 普通客户（senderType=0）
 *   - role=1 → 管理员/客服（senderType=1）
 */
@Component
public class CustomerServiceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceWebSocketHandler.class);

    /** userId → 客户 WebSocket 会话 */
    private static final ConcurrentMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    /** adminId → 客服主会话 */
    private static final ConcurrentMap<Long, WebSocketSession> adminSessions = new ConcurrentHashMap<>();
    /** 每个 adminId 对应的全部在线 session（一个客服可能多标签页） */
    private static final ConcurrentMap<Long, ConcurrentMap<String, WebSocketSession>> adminMultiSessions = new ConcurrentHashMap<>();

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /** 连接鉴权 + 注册 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        Integer role = extractRole(session);
        if (userId == null || role == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token or role"));
            return;
        }

        String token = extractToken(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }

        String roleName = jwtUtil.parseRole(token);
        User user = userMapper.selectById(userId);
        if (user == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("user not found"));
            return;
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(roleName);
        if (role == 1 && !isAdmin) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("admin role required"));
            return;
        }

        session.getAttributes().put("userId", userId);
        session.getAttributes().put("role", role);
        if (role == 0) {
            userSessions.put(userId, session);
            log.info("[KF] user#{} connected", userId);
        } else {
            adminSessions.put(userId, session);
            adminMultiSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(session.getId(), session);
            log.info("[KF] admin#{} connected", userId);
        }

        JSONObject ack = new JSONObject();
        ack.put("type", "connected");
        ack.put("role", role == 0 ? "user" : "admin");
        ack.put("userId", userId);
        session.sendMessage(new TextMessage(ack.toJSONString()));
    }

    /** 消息路由 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        Integer role = (Integer) session.getAttributes().get("role");
        if (userId == null || role == null) return;

        String payload = message.getPayload();
        JSONObject msg;
        try {
            msg = JSON.parseObject(payload);
        } catch (Exception e) {
            return;
        }

        String type = msg.getString("type");
        if (type == null) return;

        switch (type) {
            case "message":      handleChatMessage(session, userId, role, msg); break;
            case "typing":       handleTyping(userId, role, msg); break;
            case "read":         handleRead(userId, role, msg); break;
            case "close":        handleClose(userId, role, msg); break;
            default: break;
        }
    }

    /** 断开清理 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        Integer role = (Integer) session.getAttributes().get("role");
        if (userId == null || role == null) return;

        if (role == 0) {
            userSessions.remove(userId, session);
            log.info("[KF] user#{} disconnected", userId);
        } else {
            ConcurrentMap<String, WebSocketSession> sessions = adminMultiSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    adminMultiSessions.remove(userId);
                    adminSessions.remove(userId, session);
                }
            }
            log.info("[KF] admin#{} disconnected", userId);
        }
    }

    // ========== 消息处理 ==========

    private void handleChatMessage(WebSocketSession session, Long userId, Integer role, JSONObject msg) {
        String content = msg.getString("content");
        if (content == null || content.trim().isEmpty()) return;
        content = content.trim();
        if (content.length() > 2000) content = content.substring(0, 2000);

        try {
            ChatSession chatSession;
            if (role == 0) {
                chatSession = sessionMapper.selectActiveByUserId(userId);
                if (chatSession == null) {
                    chatSession = new ChatSession();
                    chatSession.setUserId(userId);
                    chatSession.setStatus(0);
                    chatSession.setCreateTime(new Date());
                    sessionMapper.insert(chatSession);
                }
            } else {
                Long sessionId = msg.getLong("sessionId");
                if (sessionId == null) return;
                chatSession = sessionMapper.selectById(sessionId);
                if (chatSession == null) return;
                if (chatSession.getAdminId() == null) {
                    sessionMapper.assignAdmin(chatSession.getId(), userId);
                    chatSession.setAdminId(userId);
                }
            }

            Long sessionId = chatSession.getId();
            int senderType = role;

            ChatMessage chatMsg = new ChatMessage();
            chatMsg.setSessionId(sessionId);
            chatMsg.setSenderType(senderType);
            chatMsg.setSenderId(userId);
            chatMsg.setContent(content);
            chatMsg.setCreateTime(new Date());
            messageMapper.insert(chatMsg);

            sessionMapper.updateLastMessage(sessionId, content, new Date());

            JSONObject broadcast = new JSONObject();
            broadcast.put("type", "message");
            broadcast.put("sessionId", sessionId);
            broadcast.put("senderType", senderType);
            broadcast.put("senderId", userId);
            broadcast.put("content", content);
            broadcast.put("createTime", new Date().getTime());

            if (role == 0) {
                Long targetAdmin = chatSession.getAdminId();
                if (targetAdmin != null && adminSessions.containsKey(targetAdmin)) {
                    sendToAdmin(targetAdmin, broadcast.toJSONString());
                    sessionMapper.incAdminUnread(sessionId);
                } else {
                    // 未分配 → 广播所有在线客服
                    for (Long aid : adminSessions.keySet()) {
                        sendToAdmin(aid, broadcast.toJSONString());
                    }
                }
                session.sendMessage(new TextMessage(broadcast.toJSONString()));
            } else {
                Long targetUser = chatSession.getUserId();
                if (userSessions.containsKey(targetUser)) {
                    sendToUser(targetUser, broadcast.toJSONString());
                    sessionMapper.incUserUnread(sessionId);
                }
                session.sendMessage(new TextMessage(broadcast.toJSONString()));
            }
        } catch (Exception e) {
            log.error("[KF] handleChatMessage error", e);
        }
    }

    private void handleTyping(Long userId, Integer role, JSONObject msg) {
        try {
            Long sessionId = msg.getLong("sessionId");
            JSONObject typing = new JSONObject();
            typing.put("type", "typing");
            typing.put("sessionId", sessionId);
            typing.put("userId", userId);
            typing.put("role", role);
            if (role == 0) {
                ChatSession s = sessionMapper.selectById(sessionId);
                if (s != null && s.getAdminId() != null) sendToAdmin(s.getAdminId(), typing.toJSONString());
            } else {
                ChatSession s = sessionMapper.selectById(sessionId);
                if (s != null) sendToUser(s.getUserId(), typing.toJSONString());
            }
        } catch (Exception ignored) {}
    }

    private void handleRead(Long userId, Integer role, JSONObject msg) {
        try {
            Long sessionId = msg.getLong("sessionId");
            if (role == 0) {
                sessionMapper.clearUserUnread(sessionId);
            } else {
                sessionMapper.clearAdminUnread(sessionId);
            }
            JSONObject read = new JSONObject();
            read.put("type", "read");
            read.put("sessionId", sessionId);
            read.put("role", role);
            if (role == 0) {
                ChatSession s = sessionMapper.selectById(sessionId);
                if (s != null && s.getAdminId() != null) sendToAdmin(s.getAdminId(), read.toJSONString());
            } else {
                ChatSession s = sessionMapper.selectById(sessionId);
                if (s != null) sendToUser(s.getUserId(), read.toJSONString());
            }
        } catch (Exception ignored) {}
    }

    private void handleClose(Long userId, Integer role, JSONObject msg) {
        try {
            Long sessionId = msg.getLong("sessionId");
            sessionMapper.closeSession(sessionId);
            JSONObject close = new JSONObject();
            close.put("type", "close");
            close.put("sessionId", sessionId);
            if (role == 0) {
                ChatSession s = sessionMapper.selectById(sessionId);
                if (s != null && s.getAdminId() != null) sendToAdmin(s.getAdminId(), close.toJSONString());
            } else {
                ChatSession s = sessionMapper.selectById(sessionId);
                if (s != null) sendToUser(s.getUserId(), close.toJSONString());
            }
        } catch (Exception ignored) {}
    }

    // ========== 静态发送方法 ==========

    public static void sendToUser(Long userId, String message) {
        WebSocketSession s = userSessions.get(userId);
        if (s != null && s.isOpen()) {
            try { s.sendMessage(new TextMessage(message)); } catch (IOException ignored) {}
        }
    }

    public static void sendToAdmin(Long adminId, String message) {
        ConcurrentMap<String, WebSocketSession> sessions = adminMultiSessions.get(adminId);
        if (sessions != null) {
            for (WebSocketSession s : sessions.values()) {
                if (s != null && s.isOpen()) {
                    try { s.sendMessage(new TextMessage(message)); } catch (IOException ignored) {}
                }
            }
        }
    }

    /** 客服未读总数（用于顶部小红点） */
    public static int getAdminUnreadCount(Long adminId, ChatSessionMapper mapper) {
        java.util.List<ChatSession> active = mapper.selectActiveByAdminId(adminId);
        int sum = 0;
        for (ChatSession s : active) sum += Math.max(0, s.getAdminUnread() == null ? 0 : s.getAdminUnread());
        return sum;
    }

    // ========== 工具 ==========

    private Long extractUserId(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null) return null;
        try { return jwtUtil.parseUserId(token); } catch (Exception e) { return null; }
    }

    private String extractToken(WebSocketSession session) {
        return session.getUri().getQuery() == null ? null : getQueryParam(session.getUri().getQuery(), "token");
    }

    private Integer extractRole(WebSocketSession session) {
        String role = session.getUri().getQuery() == null ? null : getQueryParam(session.getUri().getQuery(), "role");
        if (role == null) return null;
        try { return Integer.parseInt(role); } catch (Exception e) { return null; }
    }

    private String getQueryParam(String query, String key) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
