package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Category;
import com.mall.entity.Product;
import com.mall.entity.ProductSku;
import com.mall.mapper.CategoryMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 给商品填充 SKU 列表 + 规格维度聚合 */
    private void fillSkus(Product product) {
        if (product == null) return;
        List<ProductSku> skus = productSkuMapper.selectByProductId(product.getId());
        // 解析每个 SKU 的 specs JSON
        for (ProductSku sku : skus) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> map = objectMapper.readValue(sku.getSpecs(), HashMap.class);
                sku.setSpecMap(map);
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> e : map.entrySet()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(e.getKey()).append(": ").append(e.getValue());
                }
                sku.setSpecDesc(sb.toString());
            } catch (Exception ignored) {}
        }
        product.setSkus(skus);

        // 聚合规格维度：["颜色", "容量"] + 每个维度的可选值
        Map<String, List<String>> specOptions = new LinkedHashMap<>();
        for (ProductSku sku : skus) {
            if (sku.getSpecMap() != null) {
                for (Map.Entry<String, String> e : sku.getSpecMap().entrySet()) {
                    specOptions.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                    List<String> vals = specOptions.get(e.getKey());
                    if (!vals.contains(e.getValue())) vals.add(e.getValue());
                }
            }
        }
        product.setSpecKeys(new ArrayList<>(specOptions.keySet()));
        product.setSpecOptions(specOptions);
    }

    /**
     * 批量回填商品列表的分类名（仅当商品有 categoryId 时填充 categoryName）
     */
    private void fillCategoryName(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        List<Long> ids = products.stream()
                .map(Product::getCategoryId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;
        Map<Long, String> nameMap = new HashMap<>();
        for (Long id : ids) {
            Category c = categoryMapper.selectById(id);
            if (c != null) nameMap.put(id, c.getName());
        }
        for (Product p : products) {
            if (p.getCategoryId() != null && nameMap.containsKey(p.getCategoryId())) {
                p.setCategoryName(nameMap.get(p.getCategoryId()));
            }
        }
    }

    @Override
    @Cacheable(value = "product:hot", key = "'page:' + #page + ':' + #size + ':' + #sortBy", unless = "#result == null")
    public Map<String, Object> getPage(int page, int size, String sortBy) {
        int offset = (page - 1) * size;
        List<Product> list = productMapper.selectPage(offset, size, sortBy);
        int total = productMapper.countAll();
        fillCategoryName(list);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    @Cacheable(value = "product:hot", key = "'cat:' + #categoryId + ':' + #page + ':' + #size + ':' + #sortBy", unless = "#result == null")
    public Map<String, Object> getByCategoryId(Long categoryId, int page, int size, String sortBy) {
        int offset = (page - 1) * size;
        List<Product> list = productMapper.selectByCategoryId(categoryId, offset, size, sortBy);
        int total = productMapper.countByCategoryId(categoryId);
        fillCategoryName(list);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> search(String keyword, int page, int size, String sortBy) {
        int offset = (page - 1) * size;
        List<Product> list = productMapper.searchByName("%" + keyword + "%", offset, size, sortBy);
        int total = productMapper.countSearch("%" + keyword + "%");
        fillCategoryName(list);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public List<Product> searchByCategoryName(String keyword, int size) {
        return productMapper.selectByCategoryName("%" + keyword + "%", size);
    }

    @Override
    public List<Product> searchByPrice(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, int size) {
        return productMapper.selectByPrice(minPrice, maxPrice, size);
    }

    @Override
    @Cacheable(value = "product:detail", key = "#id", unless = "#result == null")
    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        fillCategoryName(java.util.Collections.singletonList(product));
        fillSkus(product);
        return product;
    }

    @Override
    @CacheEvict(value = {"product:detail", "product:hot"}, allEntries = true)
    public Product create(Product product) {
        product.setSales(0);
        product.setStatus(1);
        product.setCreateTime(new Date());
        productMapper.insert(product);
        return product;
    }

    @Override
    @CacheEvict(value = "product:detail", key = "#product.id")
    public Product update(Product product) {
        Product existing = productMapper.selectById(product.getId());
        if (existing == null) {
            throw new BusinessException("商品不存在");
        }
        if (product.getName() != null) existing.setName(product.getName());
        if (product.getDescription() != null) existing.setDescription(product.getDescription());
        if (product.getPrice() != null) existing.setPrice(product.getPrice());
        if (product.getStock() != null) existing.setStock(product.getStock());
        if (product.getCategoryId() != null) existing.setCategoryId(product.getCategoryId());
        if (product.getImage() != null) existing.setImage(product.getImage());
        if (product.getStatus() != null) existing.setStatus(product.getStatus());
        productMapper.update(existing);
        return existing;
    }

    @Override
    @CacheEvict(value = "product:detail", key = "#id")
    public void delete(Long id) {
        productMapper.deleteById(id);
    }
}
