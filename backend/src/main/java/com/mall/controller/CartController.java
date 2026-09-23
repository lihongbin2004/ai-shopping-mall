package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Cart;
import com.mall.service.CartService;
import com.mall.service.impl.RecommendServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@Api(tags = "购物车接口")
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired(required = false)
    private RecommendServiceImpl recommendService;

    @GetMapping("/list")
    @ApiOperation("获取购物车列表")
    public R<List<Cart>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(cartService.getCartList(userId));
    }

    @GetMapping("/count")
    @ApiOperation("获取购物车商品总数量（Redis 缓存 5 分钟）")
    public R<Integer> count(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(cartService.getCartCount(userId));
    }

    @PostMapping("/add")
    @ApiOperation("添加购物车")
    public R<Cart> add(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long productId = Long.valueOf(params.get("productId").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());
        Long skuId = null;
        if (params.get("skuId") != null && !params.get("skuId").toString().isEmpty()) {
            skuId = Long.valueOf(params.get("skuId").toString());
        }
        Cart c = cartService.addItem(userId, productId, skuId, quantity);
        if (userId != null && recommendService != null) {
            try { recommendService.trackCart(userId, productId); } catch (Exception ignore) {}
        }
        return R.ok(c);
    }

    @PutMapping("/update")
    @ApiOperation("更新购物车")
    public R<Cart> update(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        if (params.containsKey("quantity")) {
            Integer quantity = Integer.valueOf(params.get("quantity").toString());
            return R.ok(cartService.updateQuantity(id, quantity));
        }
        if (params.containsKey("checked")) {
            Integer checked = Integer.valueOf(params.get("checked").toString());
            return R.ok(cartService.updateChecked(id, checked));
        }
        throw new com.mall.common.exception.BusinessException("缺少更新字段");
    }

    @PutMapping("/check/all")
    @ApiOperation("全选/全不选购物车")
    public R<Void> checkAll(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer checked = Integer.valueOf(params.get("checked").toString());
        cartService.updateAllChecked(userId, checked);
        return R.ok();
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除购物车项")
    public R<Void> delete(@PathVariable Long id) {
        cartService.deleteItem(id);
        return R.ok();
    }

    @DeleteMapping("/clear")
    @ApiOperation("清空购物车")
    public R<Void> clear(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        cartService.clearCart(userId);
        return R.ok();
    }
}
