package com.mall.mapper;

import com.mall.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductMapper {
    Product selectById(Long id);
    List<Product> selectPage(@Param("offset") int offset, @Param("limit") int limit, @Param("sortBy") String sortBy);
    int countAll();
    List<Product> selectByCategoryId(@Param("categoryId") Long categoryId, @Param("offset") int offset, @Param("limit") int limit, @Param("sortBy") String sortBy);
    int countByCategoryId(Long categoryId);
    List<Product> searchByName(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit, @Param("sortBy") String sortBy);
    int countSearch(String keyword);
    List<Product> selectByCategoryName(@Param("keyword") String keyword, @Param("limit") int limit);
    List<Product> selectByPrice(@Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("limit") int limit);
    int insert(Product product);
    int update(Product product);
    int deleteById(Long id);
    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);
    int addSales(@Param("id") Long id, @Param("quantity") Integer quantity);
    int reduceSales(@Param("id") Long id, @Param("quantity") Integer quantity);
    /** 推荐召回：按分类集合 + 排除商品集合查商品，按销量降序 */
    List<Product> selectByCategoryIdsExclude(@Param("categoryIds") List<Long> categoryIds,
                                             @Param("excludeIds") List<Long> excludeIds,
                                             @Param("limit") int limit);
}
