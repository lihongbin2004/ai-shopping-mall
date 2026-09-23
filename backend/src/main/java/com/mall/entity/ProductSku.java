package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ProductSku {
    private Long id;
    private Long productId;
    private String skuCode;
    /** 规格组合 JSON，如 {"颜色":"黑","容量":"256G"} */
    private String specs;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private Integer sales;
    private Date createTime;

    /** 运行时解析后的规格 Map */
    private java.util.Map<String, String> specMap;
    /** 规格描述字符串，如 "颜色:黑, 容量:256G" */
    private String specDesc;
}
