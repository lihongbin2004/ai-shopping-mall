package com.mall.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SystemConfig {
    private Integer id;
    private String configKey;
    private String configValue;
    private Date updateTime;
}
