package com.mall.service;

import com.mall.entity.Product;
import java.util.List;
import java.util.Map;

public interface ProductService {
    Map<String, Object> getPage(int page, int size, String sortBy);
    Map<String, Object> getByCategoryId(Long categoryId, int page, int size, String sortBy);
    Map<String, Object> search(String keyword, int page, int size, String sortBy);
    List<Product> searchByCategoryName(String keyword, int size);
    List<Product> searchByPrice(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, int size);
    Product getById(Long id);
    Product create(Product product);
    Product update(Product product);
    void delete(Long id);
}
