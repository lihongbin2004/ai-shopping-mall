package com.mall.controller;

import com.mall.common.result.R;
import com.mall.service.FavoriteService;
import com.mall.service.impl.RecommendServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/favorite")
@Api(tags = "收藏接口")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @Autowired(required = false)
    private RecommendServiceImpl recommendService;

    @PostMapping("/add/{productId}")
    @ApiOperation("添加收藏")
    public R<Void> add(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteService.addFavorite(userId, productId);
        if (userId != null && recommendService != null) {
            try { recommendService.trackFavorite(userId, productId); } catch (Exception ignore) {}
        }
        return R.ok();
    }

    @DeleteMapping("/cancel/{productId}")
    @ApiOperation("取消收藏")
    public R<Void> cancel(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteService.cancelFavorite(userId, productId);
        return R.ok();
    }

    @GetMapping("/list")
    @ApiOperation("收藏列表")
    public R<Map<String, Object>> list(HttpServletRequest request,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(favoriteService.getFavoriteList(userId, page, size));
    }

    @GetMapping("/check/{productId}")
    @ApiOperation("检查是否收藏")
    public R<Boolean> check(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(favoriteService.checkFavorite(userId, productId));
    }
}
