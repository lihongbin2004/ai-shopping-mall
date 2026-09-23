package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Favorite {
    private Long id;
    private Long userId;
    private Long productId;
    private Date createTime;
    // 联表查询/服务层注入的商品信息
    private String name;
    private String image;
    private BigDecimal price;
    private String description;
    private Integer sales;
    private Integer productStatus;
}
