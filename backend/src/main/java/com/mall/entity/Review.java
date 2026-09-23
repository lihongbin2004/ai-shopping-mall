package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Review {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer rating;
    private String content;
    private Date createTime;
    private String username;
}
