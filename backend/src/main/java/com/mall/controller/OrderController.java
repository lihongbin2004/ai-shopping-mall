package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Order;
import com.mall.service.OrderService;
import com.mall.service.impl.RecommendServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@Api(tags = "订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired(required = false)
    private RecommendServiceImpl recommendService;

    @PostMapping("/create")
    @ApiOperation("创建订单")
    public R<Order> create(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long addressId = Long.valueOf(params.get("addressId").toString());
        String payment = (String) params.get("payment");
        if (payment == null || payment.isEmpty()) payment = "在线支付";
        Object couponObj = params.get("couponId");
        Long couponId = (couponObj == null || couponObj.toString().isEmpty()) ? null : Long.valueOf(couponObj.toString());
        // JSON 数字数组默认是 List<Integer>，需要手动转 Long
        List<Long> cartIds = new java.util.ArrayList<>();
        if (params.get("cartIds") instanceof List) {
            for (Object o : (List<?>) params.get("cartIds")) {
                if (o != null) cartIds.add(Long.valueOf(o.toString()));
            }
        }
        String remark = (String) params.get("remark");
        return R.ok(orderService.createOrder(userId, addressId, payment, couponId, cartIds, remark));
    }

    @GetMapping("/list")
    @ApiOperation("查询用户订单")
    public R<Map<String, Object>> list(HttpServletRequest request,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) Integer status) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(orderService.getUserOrders(userId, page, size, status));
    }

    @GetMapping("/detail/{id}")
    @ApiOperation("订单详情")
    public R<Order> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        return R.ok(orderService.getOrderDetail(id, userId, role));
    }

    @GetMapping("/detail-by-no/{orderNo}")
    @ApiOperation("通过订单号查询订单详情")
    public R<Order> detailByNo(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        return R.ok(orderService.getOrderDetailByOrderNo(orderNo, userId, role));
    }

    @PostMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public R<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.cancelOrder(id, userId);
        return R.ok();
    }

    @PostMapping("/pay/{id}")
    @ApiOperation("支付订单")
    public R<Order> pay(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String payment = params == null ? null : (String) params.get("payment");
        Order order = orderService.payOrder(id, userId, payment);
        // 支付成功 = 强购买信号，记录每个商品的购买行为
        if (userId != null && recommendService != null && order != null && order.getItems() != null) {
            for (com.mall.entity.OrderItem item : order.getItems()) {
                try { recommendService.trackBuy(userId, item.getProductId()); } catch (Exception ignore) {}
            }
        }
        return R.ok(order);
    }

    @PostMapping("/confirm/{id}")
    @ApiOperation("确认收货")
    public R<Order> confirm(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(orderService.confirmReceive(id, userId));
    }

    @PostMapping("/ship/{id}")
    @ApiOperation("发货（演示用）")
    public R<Order> ship(@PathVariable Long id, HttpServletRequest request) {
        // 演示用：把订单状态从 1(待发货) 推到 2(待收货)
        orderService.updateOrderStatus(id, 2);
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        return R.ok(orderService.getOrderDetail(id, userId, role));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除订单")
    public R<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.deleteOrder(id, userId);
        return R.ok();
    }
}
