package com.mall.mapper;

import com.mall.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReviewMapper {
    Review selectById(Long id);
    List<Review> selectByProductId(@Param("productId") Long productId, @Param("offset") int offset, @Param("limit") int limit);
    int countByProductId(Long productId);
    List<Review> selectByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);
    int countByUserId(Long userId);
    int insert(Review review);
    int update(Review review);
    int deleteById(@Param("id") Long id);
    Double avgRatingByProductId(@Param("productId") Long productId);
    Review selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /** 检查用户是否有已完成订单（status=3）包含指定商品 */
    int countCompletedOrderWithProduct(@Param("userId") Long userId, @Param("productId") Long productId);
}
