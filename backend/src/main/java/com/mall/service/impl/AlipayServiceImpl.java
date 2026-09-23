package com.mall.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.mall.config.AlipayConfig;
import com.mall.entity.Order;
import com.mall.mapper.OrderMapper;
import com.mall.service.AlipayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "alipay.app-id")
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private AlipayConfig alipayConfig;

    @Autowired
    private OrderMapper orderMapper;

    private AlipayClient alipayClient;

    private AlipayClient getClient() {
        if (alipayClient == null) {
            alipayClient = new DefaultAlipayClient(
                    alipayConfig.getGatewayUrl(),
                    alipayConfig.getAppId(),
                    alipayConfig.getPrivateKey(),
                    alipayConfig.getFormat(),
                    alipayConfig.getCharset(),
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getSignType(),
                    "3000",   // connectTimeout 3s
                    "5000"    // readTimeout 5s
            );
        }
        return alipayClient;
    }

    @Override
    public String createPagePayForm(Order order) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(alipayConfig.getReturnUrl());
        request.setNotifyUrl(alipayConfig.getNotifyUrl());

        // 单位：元，保留2位
        String amount = order.getTotalAmount()
                .setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();

        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\","
                        + "\"total_amount\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}",
                order.getOrderNo(),
                amount,
                "AI商城订单-" + order.getOrderNo(),
                order.getOrderNo()
        );
        request.setBizContent(bizContent);

        try {
            // 设置连接和读取超时为8秒（沙箱经常502）
            System.setProperty("sun.net.client.defaultConnectTimeout", "8000");
            System.setProperty("sun.net.client.defaultReadTimeout", "8000");
            return getClient().pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            log.error("生成支付宝表单失败 orderNo={}", order.getOrderNo(), e);
            throw new RuntimeException("支付页面生成失败（沙箱网关不稳定）：" + e.getMessage());
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
        } catch (AlipayApiException e) {
            log.error("支付宝通知验签失败", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(Map<String, String> params) {
        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");

        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            Order order = orderMapper.selectByOrderNo(outTradeNo);
            if (order != null && order.getStatus() == 0) {
                orderMapper.updatePayTime(order.getId(), 1, new Date());
                log.info("支付宝异步通知：订单 {} 支付成功", outTradeNo);
            }
        }
        return "success";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean syncOrderStatus(String orderNo) {
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizContent("{\"out_trade_no\":\"" + orderNo + "\"}");

            AlipayTradeQueryResponse response = getClient().execute(request);
            if (!response.isSuccess()) {
                log.warn("查询支付宝订单失败 orderNo={} subMsg={}", orderNo, response.getSubMsg());
                return false;
            }

            String tradeStatus = response.getTradeStatus();
            log.info("查询支付宝订单 orderNo={} tradeStatus={}", orderNo, tradeStatus);

            // TRADE_SUCCESS（支付成功）或 TRADE_FINISHED（交易完结）都算已支付
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                Order order = orderMapper.selectByOrderNo(orderNo);
                if (order != null && order.getStatus() == 0) {
                    orderMapper.updatePayTime(order.getId(), 1, new Date());
                    log.info("同步支付宝订单：订单 {} 标记为已支付", orderNo);
                }
                return true;
            }
            return false;
        } catch (AlipayApiException e) {
            log.error("查询支付宝订单异常 orderNo={}", orderNo, e);
            return false;
        }
    }
}
