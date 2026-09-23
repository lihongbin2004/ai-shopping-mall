package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    /** SKU ID（可选） */
    private Long skuId;
    private String productName;
    private String productImage;
    private Integer quantity;
    private BigDecimal price;
    /** 规格描述，如 "颜色:黑, 容量:256G" */
    private String specDesc;
}
