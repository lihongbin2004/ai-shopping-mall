package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Coupon {
    private Long id;
    private String name;
    private BigDecimal amount;
    private BigDecimal minAmount;
    private Integer stock;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
