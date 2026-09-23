package com.mall.service;

import com.mall.entity.Order;
import java.util.List;
import java.util.Map;

public interface OrderService {
    Order createOrder(Long userId, Long addressId, String payment, Long couponId, List<Long> cartIds, String remark);
    Map<String, Object> getUserOrders(Long userId, int page, int size, Integer status);
    Order getOrderDetail(Long id, Long userId, String role);
    Order getOrderDetailByOrderNo(String orderNo, Long userId, String role);
    void cancelOrder(Long id, Long userId);
    Order payOrder(Long id, Long userId, String payment);
    Order confirmReceive(Long id, Long userId);
    void deleteOrder(Long id, Long userId);
    Map<String, Object> getAllOrders(int page, int size, Integer status, String orderNo);
    void updateOrderStatus(Long id, Integer status);
}

