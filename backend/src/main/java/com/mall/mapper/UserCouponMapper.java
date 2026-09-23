package com.mall.mapper;

import com.mall.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserCouponMapper {
    UserCoupon selectById(Long id);
    List<UserCoupon> selectByUserId(@Param("userId") Long userId, @Param("status") Integer status);
    int insert(UserCoupon userCoupon);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("usedTime") java.util.Date usedTime);
    int countByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);
}
