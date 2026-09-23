package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private Long addressId;
    private String addressSnapshot;   // 下单时的地址快照（JSON），避免地址修改影响历史订单
    private String payment;
    private Date createTime;
    private Date payTime;
    private Date deliveryTime;
    private Date finishTime;
    private String remark;
    private List<OrderItem> items;
    // 关联字段（不映射到数据库）
    private transient String addressName;
    private transient String addressPhone;
    private transient String addressDetail;
    private transient String username;
}
