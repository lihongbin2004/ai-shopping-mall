package com.mall.mapper;

import com.mall.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CouponMapper {
    Coupon selectById(Long id);
    List<Coupon> selectList();
    List<Coupon> selectUsable();
    int insert(Coupon coupon);
    int update(Coupon coupon);
    int deleteById(Long id);
    int reduceStock(Long id);
}
