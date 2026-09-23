package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.RefundOrder;
import com.mall.service.RefundOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refund")
@Api(tags = "售后退款接口")
public class RefundOrderController {

    @Autowired
    private RefundOrderService refundOrderService;

    @PostMapping("/create")
    @ApiOperation("申请退款")
    public R<RefundOrder> create(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long orderId = Long.valueOf(params.get("orderId").toString());
        Long productId = params.get("productId") == null ? null : Long.valueOf(params.get("productId").toString());
        String reason = (String) params.get("reason");
        String description = (String) params.get("description");
        BigDecimal refundAmount = new BigDecimal(params.get("refundAmount").toString());
        return R.ok(refundOrderService.createRefund(userId, orderId, productId, reason, description, refundAmount));
    }

    @GetMapping("/list")
    @ApiOperation("我的退款列表")
    public R<List<RefundOrder>> myList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(refundOrderService.getUserRefunds(userId));
    }

    @GetMapping("/detail/{id}")
    @ApiOperation("退款详情")
    public R<RefundOrder> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        return R.ok(refundOrderService.getRefundDetail(id, userId, role));
    }

    @GetMapping("/admin/list")
    @ApiOperation("管理员：所有退款列表")
    public R<Map<String, Object>> adminList(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(refundOrderService.getAllRefunds(page, size, status, keyword));
    }

    @PostMapping("/admin/audit/{id}")
    @ApiOperation("管理员：审核退款")
    public R<Void> audit(@PathVariable Long id,
                         @RequestBody Map<String, Object> params,
                         HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        Integer status = Integer.valueOf(params.get("status").toString());
        String rejectReason = (String) params.get("rejectReason");
        String adminReply = (String) params.get("adminReply");
        refundOrderService.auditRefund(id, status, rejectReason, adminReply, role);
        return R.ok();
    }

    @PostMapping("/admin/confirm-refund/{id}")
    @ApiOperation("管理员：确认退款完成")
    public R<Void> confirmRefund(@PathVariable Long id, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return R.error("需要管理员权限");
        }
        refundOrderService.auditRefund(id, 1, null, "已退款", role);
        return R.ok();
    }
}
