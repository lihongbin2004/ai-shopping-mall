package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Coupon;
import com.mall.entity.UserCoupon;
import com.mall.mapper.CouponMapper;
import com.mall.mapper.UserCouponMapper;
import com.mall.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Override
    public List<Coupon> getCouponList() {
        return couponMapper.selectList();
    }

    @Override
    public Coupon createCoupon(Coupon coupon) {
        coupon.setCreateTime(new Date());
        if (coupon.getStatus() == null) {
            coupon.setStatus(1);
        }
        couponMapper.insert(coupon);
        return coupon;
    }

    @Override
    public Coupon updateCoupon(Coupon coupon) {
        Coupon existing = couponMapper.selectById(coupon.getId());
        if (existing == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (coupon.getName() != null) existing.setName(coupon.getName());
        if (coupon.getAmount() != null) existing.setAmount(coupon.getAmount());
        if (coupon.getMinAmount() != null) existing.setMinAmount(coupon.getMinAmount());
        if (coupon.getStock() != null) existing.setStock(coupon.getStock());
        if (coupon.getStartTime() != null) existing.setStartTime(coupon.getStartTime());
        if (coupon.getEndTime() != null) existing.setEndTime(coupon.getEndTime());
        if (coupon.getStatus() != null) existing.setStatus(coupon.getStatus());
        couponMapper.update(existing);
        return existing;
    }

    @Override
    public void deleteCoupon(Long id) {
        couponMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCoupon receiveCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        // 校验优惠券状态
        if (coupon.getStatus() != null && coupon.getStatus() == 0) {
            throw new BusinessException("该优惠券已禁用，无法领取");
        }
        Date now = new Date();
        if (now.before(coupon.getStartTime()) || now.after(coupon.getEndTime())) {
            throw new BusinessException("不在领取时间范围内");
        }
        if (coupon.getStock() <= 0) {
            throw new BusinessException("优惠券已被领完");
        }
        int count = userCouponMapper.countByUserIdAndCouponId(userId, couponId);
        if (count > 0) {
            throw new BusinessException("您已领取过该优惠券");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(0);
        userCouponMapper.insert(userCoupon);
        couponMapper.reduceStock(couponId);
        return userCoupon;
    }

    @Override
    public List<UserCoupon> getMyCoupons(Long userId, Integer status) {
        List<UserCoupon> list = userCouponMapper.selectByUserId(userId, status);
        for (UserCoupon uc : list) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null) {
                uc.setCouponName(coupon.getName());
                uc.setAmount(coupon.getAmount());
                uc.setMinAmount(coupon.getMinAmount());
                uc.setStartTime(coupon.getStartTime());
                uc.setEndTime(coupon.getEndTime());
            }
        }
        return list;
    }

    @Override
    public List<UserCoupon> getUsableCoupons(Long userId, BigDecimal amount) {
        List<UserCoupon> list = userCouponMapper.selectByUserId(userId, 0);
        List<UserCoupon> usable = new ArrayList<>();
        for (UserCoupon uc : list) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null) {
                // 过滤已禁用的优惠券
                if (coupon.getStatus() != null && coupon.getStatus() == 0) {
                    continue;
                }
                Date now = new Date();
                if (now.after(coupon.getStartTime()) && now.before(coupon.getEndTime())
                        && amount.compareTo(coupon.getMinAmount()) >= 0) {
                    uc.setCouponName(coupon.getName());
                    uc.setAmount(coupon.getAmount());
                    uc.setMinAmount(coupon.getMinAmount());
                    uc.setStartTime(coupon.getStartTime());
                    uc.setEndTime(coupon.getEndTime());
                    usable.add(uc);
                }
            }
        }
        return usable;
    }
}
