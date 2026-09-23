package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayUrl;
    private String signType;
    private String charset;
    private String format;
    private String notifyUrl;
    private String returnUrl;
    private Boolean sandbox;
}
