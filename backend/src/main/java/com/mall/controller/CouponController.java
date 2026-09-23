package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Coupon;
import com.mall.entity.UserCoupon;
import com.mall.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
@Api(tags = "优惠券接口")
public class CouponController {

    @Autowired
    private CouponService couponService;

    @GetMapping("/list")
    @ApiOperation("优惠券列表")
    public R<List<Coupon>> list() {
        return R.ok(couponService.getCouponList());
    }

    @PostMapping("/create")
    @ApiOperation("创建优惠券")
    public R<Coupon> create(@RequestBody Coupon coupon) {
        return R.ok(couponService.createCoupon(coupon));
    }

    @PutMapping("/update")
    @ApiOperation("更新优惠券")
    public R<Coupon> update(@RequestBody Coupon coupon) {
        return R.ok(couponService.updateCoupon(coupon));
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除优惠券")
    public R<Void> delete(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return R.ok();
    }

    @PostMapping("/receive/{couponId}")
    @ApiOperation("领取优惠券")
    public R<UserCoupon> receive(@PathVariable Long couponId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(couponService.receiveCoupon(userId, couponId));
    }

    @GetMapping("/my")
    @ApiOperation("我的优惠券")
    public R<List<UserCoupon>> myCoupons(HttpServletRequest request,
                                         @RequestParam(required = false) Integer status) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(couponService.getMyCoupons(userId, status));
    }

    @GetMapping("/usable")
    @ApiOperation("可用优惠券")
    public R<List<UserCoupon>> usableCoupons(HttpServletRequest request,
                                             @RequestParam BigDecimal amount) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(couponService.getUsableCoupons(userId, amount));
    }
}
