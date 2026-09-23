package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class UserCoupon {
    private Long id;
    private Long userId;
    private Long couponId;
    private Integer status;
    private Date usedTime;
    private String couponName;
    private java.math.BigDecimal amount;
    private java.math.BigDecimal minAmount;
    // 关联展示字段（不入库）
    private transient Date startTime;
    private transient Date endTime;
}
