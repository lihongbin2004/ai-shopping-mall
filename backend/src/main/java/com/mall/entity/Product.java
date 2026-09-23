package com.mall.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Product {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Long categoryId;
    /** 分类名称（运行时由 Service 填充，不入库） */
    private String categoryName;
    private String image;
    private Integer sales;
    private Integer status;
    private Date createTime;
    /** 推荐打分（仅运行时使用，不入库） */
    private transient Double recScore;
    /** SKU 列表（运行时填充，不入库） */
    private transient java.util.List<ProductSku> skus;
    /** 规格维度（运行时根据 SKU 聚合，如 ["颜色","容量"]） */
    private transient java.util.List<String> specKeys;
    /** 每个规格维度的可选值（运行时聚合） */
    private transient java.util.Map<String, java.util.List<String>> specOptions;
}
