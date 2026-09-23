package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Product;
import com.mall.service.RecommendService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个性化推荐接口
 *
 *  GET /api/recommend/for-you?userId=1&limit=8
 *  GET /api/recommend/related?productId=10&limit=6
 *  GET /api/recommend/hot?limit=8
 */
@RestController
@RequestMapping("/api/recommend")
@Api(tags = "推荐接口")
public class RecommendController {

    @Autowired
    private RecommendService recommendService;

    @GetMapping("/for-you")
    @ApiOperation("猜你喜欢（个性化推荐）")
    public R<Map<String, Object>> forYou(@RequestParam(required = false) Long userId,
                                         @RequestParam(defaultValue = "8") int limit) {
        return R.ok(recommendService.forYouWithMeta(userId, limit));
    }

    @GetMapping("/related")
    @ApiOperation("看了又看（相关商品）")
    public R<List<Product>> related(@RequestParam Long productId,
                                    @RequestParam(defaultValue = "8") int limit) {
        return R.ok(recommendService.related(productId, limit));
    }

    @GetMapping("/hot")
    @ApiOperation("首页热门（兜底推荐）")
    public R<List<Product>> hot(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(recommendService.hotTop(limit));
    }
}
