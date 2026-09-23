package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class RefundOrder {
    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long productId;
    private String reason;
    private String description;
    private BigDecimal refundAmount;
    /** 0=待审核 1=已通过 2=已拒绝 3=已退款 */
    private Integer status;
    private String rejectReason;
    private String adminReply;
    private Date createTime;
    private Date auditTime;
    private Date refundTime;
    // 关联字段
    private transient String username;
    private transient String productName;
    private transient String productImage;
}
