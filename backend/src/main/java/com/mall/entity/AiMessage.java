package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class AiMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Date createTime;
}
