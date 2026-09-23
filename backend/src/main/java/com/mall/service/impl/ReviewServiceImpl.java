package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Review;
import com.mall.entity.User;
import com.mall.mapper.ReviewMapper;
import com.mall.mapper.UserMapper;
import com.mall.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Review createReview(Review review) {
        // 校验：只有已完成订单包含该商品的用户才能评价
        boolean canReview = canUserReviewProduct(review.getUserId(), review.getProductId());
        if (!canReview) {
            throw new BusinessException("请先完成订单后再评价该商品");
        }
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessException("评分必须在1-5之间");
        }
        review.setCreateTime(new Date());
        reviewMapper.insert(review);
        User user = userMapper.selectById(review.getUserId());
        if (user != null) {
            review.setUsername(user.getUsername());
        }
        return review;
    }

    @Override
    public Map<String, Object> getReviewsByProduct(Long productId, int page, int size) {
        int offset = (page - 1) * size;
        List<Review> list = reviewMapper.selectByProductId(productId, offset, size);
        for (Review review : list) {
            User user = userMapper.selectById(review.getUserId());
            if (user != null) {
                review.setUsername(user.getUsername());
            }
        }
        int total = reviewMapper.countByProductId(productId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> getReviewsByUser(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Review> list = reviewMapper.selectByUserId(userId, offset, size);
        int total = reviewMapper.countByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public void updateReview(Long id, Long userId, Review review) {
        Review existing = reviewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评价不存在");
        }
        if (userId != null && !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权修改该评价");
        }
        if (review.getRating() != null) {
            if (review.getRating() < 1 || review.getRating() > 5) {
                throw new BusinessException("评分必须在1-5之间");
            }
            existing.setRating(review.getRating());
        }
        if (review.getContent() != null) {
            existing.setContent(review.getContent());
        }
        reviewMapper.update(existing);
    }

    @Override
    public void deleteReview(Long id, Long userId) {
        Review existing = reviewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评价不存在");
        }
        if (userId != null && !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该评价");
        }
        reviewMapper.deleteById(id);
    }

    @Override
    public Double getAverageRating(Long productId) {
        return reviewMapper.avgRatingByProductId(productId);
    }

    @Override
    public Review getUserReview(Long userId, Long productId) {
        return reviewMapper.selectByUserAndProduct(userId, productId);
    }

    @Override
    public boolean canUserReviewProduct(Long userId, Long productId) {
        // 1. 检查是否有已完成订单包含该商品
        int completedOrders = reviewMapper.countCompletedOrderWithProduct(userId, productId);
        if (completedOrders <= 0) {
            return false;
        }
        // 2. 检查是否已经评价过（同一用户对同一商品只能评价一次）
        Review existing = reviewMapper.selectByUserAndProduct(userId, productId);
        return existing == null;
    }
}
