package com.mall.mapper;

import com.mall.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductSkuMapper {
    List<ProductSku> selectByProductId(@Param("productId") Long productId);
    ProductSku selectById(Long id);
    int updateStock(@Param("id") Long id, @Param("delta") int delta);
    int reduceSales(@Param("id") Long id, @Param("quantity") int quantity);
    int addSales(@Param("id") Long id, @Param("quantity") int quantity);
}
