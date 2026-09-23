package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Favorite;
import com.mall.entity.Product;
import com.mall.mapper.FavoriteMapper;
import com.mall.mapper.ProductMapper;
import com.mall.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public void addFavorite(Long userId, Long productId) {
        Favorite existing = favoriteMapper.selectByUserIdAndProductId(userId, productId);
        if (existing != null) {
            throw new BusinessException("已经收藏过了");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreateTime(new Date());
        favoriteMapper.insert(favorite);
    }

    @Override
    public void cancelFavorite(Long userId, Long productId) {
        favoriteMapper.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public Map<String, Object> getFavoriteList(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Favorite> list = favoriteMapper.selectByUserId(userId, offset, size);
        int total = favoriteMapper.countByUserId(userId);
        // 用 productId 批量回填商品信息
        if (list != null && !list.isEmpty()) {
            Set<Long> productIds = list.stream()
                    .map(Favorite::getProductId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            Map<Long, Product> productMap = new HashMap<>();
            for (Long pid : productIds) {
                Product p = productMapper.selectById(pid);
                if (p != null) productMap.put(pid, p);
            }
            for (Favorite f : list) {
                Product p = productMap.get(f.getProductId());
                if (p != null) {
                    f.setName(p.getName());
                    f.setImage(p.getImage());
                    f.setPrice(p.getPrice());
                    f.setDescription(p.getDescription());
                    f.setSales(p.getSales());
                    f.setProductStatus(p.getStatus());
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public boolean checkFavorite(Long userId, Long productId) {
        return favoriteMapper.selectByUserIdAndProductId(userId, productId) != null;
    }
}
