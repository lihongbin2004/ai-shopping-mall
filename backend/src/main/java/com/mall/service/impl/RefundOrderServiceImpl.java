package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Order;
import com.mall.entity.RefundOrder;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.RefundOrderMapper;
import com.mall.service.RefundOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundOrderServiceImpl implements RefundOrderService {

    @Autowired
    private RefundOrderMapper refundOrderMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public RefundOrder createRefund(Long userId, Long orderId, Long productId,
                                    String reason, String description, BigDecimal refundAmount) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }
        // 仅已完成/已收货的订单可申请退款 (status 3=已完成 2=待收货)
        if (order.getStatus() != 3 && order.getStatus() != 2) {
            throw new BusinessException("订单当前状态不允许申请退款");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("退款原因不能为空");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于0");
        }
        if (refundAmount.compareTo(order.getTotalAmount()) > 0) {
            throw new BusinessException("退款金额不能超过订单金额");
        }

        RefundOrder refund = new RefundOrder();
        refund.setRefundNo("RF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        refund.setOrderId(orderId);
        refund.setOrderNo(order.getOrderNo());
        refund.setUserId(userId);
        refund.setProductId(productId);
        refund.setReason(reason);
        refund.setDescription(description);
        refund.setRefundAmount(refundAmount);
        refund.setStatus(0); // 待审核
        refund.setCreateTime(new Date());

        refundOrderMapper.insert(refund);
        return refund;
    }

    @Override
    public List<RefundOrder> getUserRefunds(Long userId) {
        return refundOrderMapper.selectByUserId(userId);
    }

    @Override
    public Map<String, Object> getAllRefunds(int page, int size, Integer status, String keyword) {
        Map<String, Object> params = new HashMap<>();
        if (status != null) params.put("status", status);
        if (keyword != null && !keyword.trim().isEmpty()) params.put("keyword", keyword.trim());
        params.put("offset", (page - 1) * size);
        params.put("limit", size);

        List<RefundOrder> list = refundOrderMapper.selectAll(params);
        int total = refundOrderMapper.countAll(params);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public RefundOrder getRefundDetail(Long id, Long userId, String role) {
        RefundOrder refund = refundOrderMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException("退款单不存在");
        }
        // 普通用户只能看自己的
        if (!"ADMIN".equals(role) && !refund.getUserId().equals(userId)) {
            throw new BusinessException("无权查看此退款单");
        }
        return refund;
    }

    @Override
    public void auditRefund(Long id, Integer status, String rejectReason, String adminReply, String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("需要管理员权限");
        }
        RefundOrder refund = refundOrderMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException("退款单不存在");
        }
        if (refund.getStatus() != 0) {
            throw new BusinessException("该退款单已审核");
        }
        if (status == 2 && (rejectReason == null || rejectReason.trim().isEmpty())) {
            throw new BusinessException("拒绝时必须填写拒绝原因");
        }
        refundOrderMapper.updateAudit(id, status, rejectReason, adminReply);
        // 审核通过(1) 或 标记已退款(3) → 把原订单状态改为 5(已退款)
        if (status == 1 || status == 3) {
            Order order = orderMapper.selectById(refund.getOrderId());
            if (order != null && order.getStatus() != 5) {
                orderMapper.updateStatus(refund.getOrderId(), 5);
            }
        }
        // 如果标记为已退款，更新 refund_time
        if (status == 3) {
            refundOrderMapper.updateRefund(id, new Date());
        }
    }
}
