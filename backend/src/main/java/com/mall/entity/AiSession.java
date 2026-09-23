package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class AiSession {
    private Long id;
    private Long userId;
    private String title;
    private Date createTime;
    private Date updateTime;
}
