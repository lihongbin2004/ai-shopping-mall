package com.mall.ai;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Address;
import com.mall.entity.Cart;
import com.mall.entity.Order;
import com.mall.entity.OrderItem;
import com.mall.entity.Product;
import com.mall.entity.ProductSku;
import com.mall.service.AddressService;
import com.mall.service.CartService;
import com.mall.service.OrderService;
import com.mall.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 自动化购物流程：下单 / 支付 / 查询订单
 * LocalAiEngine（本地规则引擎）与 AiToolService（大模型工具调用）共用这一份实现，
 * 保证两条链路的行为一致。
 */
@Component
public class AiOrderTool {

    @Autowired
    private OrderService orderService;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductService productService;
    @Autowired
    private AddressService addressService;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("MM-dd HH:mm");

    /**
     * 下单
     * @param productName 商品名；为空、"全部"、"购物车" 时表示"把购物车里的商品全部下单"
     * @param quantity    数量，默认 1
     */
    public Map<String, Object> createOrder(Long userId, String productName, Integer quantity) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("ok", false);
            result.put("message", "请先登录后再下单哦～");
            return result;
        }
        if (quantity == null || quantity <= 0) quantity = 1;

        // 收货地址：优先默认地址，其次第一条
        Address address = pickAddress(userId);
        if (address == null) {
            result.put("ok", false);
            result.put("message", "您还没有收货地址");
            result.put("displayText", "\n\n❌ 您还没有收货地址，暂时无法下单。\n请先到「我的 → 收货地址」添加一个地址，再回来告诉我\"帮我下单\"～");
            return result;
        }

        List<Long> cartIds = new ArrayList<>();
        List<String> itemsDesc = new ArrayList<>();
        boolean fromCart = isBlank(productName)
                || "全部".equals(productName) || "购物车".equals(productName) || productName.contains("购物车");
        try {
            if (fromCart) {
                List<Cart> carts = cartService.getCartList(userId);
                if (carts == null || carts.isEmpty()) {
                    result.put("ok", false);
                    result.put("message", "购物车是空的");
                    result.put("displayText", "\n\n🛒 您的购物车还是空的，暂时没法下单。\n可以告诉我\"帮我买一个 iPhone\"，我帮您挑好再下单～");
                    return result;
                }
                for (Cart c : carts) {
                    cartIds.add(c.getId());
                    itemsDesc.add(shortName(c.getProductName()) + " ×" + c.getQuantity());
                }
            } else {
                Product product = findProductByName(productName);
                if (product == null) {
                    result.put("ok", false);
                    result.put("message", "没有找到商品: " + productName);
                    result.put("displayText", "\n\n抱歉，商城里没有找到【" + productName + "】这件商品，暂时没法下单😢");
                    return result;
                }
                List<Cart> carts = cartService.getCartList(userId);
                Cart target = null;
                if (carts != null) {
                    for (Cart c : carts) {
                        if (c.getProductId() != null && c.getProductId().equals(product.getId())) {
                            target = c;
                            break;
                        }
                    }
                }
                if (target != null) {
                    // 购物车里已有同款：按用户说的数量更新
                    try { cartService.updateQuantity(target.getId(), quantity); } catch (Exception ignored) {}
                    cartIds.add(target.getId());
                } else {
                    Cart created = cartService.addItem(userId, product.getId(), quantity);
                    cartIds.add(created.getId());
                }
                itemsDesc.add(shortName(product.getName()) + " ×" + quantity);
            }

            Order order = orderService.createOrder(userId, address.getId(), "在线支付", null, cartIds, "AI助手代下单");

            result.put("ok", true);
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("totalAmount", order.getTotalAmount());
            result.put("status", order.getStatus());
            result.put("address", addressText(address));
            result.put("message", "下单成功，订单号 " + order.getOrderNo());

            StringBuilder sb = new StringBuilder("\n\n✅ 下单成功！\n");
            sb.append("• 订单号：**").append(order.getOrderNo()).append("**\n");
            sb.append("• 商品：").append(join(itemsDesc)).append("\n");
            sb.append("• 金额：¥").append(order.getTotalAmount()).append("\n");
            sb.append("• 收货地址：").append(addressText(address)).append("\n");
            sb.append("• 当前状态：待付款\n\n");
            sb.append("请到「我的订单」完成支付，付款后我就安排发货～");
            result.put("displayText", sb.toString());
        } catch (BusinessException e) {
            result.put("ok", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "下单失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询订单列表
     * @param status 订单状态（0待付款 1待发货 2待收货 3已完成 4已取消 5已退款），null 表示全部
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listOrders(Long userId, Integer status, int limit) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("ok", false);
            result.put("message", "请先登录后再查看订单哦～");
            return result;
        }
        try {
            Map<String, Object> page = orderService.getUserOrders(userId, 1, limit, status);
            List<Order> list = (List<Order>) page.get("list");
            Object totalObj = page.get("total");
            int total = totalObj == null ? (list == null ? 0 : list.size()) : Integer.parseInt(totalObj.toString());

            result.put("ok", true);
            result.put("orders", list);
            result.put("total", total);

            if (list == null || list.isEmpty()) {
                String scoped = status == null ? "" : "【" + statusLabel(status) + "】";
                result.put("displayText", "\n\n📦 您目前没有" + scoped + "订单哦～\n想买东西直接告诉我，比如\"帮我买一个 iPhone\"，我帮您下单。");
                return result;
            }

            StringBuilder sb = new StringBuilder("\n\n📦 您共有 ");
            sb.append(total).append(" 个订单").append(status == null ? "" : "（" + statusLabel(status) + "）");
            sb.append("，最近 ").append(list.size()).append(" 个如下：\n");
            int idx = 1;
            for (Order o : list) {
                sb.append(idx++).append(". 订单号 **").append(o.getOrderNo()).append("**\n");
                sb.append("   状态：").append(statusLabel(o.getStatus()));
                sb.append(" ｜ 金额：¥").append(o.getTotalAmount() == null ? "0" : o.getTotalAmount());
                if (o.getCreateTime() != null) sb.append(" ｜ 下单时间：").append(TIME_FMT.format(o.getCreateTime()));
                sb.append("\n");
                String items = describeItems(o.getItems());
                if (!items.isEmpty()) sb.append("   商品：").append(items).append("\n");
            }
            sb.append("\n想支付某个订单，可以直接说\"帮我支付订单\"～");
            result.put("displayText", sb.toString());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "查询订单失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询可支付的待付款订单（只查不付，用于让用户二次确认）
     * @param orderNo 订单号；为空时取最近一笔待付款订单
     */
    public Map<String, Object> findPayableOrder(Long userId, String orderNo) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("ok", false);
            result.put("message", "请先登录后再支付哦～");
            return result;
        }
        try {
            Order order = null;
            if (!isBlank(orderNo)) {
                order = orderService.getOrderDetailByOrderNo(orderNo.trim(), userId, "USER");
            } else {
                Map<String, Object> page = orderService.getUserOrders(userId, 1, 20, 0);
                @SuppressWarnings("unchecked")
                List<Order> unpaid = (List<Order>) page.get("list");
                if (unpaid != null && !unpaid.isEmpty()) order = unpaid.get(0);
            }
            if (order == null) {
                result.put("ok", false);
                result.put("message", "没有找到待付款的订单");
                result.put("displayText", "\n\n💡 您当前没有待付款的订单哦～ 想买点什么可以直接告诉我。");
                return result;
            }
            if (order.getStatus() == null || order.getStatus() != 0) {
                result.put("ok", false);
                result.put("message", "订单当前状态不可支付");
                result.put("displayText", "\n\n订单 **" + order.getOrderNo() + "** 当前状态是【" + statusLabel(order.getStatus()) + "】，不需要再支付啦～");
                return result;
            }
            result.put("ok", true);
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("totalAmount", order.getPayAmount() == null ? order.getTotalAmount() : order.getPayAmount());
            result.put("status", order.getStatus());
            return result;
        } catch (BusinessException e) {
            result.put("ok", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "查询订单失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 生成支付入口（用户二次确认后调用）
     * 注意：这里**不会**直接把订单改成已支付，而是返回支付信息，
     * 由前端调用 /api/payment/alipay/create/{orderId} 打开支付宝沙箱收银台，
     * 真正支付成功后由支付宝回调（或主动查询）更新订单状态。
     */
    public Map<String, Object> createPayEntry(Long userId, String orderNo) {
        Map<String, Object> found = findPayableOrder(userId, orderNo);
        if (!Boolean.TRUE.equals(found.get("ok"))) {
            return found;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("needPay", true);          // 前端据此打开支付宝沙箱收银台
        result.put("paid", false);
        result.put("orderId", found.get("orderId"));
        result.put("orderNo", found.get("orderNo"));
        result.put("totalAmount", found.get("totalAmount"));
        result.put("payMethod", "支付宝沙箱");
        result.put("message", "待支付订单已确认，请在支付宝沙箱完成付款");
        result.put("displayText", "\n\n💳 待支付订单已确认\n• 订单号：**" + found.get("orderNo")
                + "**\n• 应付金额：¥" + found.get("totalAmount")
                + "\n• 支付方式：支付宝沙箱\n\n请点击下方「去支付宝沙箱支付」按钮，在新窗口完成登录/扫码付款。"
                + "付款完成后对我说\"查一下订单\"，我帮您核对支付状态～");
        return result;
    }

    // ===== 内部辅助 =====

    private Address pickAddress(Long userId) {
        List<Address> list = addressService.getAddressList(userId);
        if (list == null || list.isEmpty()) return null;
        for (Address a : list) {
            if (a.getIsDefault() != null && a.getIsDefault() == 1) return a;
        }
        return list.get(0);
    }

    private String addressText(Address a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        if (a.getReceiver() != null) sb.append(a.getReceiver());
        if (a.getPhone() != null) sb.append(" ").append(a.getPhone());
        if (a.getProvince() != null) sb.append(" ").append(a.getProvince());
        if (a.getCity() != null) sb.append(a.getCity());
        if (a.getDistrict() != null) sb.append(a.getDistrict());
        if (a.getDetail() != null) sb.append(a.getDetail());
        return sb.toString().trim();
    }

    private Product findProductByName(String name) {
        if (isBlank(name)) return null;
        String q = name.trim();
        List<Product> list = searchProducts(q);
        // "iQOO 11" / "IQOO-11" / "iqoo11" 视为同一个型号
        if (list == null || list.isEmpty()) {
            list = searchProducts(AiTextUtil.normalize(q));
        }
        if (list == null) return null;
        for (Product p : list) {
            if (AiTextUtil.sameProduct(p.getName(), q)) return p;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Product> searchProducts(String keyword) {
        Map<String, Object> page = productService.search(keyword, 1, 10, null);
        if (page == null) return null;
        return (List<Product>) page.get("list");
    }

    private String describeItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (OrderItem it : items) {
            parts.add(shortName(it.getProductName()) + " ×" + it.getQuantity());
            if (parts.size() >= 3) break;
        }
        return join(parts);
    }

    private String shortName(String name) {
        if (name == null) return "商品";
        return name.length() > 24 ? name.substring(0, 24) + "…" : name;
    }

    /**
     * 查商品规格
     * 先走 search 找到商品 → 再 getById 才能拿到 SKU 列表
     */
    public Map<String, Object> getProductSpecs(String productName) {
        Map<String, Object> result = new HashMap<>();
        Product product = findProductByName(productName);
        if (product == null) {
            result.put("ok", false);
            result.put("message", "没有找到商品: " + productName);
            result.put("displayText", "抱歉，我没在本店找到「" + productName + "」哦～");
            return result;
        }
        // search 不带 SKU，必须 getById 才能拿
        Product detail = productService.getById(product.getId());
        if (detail == null) {
            result.put("ok", false);
            result.put("message", "商品详情获取失败");
            result.put("displayText", "抱歉，商品详情加载失败，请稍后再试～");
            return result;
        }
        List<ProductSku> skus = detail.getSkus();

        StringBuilder text = new StringBuilder();
        text.append("📦 【").append(detail.getName()).append("】");
        text.append("\n💰 统一价：¥").append(detail.getPrice()).append("   库存：").append(detail.getStock());

        if (skus == null || skus.isEmpty()) {
            text.append("\n\n这个商品目前只有单一规格，没有颜色、内存等可选版本～");
        } else {
            // 先聚合规格维度（颜色 / 容量 / 尺码 等）
            Map<String, List<String>> dims = new LinkedHashMap<>();
            for (ProductSku sku : skus) {
                if (sku.getSpecMap() != null) {
                    for (Map.Entry<String, String> e : sku.getSpecMap().entrySet()) {
                        dims.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                        List<String> vals = dims.get(e.getKey());
                        if (!vals.contains(e.getValue())) vals.add(e.getValue());
                    }
                }
            }
            text.append("\n\n**规格维度**：");
            for (Map.Entry<String, List<String>> e : dims.entrySet()) {
                text.append("\n  ").append(e.getKey()).append("：");
                text.append(String.join(" / ", e.getValue()));
            }
            text.append("\n\n**各版本详情**：");
            int i = 1;
            for (ProductSku sku : skus) {
                text.append("\n  ").append(i++).append(". ");
                text.append(sku.getSpecDesc());
                text.append("  ¥").append(sku.getPrice());
                text.append("  库存 ").append(sku.getStock());
            }
        }

        result.put("ok", true);
        result.put("productId", detail.getId());
        result.put("productName", detail.getName());
        result.put("skuCount", skus == null ? 0 : skus.size());
        result.put("displayText", text.toString());
        return result;
    }

    private String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append("、");
            sb.append(p);
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String statusLabel(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待付款";
            case 1: return "待发货";
            case 2: return "待收货";
            case 3: return "已完成";
            case 4: return "已取消";
            case 5: return "已退款";
            default: return "状态" + status;
        }
    }

    /** 从用户原话里识别订单状态筛选条件 */
    public static Integer detectStatusFilter(String msg) {
        if (msg == null) return null;
        if (msg.contains("待付款") || msg.contains("未付款") || msg.contains("未支付")) return 0;
        if (msg.contains("待发货") || msg.contains("没发货")) return 1;
        if (msg.contains("待收货") || msg.contains("运输中") || msg.contains("已发货")) return 2;
        if (msg.contains("已完成")) return 3;
        if (msg.contains("已取消")) return 4;
        if (msg.contains("退款")) return 5;
        return null;
    }
}
