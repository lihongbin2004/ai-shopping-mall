package com.mall.mapper;

import com.mall.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FavoriteMapper {
    Favorite selectById(Long id);
    Favorite selectByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    List<Favorite> selectByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);
    int countByUserId(Long userId);
    int insert(Favorite favorite);
    int deleteById(Long id);
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    /** 用户收藏过的商品 ID 集合 */
    java.util.Set<Long> selectProductIdsByUserId(@Param("userId") Long userId);
}
