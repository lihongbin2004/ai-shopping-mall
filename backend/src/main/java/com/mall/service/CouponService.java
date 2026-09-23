package com.mall.service;

import com.mall.entity.Coupon;
import com.mall.entity.UserCoupon;
import java.util.List;

public interface CouponService {
    List<Coupon> getCouponList();
    Coupon createCoupon(Coupon coupon);
    Coupon updateCoupon(Coupon coupon);
    void deleteCoupon(Long id);
    UserCoupon receiveCoupon(Long userId, Long couponId);
    List<UserCoupon> getMyCoupons(Long userId, Integer status);
    List<UserCoupon> getUsableCoupons(Long userId, java.math.BigDecimal amount);
}
