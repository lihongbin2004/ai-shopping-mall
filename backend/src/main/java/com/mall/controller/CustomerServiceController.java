package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.ChatMessage;
import com.mall.entity.ChatSession;
import com.mall.entity.User;
import com.mall.mapper.ChatMessageMapper;
import com.mall.mapper.ChatSessionMapper;
import com.mall.mapper.UserMapper;
import com.mall.websocket.CustomerServiceWebSocketHandler;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/kf")
@Api(tags = "在线客服接口")
public class CustomerServiceController {

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    /** 客户侧：获取或创建自己的活跃会话 */
    @GetMapping("/session")
    @ApiOperation("获取客户活跃会话")
    public R<ChatSession> getMySession(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        ChatSession session = sessionMapper.selectActiveByUserId(userId);
        if (session == null) {
            session = new ChatSession();
            session.setUserId(userId);
            session.setStatus(0);
            session.setCreateTime(new Date());
            sessionMapper.insert(session);
        }
        fillUserNames(Collections.singletonList(session));
        return R.ok(session);
    }

    /** 客户侧：最近历史会话 */
    @GetMapping("/sessions/recent")
    @ApiOperation("客户最近会话")
    public R<List<ChatSession>> myRecentSessions(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "10") int limit) {
        Long userId = (Long) request.getAttribute("userId");
        List<ChatSession> list = sessionMapper.selectRecentByUserId(userId, limit);
        fillUserNames(list);
        return R.ok(list);
    }

    /** 获取会话消息历史 */
    @GetMapping("/messages")
    @ApiOperation("获取会话消息")
    public R<List<ChatMessage>> getMessages(@RequestParam Long sessionId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) return R.ok(new ArrayList<>());
        boolean isAdmin = "ADMIN".equals(role);
        if (!isAdmin && !session.getUserId().equals(userId)) {
            return R.ok(new ArrayList<>());
        }
        List<ChatMessage> list = messageMapper.selectBySessionId(sessionId);
        return R.ok(list);
    }

    /** 关闭会话 */
    @PostMapping("/session/{id}/close")
    @ApiOperation("关闭会话")
    public R<Void> closeSession(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        ChatSession session = sessionMapper.selectById(id);
        if (session == null) return R.error("会话不存在");
        boolean isAdmin = "ADMIN".equals(role);
        if (!isAdmin && !session.getUserId().equals(userId)) return R.error("无权操作");
        sessionMapper.closeSession(id);
        return R.ok();
    }

    // ===== Admin 侧 =====

    /** Admin 侧：所有活跃会话（等待客服接入 + 进行中） */
    @GetMapping("/admin/sessions")
    @ApiOperation("客服工作台会话列表")
    public R<List<ChatSession>> adminSessions(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) return R.error("需要管理员权限");
        List<ChatSession> list = sessionMapper.selectAllActive();
        fillUserNames(list);
        // 未分配的排前面
        list.sort((a, b) -> {
            boolean aNoAdmin = a.getAdminId() == null;
            boolean bNoAdmin = b.getAdminId() == null;
            if (aNoAdmin && !bNoAdmin) return -1;
            if (!aNoAdmin && bNoAdmin) return 1;
            return 0;
        });
        return R.ok(list);
    }

    /** Admin 侧：未读消息总数（顶部小红点） */
    @GetMapping("/admin/unread")
    @ApiOperation("客服未读数")
    public R<Integer> adminUnread(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) return R.ok(0);
        int total = 0;
        List<ChatSession> active = sessionMapper.selectAllActive();
        for (ChatSession s : active) {
            if (s.getAdminId() == null || s.getAdminId().equals(userId)) {
                total += Math.max(0, s.getAdminUnread() == null ? 0 : s.getAdminUnread());
            }
        }
        return R.ok(total);
    }

    /** Admin 侧：接入会话（分配给自己） */
    @PostMapping("/admin/session/{id}/take")
    @ApiOperation("客服接入会话")
    public R<Void> takeSession(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) return R.error("需要管理员权限");
        sessionMapper.assignAdmin(id, userId);
        sessionMapper.clearAdminUnread(id);
        return R.ok();
    }

    // ===== 工具 =====

    private void fillUserNames(List<ChatSession> list) {
        for (ChatSession s : list) {
            if (s.getUserId() != null) {
                User u = userMapper.selectById(s.getUserId());
                if (u != null) s.setUserName(u.getUsername());
            }
            if (s.getAdminId() != null) {
                User a = userMapper.selectById(s.getAdminId());
                if (a != null) s.setAdminName(a.getUsername());
            }
        }
    }
}
