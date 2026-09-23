package com.mall.service;

import com.mall.entity.RefundOrder;
import java.util.List;
import java.util.Map;

public interface RefundOrderService {
    RefundOrder createRefund(Long userId, Long orderId, Long productId, String reason, String description, java.math.BigDecimal refundAmount);
    List<RefundOrder> getUserRefunds(Long userId);
    Map<String, Object> getAllRefunds(int page, int size, Integer status, String keyword);
    RefundOrder getRefundDetail(Long id, Long userId, String role);
    void auditRefund(Long id, Integer status, String rejectReason, String adminReply, String role);
}
