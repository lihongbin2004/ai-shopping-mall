package com.mall.mapper;

import com.mall.entity.Cart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CartMapper {
    Cart selectById(Long id);
    Cart selectByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    Cart selectByUserIdAndProductIdAndSkuId(@Param("userId") Long userId, @Param("productId") Long productId, @Param("skuId") Long skuId);
    List<Cart> selectByUserId(Long userId);
    Integer countByUserId(Long userId);
    int insert(Cart cart);
    int update(Cart cart);
    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);
    int updateChecked(@Param("id") Long id, @Param("checked") Integer checked);
    int updateAllChecked(@Param("userId") Long userId, @Param("checked") Integer checked);
    int deleteById(Long id);
    int deleteByUserId(Long userId);
    /** 用户购物车里出现过的商品 ID 集合（去重），用于推荐打分/去重 */
    java.util.Set<Long> selectProductIdsByUserId(@Param("userId") Long userId);
    int deleteBatch(@Param("ids") List<Long> ids);
}
