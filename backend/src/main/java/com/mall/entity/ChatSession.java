package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ChatSession {
    private Long id;
    private Long userId;
    private Long adminId;
    private Integer status;  // 0=进行中 1=已关闭
    private String lastMessage;
    private Date lastTime;
    private Integer unreadCount;   // 客户未读数
    private Integer adminUnread;   // 客服未读数
    private Date createTime;

    /** 运行时填充 */
    private String userName;
    private String userAvatar;
    private String adminName;
}
