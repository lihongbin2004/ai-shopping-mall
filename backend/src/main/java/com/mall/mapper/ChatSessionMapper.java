package com.mall.mapper;

import com.mall.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ChatSessionMapper {
    ChatSession selectById(Long id);
    /** 用户活跃会话（status=0） */
    ChatSession selectActiveByUserId(@Param("userId") Long userId);
    /** 客服活跃会话列表 */
    List<ChatSession> selectActiveByAdminId(@Param("adminId") Long adminId);
    /** 所有活跃会话（客服工作台总览） */
    List<ChatSession> selectAllActive();
    /** 最近活跃会话（含已关闭） */
    List<ChatSession> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    List<ChatSession> selectRecentByAdminId(@Param("adminId") Long adminId, @Param("limit") int limit);
    int insert(ChatSession session);
    int update(ChatSession session);
    int updateLastMessage(@Param("id") Long id, @Param("message") String message, @Param("time") java.util.Date time);
    int incAdminUnread(@Param("id") Long id);
    int incUserUnread(@Param("id") Long id);
    int clearAdminUnread(@Param("id") Long id);
    int clearUserUnread(@Param("id") Long id);
    int closeSession(@Param("id") Long id);
    int assignAdmin(@Param("id") Long id, @Param("adminId") Long adminId);
}
