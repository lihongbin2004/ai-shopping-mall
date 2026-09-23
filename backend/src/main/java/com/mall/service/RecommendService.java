package com.mall.service;

import com.mall.entity.Product;

import java.util.List;
import java.util.Map;

/**
 * 个性化推荐服务
 *
 * 算法（混合推荐）：
 *  1) 行为采集   - 浏览 / 收藏 / 加购 / 购买 落 Redis
 *  2) 兴趣画像   - 按分类加权聚合：购买 5 / 加购 3 / 收藏 2 / 浏览 1
 *  3) 候选召回   - 兴趣分类下的商品 ∪ 全局热门
 *  4) 精排打分   - 销量(0.4) + 兴趣匹配(0.4) + 新品权重(0.2)
 *  5) 去重/截取  - 排除已购 + 已收藏 + 已在购物车，TopN
 *  6) 缓存       - Redis 30 分钟，user 维度 key
 */
public interface RecommendService {

    /** 个性化推荐（猜你喜欢），limit 传 0 则用默认 8 */
    List<Product> forYou(Long userId, int limit);

    /** 相关商品（看了又看）：基于当前商品分类召回 */
    List<Product> related(Long productId, int limit);

    /** 兜底：首页热门（与 ProductController.hot 一致，可独立缓存） */
    List<Product> hotTop(int limit);

    /** 通用入口：返回 {list, total, source, debug} */
    Map<String, Object> forYouWithMeta(Long userId, int limit);
}
