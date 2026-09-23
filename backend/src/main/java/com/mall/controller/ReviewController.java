package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Review;
import com.mall.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
@Api(tags = "评价接口")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping("/create")
    @ApiOperation("添加评价")
    public R<Review> create(@RequestBody Review review, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        review.setUserId(userId);
        return R.ok(reviewService.createReview(review));
    }

    @GetMapping("/product/{productId}")
    @ApiOperation("查询商品评价")
    public R<Map<String, Object>> listByProduct(@PathVariable Long productId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return R.ok(reviewService.getReviewsByProduct(productId, page, size));
    }

    @GetMapping("/user")
    @ApiOperation("查询用户评价")
    public R<Map<String, Object>> listByUser(HttpServletRequest request,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(reviewService.getReviewsByUser(userId, page, size));
    }

    @PutMapping("/update/{id}")
    @ApiOperation("更新评价")
    public R<Void> update(@PathVariable Long id, @RequestBody Review review, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        reviewService.updateReview(id, userId, review);
        return R.ok();
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除评价")
    public R<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        reviewService.deleteReview(id, userId);
        return R.ok();
    }

    @GetMapping("/avg/{productId}")
    @ApiOperation("商品平均评分")
    public R<Double> avgRating(@PathVariable Long productId) {
        return R.ok(reviewService.getAverageRating(productId));
    }

    @GetMapping("/check")
    @ApiOperation("查询当前用户对商品的评价")
    public R<Review> checkUserReview(@RequestParam Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(reviewService.getUserReview(userId, productId));
    }

    @GetMapping("/check-can-review")
    @ApiOperation("检查当前用户是否可以评价某商品")
    public R<Boolean> checkCanReview(@RequestParam Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(reviewService.canUserReviewProduct(userId, productId));
    }
}
