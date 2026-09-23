package com.mall.ai;

import com.mall.entity.Cart;
import com.mall.entity.Favorite;
import com.mall.entity.Product;
import com.mall.service.CartService;
import com.mall.service.FavoriteService;
import com.mall.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 工具调用执行器
 * 解析 AI 响应中的工具调用标记 [[TOOL:xxx]]{...}[[/TOOL]] 并实际执行
 */
@Service
public class AiToolService {

    @Autowired
    private ProductService productService;

    @Autowired
    private CartService cartService;

    @Autowired
    private FavoriteService favoriteService;

    // 匹配 [[TOOL:addCart]]{"productName":"坚果礼盒","quantity":2}[[/TOOL]]
    private static final Pattern TOOL_PATTERN = Pattern.compile(
            "\\[\\[TOOL:(addCart|searchProduct|clearCart|getFavorites|addFavoritesToCart)[^\\]]*\\]\\]([\\s\\S]*?)\\[\\[/TOOL\\]\\]"
    );

    /**
     * 解析并执行 AI 响应中的所有工具调用
     * @param aiResponse AI 完整响应文本
     * @param userId 用户ID
     * @return 执行结果列表
     */
    public List<Map<String, Object>> executeTools(String aiResponse, Long userId) {
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        if (aiResponse == null || aiResponse.isEmpty()) {
            return results;
        }

        Matcher matcher = TOOL_PATTERN.matcher(aiResponse);
        while (matcher.find()) {
            String toolName = matcher.group(1);
            String jsonParams = matcher.group(2).trim();

            Map<String, Object> result = new HashMap<>();
            result.put("tool", toolName);
            try {
                com.alibaba.fastjson2.JSONObject params = parseJson(jsonParams);

                switch (toolName) {
                    case "addCart":
                        result.putAll(doAddCart(params, userId));
                        break;
                    case "searchProduct":
                        result.putAll(doSearchProduct(params));
                        break;
                    case "clearCart":
                        result.putAll(doClearCart(userId));
                        break;
                    case "getFavorites":
                        result.putAll(doGetFavorites(userId));
                        break;
                    case "addFavoritesToCart":
                        result.putAll(doAddFavoritesToCart(params, userId));
                        break;
                    default:
                        result.put("ok", false);
                        result.put("message", "未知工具: " + toolName);
                }
            } catch (Exception e) {
                result.put("ok", false);
                result.put("message", "工具执行失败: " + e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    private Map<String, Object> doAddCart(com.alibaba.fastjson2.JSONObject params, Long userId) {
        Map<String, Object> result = new HashMap<>();
        String productName = params.getString("productName");
        Integer quantity = params.getInteger("quantity");
        if (quantity == null || quantity <= 0) quantity = 1;
        if (productName == null || productName.trim().isEmpty()) {
            result.put("ok", false);
            result.put("message", "缺少商品名称");
            return result;
        }

        // 模糊匹配商品
        Product product = findProductByName(productName);
        if (product == null) {
            result.put("ok", false);
            result.put("message", "未找到商品: " + productName);
            return result;
        }

        Cart cart = cartService.addItem(userId, product.getId(), quantity);
        result.put("ok", true);
        result.put("message", "已将 " + quantity + " 件【" + product.getName() + "】加入购物车");
        result.put("productId", product.getId());
        result.put("productName", product.getName());
        result.put("quantity", quantity);
        result.put("cartId", cart.getId());
        return result;
    }

    private Map<String, Object> doSearchProduct(com.alibaba.fastjson2.JSONObject params) {
        Map<String, Object> result = new HashMap<>();
        String keyword = params.getString("keyword");
        java.math.BigDecimal maxPrice = params.getBigDecimal("maxPrice");
        java.math.BigDecimal minPrice = params.getBigDecimal("minPrice");
        if (keyword == null) keyword = "";
        if (keyword.trim().isEmpty() && maxPrice == null && minPrice == null) {
            result.put("ok", false);
            result.put("message", "缺少搜索关键词或价格条件");
            return result;
        }

        // 第一阶段：按 keyword 搜 name / description
        java.util.List<Product> list = null;
        if (!keyword.trim().isEmpty()) {
            Map<String, Object> page = productService.search(keyword, 1, 20, "sales");
            list = (java.util.List<Product>) page.get("list");
        }

        // 合并价格过滤
        if (list != null && !list.isEmpty() && (maxPrice != null || minPrice != null)) {
            java.util.List<Product> filtered = new java.util.ArrayList<>();
            for (Product p : list) {
                java.math.BigDecimal price = p.getPrice();
                if (price == null) continue;
                if (maxPrice != null && price.compareTo(maxPrice) > 0) continue;
                if (minPrice != null && price.compareTo(minPrice) < 0) continue;
                filtered.add(p);
            }
            list = filtered;
        }

        // 第二阶段：keyword 没结果时尝试按分类名搜
        String searchedBy = list != null && !list.isEmpty() ? "name/description" : "";
        if ((list == null || list.isEmpty()) && !keyword.trim().isEmpty()) {
            java.util.List<Product> catList = searchByCategoryName(keyword, 20);
            if (catList != null && !catList.isEmpty()) {
                list = catList;
                searchedBy = "category";
            }
        }

        // 第三阶段：纯价格筛选（无 keyword 时）
        if ((list == null || list.isEmpty()) && (maxPrice != null || minPrice != null)) {
            try {
                list = productService.searchByPrice(minPrice, maxPrice, 20);
                searchedBy = "price";
            } catch (Exception e) {
                list = java.util.Collections.emptyList();
            }
        }

        result.put("ok", true);
        result.put("keyword", keyword);
        result.put("products", list);
        result.put("searchedBy", searchedBy);

        // 生成可读展示文字（前端直接拼接显示给用户）
        if (list == null || list.isEmpty()) {
            String keywordHint = (keyword == null || keyword.trim().isEmpty()) ? "" : "【" + keyword + "】";
            result.put("displayText", "\n\n抱歉，本店暂时没有" + keywordHint + "这类型商品😢 不过我给您挑了几款店里最热销的，您可以看看有没有喜欢的：");
        } else {
            String header;
            if (maxPrice != null && minPrice != null) {
                header = "为您找到 ¥" + minPrice + "-¥" + maxPrice + " 区间的商品";
            } else if (maxPrice != null) {
                header = "为您找到 ¥" + maxPrice + " 以下的商品";
            } else if (minPrice != null) {
                header = "为您找到 ¥" + minPrice + " 以上的商品";
            } else {
                header = "为您找到以下商品";
            }
            StringBuilder sb = new StringBuilder("\n\n").append(header).append("：\n");
            int idx = 1;
            for (Product p : list) {
                sb.append(idx++)
                  .append(". 【").append(p.getName()).append("】")
                  .append(" ¥").append(p.getPrice())
                  .append(p.getSales() != null ? "（已售 " + p.getSales() + " 件）" : "")
                  .append("\n");
            }
            sb.append("\n如需购买请告诉我，例如：把第 1 个加入购物车。");
            result.put("displayText", sb.toString());
        }
        return result;
    }

    /**
     * 按分类名搜索商品（JOIN category 表）
     */
    private List<Product> searchByCategoryName(String keyword, int size) {
        try {
            return productService.searchByCategoryName(keyword, size);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private Map<String, Object> doClearCart(Long userId) {
        Map<String, Object> result = new HashMap<>();
        cartService.clearCart(userId);
        result.put("ok", true);
        result.put("message", "购物车已清空");
        return result;
    }

    /**
     * 按商品名模糊匹配（取第一条结果）
     */
    private Product findProductByName(String name) {
        Map<String, Object> page = productService.search(name, 1, 10, null);
        @SuppressWarnings("unchecked")
        List<Product> list = (List<Product>) page.get("list");
        if (list == null || list.isEmpty()) return null;
        // 优先精确匹配
        for (Product p : list) {
            if (p.getName().equals(name)) return p;
        }
        return list.get(0);
    }

    /**
     * 列出用户所有收藏商品
     */
    private Map<String, Object> doGetFavorites(Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> page = favoriteService.getFavoriteList(userId, 1, 50);
            List<Favorite> favs = (List<Favorite>) page.get("list");
            result.put("ok", true);
            result.put("favorites", favs);
            result.put("count", favs == null ? 0 : favs.size());

            // 生成展示文本
            if (favs == null || favs.isEmpty()) {
                result.put("displayText", "\n\n您当前还没有收藏任何商品哦~ 看到喜欢的商品可以点击商品页的\"♡收藏\"按钮加入收藏夹。");
            } else {
                StringBuilder sb = new StringBuilder("\n\n您当前收藏了 ").append(favs.size()).append(" 件商品：\n");
                int idx = 1;
                for (Favorite f : favs) {
                    String name = f.getName() == null ? "(商品已下架)" : f.getName();
                    String price = f.getPrice() == null ? "" : " ¥" + f.getPrice();
                    sb.append(idx++).append(". 【").append(name).append("】").append(price).append("\n");
                }
                sb.append("\n如果您想一次性把它们都加入购物车，请告诉我\"把收藏全部加购\"；\n");
                sb.append("如果想加购指定商品，请告诉我\"把收藏里的XX加购\"。");
                result.put("displayText", sb.toString());
            }
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "查询收藏失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 把收藏商品加入购物车
     * @param params productName: 商品名（"全部"/"all"/"*" 视为全部收藏）; quantity: 数量(默认1)
     */
    private Map<String, Object> doAddFavoritesToCart(com.alibaba.fastjson2.JSONObject params, Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String productName = params.getString("productName");
            Integer quantity = params.getInteger("quantity");
            if (quantity == null || quantity <= 0) quantity = 1;

            @SuppressWarnings("unchecked")
            Map<String, Object> page = favoriteService.getFavoriteList(userId, 1, 50);
            List<Favorite> favs = (List<Favorite>) page.get("list");
            if (favs == null || favs.isEmpty()) {
                result.put("ok", false);
                result.put("message", "您还没有收藏任何商品");
                return result;
            }

            // 确定要加购的商品列表
            java.util.List<Favorite> targets = new java.util.ArrayList<>();
            boolean addAll = productName == null || productName.trim().isEmpty()
                    || productName.equals("全部") || productName.equalsIgnoreCase("all")
                    || productName.equals("*");
            if (addAll) {
                targets.addAll(favs);
            } else {
                String needle = productName.toLowerCase();
                for (Favorite f : favs) {
                    if (f.getName() != null && f.getName().toLowerCase().contains(needle)) {
                        targets.add(f);
                    }
                }
                if (targets.isEmpty()) {
                    result.put("ok", false);
                    result.put("message", "在您的收藏里没有找到【" + productName + "】");
                    return result;
                }
            }

            // 逐个加入购物车
            int success = 0;
            StringBuilder names = new StringBuilder();
            for (Favorite f : targets) {
                if (f.getProductId() == null) continue;
                try {
                    cartService.addItem(userId, f.getProductId(), quantity);
                    success++;
                    if (names.length() > 0) names.append("、");
                    names.append(f.getName());
                } catch (Exception ignored) {}
            }

            result.put("ok", success > 0);
            result.put("count", success);
            result.put("message", "已将 " + success + " 件收藏商品加入购物车：" + names);
            result.put("displayText", "\n\n✅ 已成功把" + (addAll ? "您收藏的全部" : "收藏里的【" + productName + "】")
                    + "商品（共 " + success + " 件）加入购物车：" + names + "。");
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "加购失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 简单 JSON 解析（容错）
     */
    private com.alibaba.fastjson2.JSONObject parseJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new com.alibaba.fastjson2.JSONObject();
        }
        try {
            return com.alibaba.fastjson2.JSON.parseObject(text);
        } catch (Exception e) {
            // 解析失败，尝试做容错处理
            return new com.alibaba.fastjson2.JSONObject();
        }
    }

    /**
     * 移除响应中的工具调用标记，得到展示给用户的纯文本
     */
    public String cleanResponse(String aiResponse) {
        if (aiResponse == null) return "";
        return TOOL_PATTERN.matcher(aiResponse).replaceAll("").trim();
    }
}
