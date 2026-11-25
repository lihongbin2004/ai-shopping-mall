package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Cart;
import com.mall.entity.Product;
import com.mall.entity.ProductSku;
import com.mall.mapper.CartMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    private static final String CART_COUNT_KEY = "cart:count:user:";
    private static final long CART_COUNT_TTL_MIN = 5;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 失效用户购物车数量缓存 */
    private void invalidateCount(Long userId) {
        if (userId == null || redis == null) return;
        redis.delete(CART_COUNT_KEY + userId);
    }

    @Override
    public List<Cart> getCartList(Long userId) {
        List<Cart> list = cartMapper.selectByUserId(userId);
        for (Cart cart : list) {
            Product product = productMapper.selectById(cart.getProductId());
            if (product != null) {
                cart.setProductName(product.getName());
                cart.setProductImage(product.getImage());
                cart.setProductStatus(product.getStatus());
            }
            // 填充 SKU 级别的价格、库存、规格描述
            if (cart.getSkuId() != null) {
                ProductSku sku = productSkuMapper.selectById(cart.getSkuId());
                if (sku != null) {
                    cart.setProductPrice(sku.getPrice());
                    cart.setStock(sku.getStock());
                    cart.setProductImage(sku.getImage() != null ? sku.getImage() : product.getImage());
                    cart.setSpecDesc(buildSpecDesc(sku.getSpecs()));
                } else {
                    cart.setProductPrice(product.getPrice());
                    cart.setStock(product.getStock());
                }
            } else {
                cart.setProductPrice(product != null ? product.getPrice() : null);
                cart.setStock(product != null ? product.getStock() : null);
            }
        }
        return list;
    }

    @Override
    public Cart addItem(Long userId, Long productId, Long skuId, Integer quantity) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        ProductSku sku = null;
        if (skuId != null) {
            sku = productSkuMapper.selectById(skuId);
            if (sku == null || !sku.getProductId().equals(productId)) {
                throw new BusinessException("SKU 不存在或不属于该商品");
            }
            if (sku.getStock() < quantity) {
                throw new BusinessException("SKU 库存不足，仅剩 " + sku.getStock() + " 件");
            }
        } else {
            if (product.getStock() < quantity) {
                throw new BusinessException("库存不足，仅剩 " + product.getStock() + " 件");
            }
        }

        // 同 product + 同 sku 合并
        Cart existing = cartMapper.selectByUserIdAndProductIdAndSkuId(userId, productId, skuId);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            cartMapper.update(existing);
            invalidateCount(userId);
            return existing;
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSkuId(skuId);
        cart.setQuantity(quantity);
        cart.setCreateTime(new Date());
        cartMapper.insert(cart);
        invalidateCount(userId);
        return cart;
    }

    /** 无 SKU 兼容方法 */
    @Override
    public Cart addItem(Long userId, Long productId, Integer quantity) {
        return addItem(userId, productId, null, quantity);
    }

    @Override
    public Cart updateQuantity(Long id, Integer quantity) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BusinessException("购物车记录不存在");
        }
        cart.setQuantity(quantity);
        cartMapper.update(cart);
        invalidateCount(cart.getUserId());
        return cart;
    }

    @Override
    public Cart updateChecked(Long id, Integer checked) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BusinessException("购物车记录不存在");
        }
        cart.setChecked(checked);
        cartMapper.updateChecked(id, checked);
        return cart;
    }

    @Override
    public void updateAllChecked(Long userId, Integer checked) {
        cartMapper.updateAllChecked(userId, checked);
    }

    @Override
    public void deleteItem(Long id) {
        Cart cart = cartMapper.selectById(id);
        cartMapper.deleteById(id);
        if (cart != null) invalidateCount(cart.getUserId());
    }

    @Override
    public void clearCart(Long userId) {
        cartMapper.deleteByUserId(userId);
        invalidateCount(userId);
    }

    @Override
    public List<Cart> getBatch(List<Long> ids) {
        return null;
    }

    @Override
    public Integer getCartCount(Long userId) {
        if (userId == null) return 0;
        if (redis == null) {
            return cartMapper.countByUserId(userId);
        }
        String key = CART_COUNT_KEY + userId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return Integer.valueOf(cached);
        }
        Integer count = cartMapper.countByUserId(userId);
        redis.opsForValue().set(key, String.valueOf(count), CART_COUNT_TTL_MIN, TimeUnit.MINUTES);
        return count;
    }

    /** 把 specs JSON 转成可读字符串，如 "颜色:黑, 容量:256G" */
    private String buildSpecDesc(String specsJson) {
        if (specsJson == null || specsJson.isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = objectMapper.readValue(specsJson, HashMap.class);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append(": ").append(e.getValue());
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
