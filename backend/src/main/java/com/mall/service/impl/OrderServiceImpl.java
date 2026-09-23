package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.*;
import com.mall.mapper.*;
import com.mall.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private UserMapper userMapper;

    private synchronized String generateOrderNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String dateStr = sdf.format(new Date());
        int random = (int) (Math.random() * 9000) + 1000;
        return "ORD" + dateStr + random;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, Long addressId, String payment, Long couponId, List<Long> cartIds, String remark) {
        // 校验地址归属
        com.mall.entity.Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException("收货地址不存在");
        }

        List<Cart> cartList = new ArrayList<>();
        for (Long cartId : cartIds) {
            Cart cart = cartMapper.selectById(cartId);
            if (cart == null || !cart.getUserId().equals(userId)) {
                throw new BusinessException("购物车记录不存在");
            }
            cartList.add(cart);
        }

        if (cartList.isEmpty()) {
            throw new BusinessException("请选择要购买的商品");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (Cart cart : cartList) {
            Product product = productMapper.selectById(cart.getProductId());
            if (product == null) {
                throw new BusinessException("商品[" + cart.getProductId() + "]不存在");
            }
            if (product.getStatus() != null && product.getStatus() == 0) {
                throw new BusinessException("商品[" + product.getName() + "]已下架，无法购买");
            }

            // SKU 优先
            ProductSku sku = null;
            BigDecimal itemPrice;
            String itemImage;
            String specDesc = null;
            if (cart.getSkuId() != null) {
                sku = productSkuMapper.selectById(cart.getSkuId());
                if (sku == null || !sku.getProductId().equals(product.getId())) {
                    throw new BusinessException("SKU 不存在或不属于商品[" + product.getName() + "]");
                }
                if (sku.getStock() < cart.getQuantity()) {
                    throw new BusinessException("商品[" + product.getName() + " " + sku.getSpecDesc() + "]库存不足");
                }
                itemPrice = sku.getPrice();
                itemImage = sku.getImage() != null ? sku.getImage() : product.getImage();
                specDesc = sku.getSpecDesc();
            } else {
                if (product.getStock() < cart.getQuantity()) {
                    throw new BusinessException("商品[" + product.getName() + "]库存不足");
                }
                itemPrice = product.getPrice();
                itemImage = product.getImage();
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setSkuId(cart.getSkuId());
            item.setSpecDesc(specDesc);
            item.setProductName(product.getName());
            item.setProductImage(itemImage);
            item.setQuantity(cart.getQuantity());
            item.setPrice(itemPrice);
            orderItems.add(item);

            totalAmount = totalAmount.add(itemPrice.multiply(BigDecimal.valueOf(cart.getQuantity())));

            // 原子扣减库存
            if (sku != null) {
                int rows = productSkuMapper.updateStock(sku.getId(), -cart.getQuantity());
                if (rows == 0) {
                    throw new BusinessException("商品[" + product.getName() + "]库存不足");
                }
                productSkuMapper.addSales(sku.getId(), cart.getQuantity());
            } else {
                int rows = productMapper.updateStock(product.getId(), cart.getQuantity());
                if (rows == 0) {
                    throw new BusinessException("商品[" + product.getName() + "]库存不足");
                }
                productMapper.addSales(product.getId(), cart.getQuantity());
            }
        }

        if (couponId != null) {
            // 校验优惠券归属
            UserCoupon userCoupon = userCouponMapper.selectById(couponId);
            if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
                throw new BusinessException("优惠券不可用");
            }
            // 校验优惠券本身是否已禁用
            com.mall.entity.Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (coupon != null && coupon.getStatus() != null && coupon.getStatus() == 0) {
                throw new BusinessException("该优惠券已禁用");
            }
            if (userCoupon.getStatus() == 0) {
                BigDecimal minAmount = userCoupon.getMinAmount() == null ? BigDecimal.ZERO : userCoupon.getMinAmount();
                BigDecimal discount = userCoupon.getAmount() == null ? BigDecimal.ZERO : userCoupon.getAmount();
                if (totalAmount.compareTo(minAmount) >= 0) {
                    totalAmount = totalAmount.subtract(discount);
                    if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                        totalAmount = BigDecimal.ZERO;
                    }
                    userCouponMapper.updateStatus(userCoupon.getId(), 1, new Date());
                }
            }
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        order.setAddressId(addressId);
        order.setAddressSnapshot(buildAddressSnapshot(address));
        order.setPayment(payment);
        order.setCreateTime(new Date());
        order.setRemark(remark);
        orderMapper.insert(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        for (Long cartId : cartIds) {
            cartMapper.deleteById(cartId);
        }

        order.setItems(orderItems);
        fillAddress(order);
        return order;
    }

    @Override
    public Map<String, Object> getUserOrders(Long userId, int page, int size, Integer status) {
        int offset = (page - 1) * size;
        List<Order> list;
        int total;
        if (status != null) {
            list = orderMapper.selectByUserIdAndStatus(userId, status, offset, size);
            total = orderMapper.countByUserIdAndStatus(userId, status);
        } else {
            list = orderMapper.selectByUserId(userId, offset, size);
            total = orderMapper.countByUserId(userId);
        }
        for (Order order : list) {
            order.setItems(orderItemMapper.selectByOrderId(order.getId()));
            fillAddress(order);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Order getOrderDetail(Long id, Long userId, String role) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 管理员可查看任意订单
        boolean isAdmin = "ADMIN".equals(role);
        if (!isAdmin && userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该订单");
        }
        order.setItems(orderItemMapper.selectByOrderId(order.getId()));
        fillAddress(order);
        fillUsername(order);
        return order;
    }

    @Override
    public Order getOrderDetailByOrderNo(String orderNo, Long userId, String role) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 管理员可查看任意订单
        boolean isAdmin = "ADMIN".equals(role);
        if (!isAdmin && userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该订单");
        }
        order.setItems(orderItemMapper.selectByOrderId(order.getId()));
        fillAddress(order);
        fillUsername(order);
        return order;
    }

    private void fillAddress(Order order) {
        // 优先使用下单时的地址快照，避免用户后来修改地址影响历史订单显示
        if (order.getAddressSnapshot() != null && !order.getAddressSnapshot().isEmpty()) {
            try {
                com.alibaba.fastjson2.JSONObject snap = com.alibaba.fastjson2.JSON.parseObject(order.getAddressSnapshot());
                order.setAddressName(snap.getString("receiver"));
                order.setAddressPhone(snap.getString("phone"));
                StringBuilder sb = new StringBuilder();
                if (snap.getString("province") != null) sb.append(snap.getString("province"));
                if (snap.getString("city") != null) sb.append(snap.getString("city"));
                if (snap.getString("district") != null) sb.append(snap.getString("district"));
                if (snap.getString("detail") != null) sb.append(snap.getString("detail"));
                order.setAddressDetail(sb.toString());
                return;
            } catch (Exception ignored) {
                // 快照解析失败，回退查 address 表
            }
        }
        // 回退：从 address 表实时查（兼容老订单）
        if (order.getAddressId() == null) return;
        com.mall.entity.Address addr = addressMapper.selectById(order.getAddressId());
        if (addr != null) {
            order.setAddressName(addr.getReceiver());
            order.setAddressPhone(addr.getPhone());
            StringBuilder sb = new StringBuilder();
            if (addr.getProvince() != null) sb.append(addr.getProvince());
            if (addr.getCity() != null) sb.append(addr.getCity());
            if (addr.getDistrict() != null) sb.append(addr.getDistrict());
            if (addr.getDetail() != null) sb.append(addr.getDetail());
            order.setAddressDetail(sb.toString());
        }
    }

    /** 构建地址快照 JSON */
    private String buildAddressSnapshot(com.mall.entity.Address addr) {
        try {
            com.alibaba.fastjson2.JSONObject snap = new com.alibaba.fastjson2.JSONObject();
            snap.put("receiver", addr.getReceiver());
            snap.put("phone", addr.getPhone());
            snap.put("province", addr.getProvince());
            snap.put("city", addr.getCity());
            snap.put("district", addr.getDistrict());
            snap.put("detail", addr.getDetail());
            return snap.toJSONString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        // 仅允许待付款(0)和待发货(1)取消；已发货后需走退款流程
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BusinessException("当前订单状态不可取消，如需退款请走退款流程");
        }
        // 待发货状态（已付款）取消 → 视为已退款，便于用户重新发起支付
        int newStatus = order.getStatus() == 1 ? 5 : 4;
        orderMapper.updateStatus(id, newStatus);
        if (newStatus == 5) {
            // 标记退款时间，金额会在退款流程中处理；这里只占位
            orderMapper.updateFinishTime(id, 5, new Date());
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(id);
        for (OrderItem item : items) {
            // 还原库存（SKU 优先）
            if (item.getSkuId() != null) {
                productSkuMapper.updateStock(item.getSkuId(), item.getQuantity());
                productSkuMapper.reduceSales(item.getSkuId(), item.getQuantity());
            } else {
                productMapper.updateStock(item.getProductId(), -item.getQuantity());
                productMapper.reduceSales(item.getProductId(), item.getQuantity());
            }
        }
    }

    @Override
    public Map<String, Object> getAllOrders(int page, int size, Integer status, String orderNo) {
        int offset = (page - 1) * size;
        List<Order> list;
        int total;
        boolean hasOrderNo = orderNo != null && !orderNo.trim().isEmpty();
        if (hasOrderNo) {
            // 按订单号搜索
            if (status == null) {
                list = orderMapper.selectByOrderNoLike(orderNo.trim(), offset, size);
                total = orderMapper.countByOrderNoLike(orderNo.trim());
            } else {
                list = orderMapper.selectByStatusAndOrderNoLike(status, orderNo.trim(), offset, size);
                total = orderMapper.countByStatusAndOrderNoLike(status, orderNo.trim());
            }
        } else if (status == null) {
            list = orderMapper.selectAll(offset, size);
            total = orderMapper.countAll();
        } else {
            list = orderMapper.selectByStatus(status, offset, size);
            total = orderMapper.countByStatus(status);
        }
        for (Order order : list) {
            order.setItems(orderItemMapper.selectByOrderId(order.getId()));
            fillAddress(order);
            fillUsername(order);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private void fillUsername(Order order) {
        if (order.getUserId() == null) return;
        User user = userMapper.selectById(order.getUserId());
        if (user != null) {
            order.setUsername(user.getUsername());
        }
    }

    @Override
    public void updateOrderStatus(Long id, Integer status) {
        orderMapper.updateStatus(id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order payOrder(Long id, Long userId, String payment) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("当前订单状态不可支付");
        }
        // 支付宝沙箱模式下，真正可靠的支付完成会由支付宝异步通知触发；
        // 但为了让用户在前端能立刻看到状态变更，这里同步将订单标记为已支付。
        if (payment != null && !payment.isEmpty()) {
            order.setPayment(payment);
        }
        orderMapper.updatePayTime(id, 1, new Date());
        order.setStatus(1);
        order.setPayTime(new Date());
        order.setPayAmount(order.getTotalAmount());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order confirmReceive(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() != 2) {
            throw new BusinessException("当前订单状态不可确认收货");
        }
        orderMapper.updateFinishTime(id, 3, new Date());
        order.setStatus(3);
        order.setFinishTime(new Date());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() != 4) {
            throw new BusinessException("只有已取消的订单可以删除");
        }
        orderItemMapper.deleteByOrderId(id);
        orderMapper.deleteById(id);
    }
}
