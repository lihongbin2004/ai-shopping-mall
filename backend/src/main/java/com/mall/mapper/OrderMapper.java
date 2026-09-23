package com.mall.mapper;

import com.mall.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;

@Mapper
public interface OrderMapper {
    Order selectById(Long id);
    Order selectByOrderNo(String orderNo);
    List<Order> selectByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);
    List<Order> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status, @Param("offset") int offset, @Param("limit") int limit);
    int countByUserId(Long userId);
    int countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);
    java.math.BigDecimal sumPaidAmountByUserId(Long userId);
    List<Order> selectByStatus(@Param("status") Integer status, @Param("offset") int offset, @Param("limit") int limit);
    int countByStatus(@Param("status") Integer status);
    List<Order> selectAll(@Param("offset") int offset, @Param("limit") int limit);
    int countAll();
    List<Order> selectByOrderNoLike(@Param("orderNo") String orderNo, @Param("offset") int offset, @Param("limit") int limit);
    int countByOrderNoLike(@Param("orderNo") String orderNo);
    List<Order> selectByStatusAndOrderNoLike(@Param("status") Integer status, @Param("orderNo") String orderNo, @Param("offset") int offset, @Param("limit") int limit);
    int countByStatusAndOrderNoLike(@Param("status") Integer status, @Param("orderNo") String orderNo);
    java.math.BigDecimal sumRevenue();
    int insert(Order order);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int updatePayTime(@Param("id") Long id, @Param("status") Integer status, @Param("payTime") Date payTime);
    int updateFinishTime(@Param("id") Long id, @Param("status") Integer status, @Param("finishTime") Date finishTime);
    int deleteById(Long id);
}
