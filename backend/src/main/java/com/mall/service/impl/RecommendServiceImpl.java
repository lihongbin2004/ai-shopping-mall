package com.mall.service.impl;

import com.mall.entity.Favorite;
import com.mall.entity.Product;
import com.mall.mapper.CartMapper;
import com.mall.mapper.CategoryMapper;
import com.mall.mapper.FavoriteMapper;
import com.mall.mapper.OrderItemMapper;
import com.mall.mapper.ProductMapper;
import com.mall.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合推荐算法实现（行为画像 + 分类召回 + 多因子排序 + Redis 缓存）
 */
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

    // 行为类型权重（数字越大越重要）
    private static final double W_VIEW     = 1.0;
    private static final double W_FAVORITE = 2.0;
    private static final double W_CART     = 3.0;
    private static final double W_BUY      = 5.0;

    // 排序权重
    private static final double W_SALES_INTEREST = 0.4;
    private static final double W_INTEREST_MATCH  = 0.4;
    private static final double W_NEW             = 0.2;

    // 缓存
    private static final String CACHE_FORYOU = "rec:foryou:user:";
    private static final String CACHE_HOT    = "rec:hot:";
    private static final long   CACHE_TTL_MIN = 30;

    // 行为记录
    private static final String VIEW_HIS = "rec:view:user:";     // ZSET productId -> timestamp

    @Autowired private ProductMapper    productMapper;
    @Autowired private CategoryMapper   categoryMapper;
    @Autowired private FavoriteMapper   favoriteMapper;
    @Autowired private CartMapper       cartMapper;
    @Autowired private OrderItemMapper  orderItemMapper;
    @Autowired private StringRedisTemplate redis;

    // ========================================================
    //  公开 API
    // ========================================================

    @Override
    public List<Product> forYou(Long userId, int limit) {
        return forYouWithMeta(userId, limit).get("list") instanceof List
                ? (List<Product>) forYouWithMeta(userId, limit).get("list")
                : Collections.emptyList();
    }

    @Override
    public Map<String, Object> forYouWithMeta(Long userId, int limit) {
        int n = limit <= 0 ? 8 : Math.min(limit, 50);
        Map<String, Object> out = new LinkedHashMap<>();
        if (userId == null) {
            out.put("list", hotTop(n));
            out.put("source", "hot_fallback_anon");
            return out;
        }

        // 1) 缓存
        String key = CACHE_FORYOU + userId + ":" + n;
        String cached = safeGet(key);
        if (cached != null && !cached.isEmpty()) {
            try {
                List<Product> list = decodeList(cached);
                out.put("list", list);
                out.put("source", "cache");
                out.put("total", list.size());
                return out;
            } catch (Exception ignore) { /* fallthrough */ }
        }

        // 2) 兴趣画像 -> 召回 -> 排序
        Map<Long, Double> interest = buildInterestProfile(userId);
        Set<Long> excluded = buildExcludedSet(userId);

        List<Product> ranked;
        String source;
        if (interest.isEmpty()) {
            // 冷启动：全局热门
            ranked = hotTop(n);
            source = "cold_start_hot";
        } else {
            ranked = personalizedRecommend(interest, excluded, n);
            source = "personalized";
            // 个性化不足时用热门补齐
            if (ranked.size() < n) {
                List<Product> filler = hotTop(n * 2);
                for (Product p : filler) {
                    if (ranked.size() >= n) break;
                    if (excluded.contains(p.getId())) continue;
                    if (ranked.stream().anyMatch(x -> x.getId().equals(p.getId()))) continue;
                    ranked.add(p);
                }
                source = ranked.isEmpty() ? "cold_start_hot" : source + "+hot_fill";
            }
        }

        // 3) 写缓存
        try { safeSet(key, encodeList(ranked), Duration.ofMinutes(CACHE_TTL_MIN)); } catch (Exception e) { log.warn("[rec] cache write fail: {}", e.getMessage()); }

        out.put("list", ranked);
        out.put("source", source);
        out.put("total", ranked.size());
        out.put("interestCategories", topCategories(interest, 3));
        return out;
    }

    @Override
    public List<Product> related(Long productId, int limit) {
        if (productId == null) return hotTop(limit <= 0 ? 8 : limit);
        int n = limit <= 0 ? 8 : Math.min(limit, 50);

        Product self = productMapper.selectById(productId);
        if (self == null || self.getCategoryId() == null) {
            return hotTop(n);
        }
        List<Long> cats = Collections.singletonList(self.getCategoryId());
        List<Long> exclude = Collections.singletonList(productId);
        List<Product> list = productMapper.selectByCategoryIdsExclude(cats, exclude, n);
        if (list.size() < n) {
            // 不足用热门补
            for (Product p : hotTop(n * 2)) {
                if (list.size() >= n) break;
                if (p.getId().equals(productId)) continue;
                if (list.stream().anyMatch(x -> x.getId().equals(p.getId()))) continue;
                list.add(p);
            }
        }
        fillCategoryName(list);
        return list;
    }

    @Override
    public List<Product> hotTop(int limit) {
        int n = limit <= 0 ? 8 : Math.min(limit, 50);
        String key = CACHE_HOT + n;
        String cached = safeGet(key);
        if (cached != null && !cached.isEmpty()) {
            try { return decodeList(cached); } catch (Exception ignore) {}
        }
        // 全局按销量
        List<Product> list = productMapper.selectPage(0, n, "sales");
        fillCategoryName(list);
        try { safeSet(key, encodeList(list), Duration.ofMinutes(CACHE_TTL_MIN)); } catch (Exception e) { log.warn("[rec] hot cache fail: {}", e.getMessage()); }
        return list;
    }

    // ========================================================
    //  行为采集（公开方法，供 Controller / Service 调用）
    // ========================================================

    /** 记录一次浏览：score = timestamp, 同时累计该商品对应分类的兴趣 */
    public void trackView(Long userId, Long productId) {
        if (userId == null || productId == null) return;
        try {
            long now = System.currentTimeMillis();
            String key = VIEW_HIS + userId;
            redis.opsForZSet().add(key, productId.toString(), now);
            redis.expire(key, Duration.ofDays(30));
            // 同步加分：浏览
            Product p = productMapper.selectById(productId);
            if (p != null && p.getCategoryId() != null) {
                addInterest(userId, p.getCategoryId(), W_VIEW);
            }
        } catch (Exception e) {
            log.warn("[rec] trackView fail: {}", e.getMessage());
        }
    }

    /** 收藏：加分 + 失效个性化缓存 */
    public void trackFavorite(Long userId, Long productId) {
        if (userId == null || productId == null) return;
        Product p = productMapper.selectById(productId);
        if (p != null && p.getCategoryId() != null) {
            addInterest(userId, p.getCategoryId(), W_FAVORITE);
        }
        invalidateUserCache(userId);
    }

    /** 加购：加分 + 失效缓存 */
    public void trackCart(Long userId, Long productId) {
        if (userId == null || productId == null) return;
        Product p = productMapper.selectById(productId);
        if (p != null && p.getCategoryId() != null) {
            addInterest(userId, p.getCategoryId(), W_CART);
        }
        invalidateUserCache(userId);
    }

    /** 购买：加分 + 失效缓存（强信号） */
    public void trackBuy(Long userId, Long productId) {
        if (userId == null || productId == null) return;
        Product p = productMapper.selectById(productId);
        if (p != null && p.getCategoryId() != null) {
            addInterest(userId, p.getCategoryId(), W_BUY);
        }
        invalidateUserCache(userId);
    }

    public void invalidateUserCache(Long userId) {
        if (userId == null) return;
        try {
            Set<String> keys = redis.keys(CACHE_FORYOU + userId + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) {
            log.warn("[rec] invalidate fail: {}", e.getMessage());
        }
    }

    // ========================================================
    //  内部：兴趣画像
    // ========================================================

    /** 用户兴趣 Hash: categoryId -> score，存 Redis key=rec:interest:userId */
    private void addInterest(Long userId, Long categoryId, double delta) {
        if (userId == null || categoryId == null) return;
        String key = "rec:interest:userId:" + userId; // 注意：避免与 rec:interest:user:... 冲突
        try {
            redis.opsForHash().increment(key, String.valueOf(categoryId), delta);
            redis.expire(key, Duration.ofDays(60));
        } catch (Exception e) {
            log.warn("[rec] addInterest fail: {}", e.getMessage());
        }
    }

    /**
     * 综合构建兴趣画像：Redis 在线画像 + DB 历史（收藏/加购/购买）
     * 返回 categoryId -> score
     */
    private Map<Long, Double> buildInterestProfile(Long userId) {
        Map<Long, Double> interest = new HashMap<>();

        // A) 实时画像（Redis Hash）
        try {
            String key = "rec:interest:userId:" + userId;
            Map<Object, Object> raw = redis.opsForHash().entries(key);
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                try {
                    Long cid = Long.valueOf(e.getKey().toString());
                    Double score = Double.valueOf(e.getValue().toString());
                    interest.merge(cid, score, Double::sum);
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            log.warn("[rec] read interest from redis fail: {}", e.getMessage());
        }

        // B) 历史画像（DB）—— 给一个基准分
        try {
            // 收藏过的商品
            List<Favorite> favs = favoriteMapper.selectByUserId(userId, 0, 200);
            for (Favorite f : favs) {
                Product p = productMapper.selectById(f.getProductId());
                if (p != null && p.getCategoryId() != null) {
                    interest.merge(p.getCategoryId(), W_FAVORITE, Double::sum);
                }
            }
            // 加购过的商品
            Set<Long> cartIds = cartMapper.selectProductIdsByUserId(userId);
            if (cartIds != null) {
                for (Long pid : cartIds) {
                    Product p = productMapper.selectById(pid);
                    if (p != null && p.getCategoryId() != null) {
                        interest.merge(p.getCategoryId(), W_CART, Double::sum);
                    }
                }
            }
            // 购买过的商品
            Set<Long> bought = orderItemMapper.selectBoughtProductIdsByUserId(userId);
            if (bought != null) {
                for (Long pid : bought) {
                    Product p = productMapper.selectById(pid);
                    if (p != null && p.getCategoryId() != null) {
                        interest.merge(p.getCategoryId(), W_BUY, Double::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[rec] build interest from db fail: {}", e.getMessage());
        }
        return interest;
    }

    // ========================================================
    //  内部：召回 + 排序
    // ========================================================

    private List<Product> personalizedRecommend(Map<Long, Double> interest, Set<Long> excluded, int n) {
        // 1) 取 Top 兴趣分类（Top3）作为召回范围
        List<Long> topCats = interest.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (topCats.isEmpty()) return Collections.emptyList();

        // 2) 召回：每个 Top 分类各拉 (n) 个，按销量
        List<Product> candidates = productMapper.selectByCategoryIdsExclude(topCats, new ArrayList<>(excluded), Math.max(n * 2, 20));

        // 3) 打分
        double maxSales = candidates.stream().mapToInt(p -> p.getSales() == null ? 0 : p.getSales()).max().orElse(1);
        long newestId = candidates.stream().mapToLong(p -> p.getId() == null ? 0 : p.getId()).max().orElse(1);

        double maxInterest = interest.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        for (Product p : candidates) {
            double salesNorm = (p.getSales() == null ? 0 : p.getSales()) / Math.max(maxSales, 1);
            double interestMatch = 0.0;
            if (p.getCategoryId() != null && interest.containsKey(p.getCategoryId())) {
                interestMatch = interest.get(p.getCategoryId()) / maxInterest;
            }
            double newWeight = (p.getId() == null ? 0 : (double) p.getId()) / Math.max(newestId, 1);
            double score = W_SALES_INTEREST * salesNorm + W_INTEREST_MATCH * interestMatch + W_NEW * newWeight;
            p.setRecScore(score);
        }
        candidates.sort((a, b) -> Double.compare(
                b.getRecScore() == null ? 0 : b.getRecScore(),
                a.getRecScore() == null ? 0 : a.getRecScore()));
        if (candidates.size() > n) candidates = candidates.subList(0, n);

        fillCategoryName(candidates);
        return candidates;
    }

    private Set<Long> buildExcludedSet(Long userId) {
        Set<Long> set = new HashSet<>();
        try {
            Set<Long> bought = orderItemMapper.selectBoughtProductIdsByUserId(userId);
            if (bought != null) set.addAll(bought);
        } catch (Exception ignore) {}
        try {
            Set<Long> fav = favoriteMapper.selectProductIdsByUserId(userId);
            if (fav != null) set.addAll(fav);
        } catch (Exception ignore) {}
        try {
            Set<Long> cart = cartMapper.selectProductIdsByUserId(userId);
            if (cart != null) set.addAll(cart);
        } catch (Exception ignore) {}
        return set;
    }

    private void fillCategoryName(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        List<Long> ids = products.stream().map(Product::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return;
        Map<Long, String> nameMap = new HashMap<>();
        for (Long id : ids) {
            com.mall.entity.Category c = categoryMapper.selectById(id);
            if (c != null) nameMap.put(id, c.getName());
        }
        for (Product p : products) {
            if (p.getCategoryId() != null) p.setCategoryName(nameMap.getOrDefault(p.getCategoryId(), null));
        }
    }

    // ========================================================
    //  内部：缓存编解码（极简 JSON）
    // ========================================================

    private String safeGet(String key) {
        try { return redis.opsForValue().get(key); } catch (Exception e) { return null; }
    }
    private void safeSet(String key, String val, Duration ttl) {
        try { redis.opsForValue().set(key, val, ttl); } catch (Exception ignore) {}
    }

    /** 编码：id1|sales1#name1#image1#price1#catId1;id2|...  （紧凑、无依赖） */
    private String encodeList(List<Product> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Product p = list.get(i);
            if (i > 0) sb.append(';');
            sb.append(p.getId() == null ? 0 : p.getId()).append('|')
              .append(p.getSales() == null ? 0 : p.getSales()).append('#')
              .append(safe(p.getName())).append('#')
              .append(safe(p.getImage())).append('#')
              .append(p.getPrice() == null ? "" : p.getPrice().toPlainString()).append('#')
              .append(p.getCategoryId() == null ? 0 : p.getCategoryId()).append('#')
              .append(safe(p.getCategoryName())).append('#')
              .append(p.getStock() == null ? 0 : p.getStock());
        }
        return sb.toString();
    }

    private List<Product> decodeList(String s) {
        List<Product> list = new ArrayList<>();
        if (s == null || s.isEmpty()) return list;
        for (String item : s.split(";")) {
            if (item.isEmpty()) continue;
            String[] head = item.split("\\|", 2);
            String[] body = head.length < 2 ? new String[0] : head[1].split("#", -1);
            Product p = new Product();
            p.setId(Long.valueOf(head[0]));
            if (body.length > 0) p.setSales(parseInt(body[0]));
            if (body.length > 1) p.setName(unescape(body[1]));
            if (body.length > 2) p.setImage(unescape(body[2]));
            if (body.length > 3 && !body[3].isEmpty()) p.setPrice(new java.math.BigDecimal(body[3]));
            if (body.length > 4) p.setCategoryId(parseLong(body[4]));
            if (body.length > 5) p.setCategoryName(unescape(body[5]));
            if (body.length > 6) p.setStock(parseInt(body[6]));
            list.add(p);
        }
        return list;
    }

    private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }
    private static String safe(String s) { return s == null ? "" : escape(s); }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("|", "\\|").replace("#", "\\#").replace(";", "\\;"); }
    private static String unescape(String s) { return s.replace("\\;", ";").replace("\\#", "#").replace("\\|", "|").replace("\\\\", "\\"); }

    private List<Map<String, Object>> topCategories(Map<Long, Double> interest, int k) {
        return interest.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categoryId", e.getKey());
                    m.put("score", Math.round(e.getValue() * 100) / 100.0);
                    return m;
                })
                .collect(Collectors.toList());
    }
}
