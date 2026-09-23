package com.mall.service;

import com.mall.entity.Cart;
import java.util.List;

public interface CartService {
    List<Cart> getCartList(Long userId);
    /** 加购物车（带 SKU） */
    Cart addItem(Long userId, Long productId, Long skuId, Integer quantity);
    /** 加购物车（无 SKU 兼容） */
    Cart addItem(Long userId, Long productId, Integer quantity);
    Cart updateQuantity(Long id, Integer quantity);
    Cart updateChecked(Long id, Integer checked);
    void updateAllChecked(Long userId, Integer checked);
    void deleteItem(Long id);
    void clearCart(Long userId);
    List<Cart> getBatch(List<Long> ids);
    /** 获取购物车商品总数量（Redis 缓存 5 分钟） */
    Integer getCartCount(Long userId);
}
