package com.mall.service;

import com.mall.entity.Favorite;
import java.util.Map;

public interface FavoriteService {
    void addFavorite(Long userId, Long productId);
    void cancelFavorite(Long userId, Long productId);
    Map<String, Object> getFavoriteList(Long userId, int page, int size);
    boolean checkFavorite(Long userId, Long productId);
}
