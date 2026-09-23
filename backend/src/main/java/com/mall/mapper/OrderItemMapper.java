package com.mall.mapper;

import com.mall.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OrderItemMapper {
    List<OrderItem> selectByOrderId(Long orderId);
    int insert(OrderItem item);
    int insertBatch(List<OrderItem> items);
    int deleteByOrderId(Long orderId);
    /** 查询用户已购商品 ID 集合（去重），用于推荐去重/打分 */
    java.util.Set<Long> selectBoughtProductIdsByUserId(@Param("userId") Long userId);
}
