package com.mall.controller;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.R;
import com.mall.entity.Order;
import com.mall.mapper.OrderMapper;
import com.mall.service.AlipayService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@Api(tags = "支付接口")
public class PaymentController {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AlipayService alipayService;

    /**
     * 创建支付宝沙箱支付表单
     * 返回 JSON { form: '<form>...</form>', orderNo: 'xxx' }
     * 前端拿到 form 后 document.body.innerHTML + .submit() 跳转
     */
    @PostMapping("/alipay/create/{orderId}")
    @ApiOperation("创建支付宝支付")
    public R<Map<String, String>> createAlipayPay(@PathVariable Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("订单状态不可支付");
        }
        String form = alipayService.createPagePayForm(order);
        Map<String, String> data = new HashMap<>();
        data.put("form", form);
        data.put("orderNo", order.getOrderNo());
        return R.ok(data);
    }
    /**
     * 模拟支付（沙箱不稳定时调试用）
     */
    @PostMapping("/mock-pay/{orderId}")
    @ApiOperation("模拟支付成功")
    public R<Void> mockPay(@PathVariable Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("订单状态不可支付");
        }
        orderMapper.updatePayTime(orderId, 1, new java.util.Date());
        return R.ok();
    }

    /**
     * 主动查询订单状态（支付返回页面时调用）
     * 解决沙箱/本地环境 notify 回调无法到达的问题
     */
    @PostMapping("/alipay/query/{orderId}")
    @ApiOperation("查询订单支付状态并同步")
    public R<Map<String, Object>> queryOrderStatus(@PathVariable Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 先查本地
        boolean paid = order.getStatus() != 0;
        // 本地未支付时，主动查支付宝
        if (!paid) {
            paid = alipayService.syncOrderStatus(order.getOrderNo());
            if (paid) {
                order = orderMapper.selectById(orderId);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("paid", paid);
        data.put("status", order.getStatus());
        data.put("orderNo", order.getOrderNo());
        return R.ok(data);
    }

    /**
     * 支付宝异步通知
     */
    @PostMapping("/alipay/notify")
    @ApiOperation("支付宝异步通知")
    public void alipayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("收到支付宝异步通知");
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
            String name = iter.next();
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        boolean verified = alipayService.verifyNotify(params);
        String result;
        if (verified) {
            result = alipayService.handleNotify(params);
        } else {
            log.warn("支付宝通知验签失败 params={}", params);
            result = "fail";
        }

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print(result);
        out.flush();
        out.close();
    }
}
