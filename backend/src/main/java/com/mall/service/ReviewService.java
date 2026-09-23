package com.mall.service;

import com.mall.entity.Review;
import java.util.Map;

public interface ReviewService {
    Review createReview(Review review);
    Map<String, Object> getReviewsByProduct(Long productId, int page, int size);
    Map<String, Object> getReviewsByUser(Long userId, int page, int size);
    void updateReview(Long id, Long userId, Review review);
    void deleteReview(Long id, Long userId);
    Double getAverageRating(Long productId);
    Review getUserReview(Long userId, Long productId);

    /** 检查用户是否可以评价某商品（必须有已完成订单包含该商品） */
    boolean canUserReviewProduct(Long userId, Long productId);
}
