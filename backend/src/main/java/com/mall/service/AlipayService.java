package com.mall.service;

import com.mall.entity.Order;

import java.util.Map;

public interface AlipayService {
    /**
     * 生成支付宝 PC 网页支付表单 HTML（沙箱）
     */
    String createPagePayForm(Order order);

    /**
     * 校验异步通知签名
     */
    boolean verifyNotify(Map<String, String> params);

    /**
     * 异步通知处理：成功则把订单置为已支付
     * @return "success" 表示处理成功
     */
    String handleNotify(Map<String, String> params);

    /**
     * 主动查询支付宝订单状态，如果已支付则把本地订单置为已支付
     * 用于支付完成返回页面时同步状态（解决沙箱/本地无法接收 notify 的问题）
     * @return true 表示已支付
     */
    boolean syncOrderStatus(String orderNo);
}
