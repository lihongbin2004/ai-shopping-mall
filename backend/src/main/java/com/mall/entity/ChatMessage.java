package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    /** 0=客户(user) 1=客服(admin) */
    private Integer senderType;
    private Long senderId;
    private String content;
    private Date createTime;

    /** 运行时 */
    private String senderName;
}
