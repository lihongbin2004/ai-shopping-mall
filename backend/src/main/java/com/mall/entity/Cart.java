package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Cart {
    private Long id;
    private Long userId;
    private Long productId;
    /** SKU ID（可选，单规格商品可不传） */
    private Long skuId;
    private Integer quantity;
    private Integer checked;
    private Date createTime;
    /** 运行时填充 */
    private String productName;
    private String productImage;
    private java.math.BigDecimal productPrice;
    private Integer productStatus;
    private Integer stock;
    /** 运行时填充：规格描述 */
    private String specDesc;
}
