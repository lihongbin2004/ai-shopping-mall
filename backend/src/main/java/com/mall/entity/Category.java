package com.mall.entity;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class Category {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    private Date createTime;
    private List<Category> children;
}
