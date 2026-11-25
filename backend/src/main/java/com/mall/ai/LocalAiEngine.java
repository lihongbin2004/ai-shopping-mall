package com.mall.ai;

import com.mall.entity.Cart;
import com.mall.entity.Favorite;
import com.mall.entity.Product;
import com.mall.service.CartService;
import com.mall.service.FavoriteService;
import com.mall.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 本地 AI 引擎 - 关键词匹配 + 工具调用
 * 当 DeepSeek 不可用时作为降级方案，保证 AI 客服不卡死
 * 风格：自然对话 + RAG 知识 + 历史上下文
 */
@Component
public class LocalAiEngine {

    @Autowired private ProductService productService;
    @Autowired private CartService cartService;
    @Autowired private FavoriteService favoriteService;
    @Autowired private com.mall.service.CategoryService categoryService;
    @Autowired private RAGService ragService;
    /** 自动化购物流程：下单 / 支付 / 查订单（与大模型工具调用共用同一份实现） */
    @Autowired private AiOrderTool aiOrderTool;

    /** 支付二次确认：userId → 待确认的订单（5 分钟内有效，避免 AI 擅自扣款） */
    private final Map<Long, PendingPay> pendingPays = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PAY_CONFIRM_TTL_MS = 5 * 60 * 1000L;

    private static class PendingPay {
        final String orderNo;
        final BigDecimal amount;
        final long expireAt;

        PendingPay(String orderNo, BigDecimal amount, long expireAt) {
            this.orderNo = orderNo;
            this.amount = amount;
            this.expireAt = expireAt;
        }
    }

    /**
     * 处理用户消息（兼容旧调用）
     */
    public LocalResult process(String userMessage, Long userId) {
        return process(userMessage, userId, null);
    }

    /**
     * 处理用户消息（带历史消息）
     */
    public LocalResult process(String userMessage, Long userId, List<AiMessageLite> history) {
        if (userMessage == null) userMessage = "";
        String msg = userMessage.trim().toLowerCase();
        // 把"加入到购物车" "加到购物车里" 等都归一化，方便关键词匹配
        msg = msg.replace("加入到", "加入")
                 .replace("加到", "加入")
                 .replace("加进", "加入")
                 .replace("加进到", "加入")
                 .replace("购物车里", "购物车")
                 .replace("收藏夹里", "收藏");

        // 0. 优先识别"第N个"代词：把历史 assistant 列出的第 N 个商品直接取出来
        //    解决"把第三个加入购物车"这种指代类指令
        String nthProduct = extractNthProductName(userMessage, history);
        List<Map<String, Object>> tools = new ArrayList<>();
        String reply = "";

        // 1. 收藏相关
        if (msg.contains("收藏") && (msg.contains("全部") || msg.contains("所有") || msg.contains("加购") || msg.contains("加入") || msg.contains("加购物车"))) {
            // 把收藏加购
            Map<String, Object> args = new HashMap<>();
            args.put("productName", "全部");
            args.put("quantity", 1);
            Map<String, Object> toolRes = doAddFavoritesToCart(userId, args);
            tools.add(wrapTool("addFavoritesToCart", toolRes));
            reply = (String) toolRes.getOrDefault("displayText", "好的，正在为您处理...");
        }
        else if ((msg.contains("我的收藏") || msg.contains("看收藏") || msg.contains("查收藏") || msg.contains("收藏了什么") || msg.contains("收藏夹"))) {
            Map<String, Object> toolRes = doGetFavorites(userId);
            tools.add(wrapTool("getFavorites", toolRes));
            reply = (String) toolRes.getOrDefault("displayText", "好的，正在查询您的收藏...");
        }
        else if (msg.contains("收藏") && (msg.contains("加购") || msg.contains("加入") || msg.contains("加购物车"))) {
            // 收藏里的XX加购 - 尝试提取商品名
            String productName = extractProductName(msg);
            Map<String, Object> args = new HashMap<>();
            args.put("productName", productName);
            args.put("quantity", 1);
            Map<String, Object> toolRes = doAddFavoritesToCart(userId, args);
            tools.add(wrapTool("addFavoritesToCart", toolRes));
            reply = (String) toolRes.getOrDefault("displayText", "好的，正在为您处理...");
        }
        // 2. 支付二次确认：只有用户明确回复"确认"才真正扣款（避免 AI 擅自付款）
        else if (isPayConfirmIntent(msg)) {
            PendingPay pending = userId == null ? null : pendingPays.get(userId);
            if (pending == null || pending.expireAt < System.currentTimeMillis()) {
                if (userId != null) pendingPays.remove(userId);
                Map<String, Object> noPending = new HashMap<>();
                noPending.put("ok", false);
                noPending.put("message", "没有待确认的支付");
                tools.add(wrapTool("payOrder", noPending));
                reply = "我这边没有待确认的支付哦～ 如果要付款，先说\"帮我支付订单\"，我会把订单号和金额报给您确认。";
            } else {
                // 用户已确认：返回支付宝沙箱支付入口（不会直接改订单状态）
                Map<String, Object> toolRes = aiOrderTool.createPayEntry(userId, pending.orderNo);
                pendingPays.remove(userId);
                tools.add(wrapTool("payOrder", toolRes));
                reply = Boolean.TRUE.equals(toolRes.get("ok"))
                        ? (String) toolRes.getOrDefault("displayText", "已生成支付宝沙箱支付入口，请点击下方按钮完成付款")
                        : (String) toolRes.getOrDefault("displayText",
                            "暂时无法拉起支付：" + toolRes.getOrDefault("message", "请稍后再试"));
            }
        }
        // 3. 取消支付：清掉待确认状态，不做任何扣款
        else if (isPayCancelIntent(msg)) {
            boolean had = userId != null && pendingPays.remove(userId) != null;
            Map<String, Object> cancelRes = new HashMap<>();
            cancelRes.put("ok", false);
            cancelRes.put("cancelled", true);
            tools.add(wrapTool("payOrder", cancelRes));
            reply = had
                    ? "好的，已取消支付，订单仍保持待付款，需要时随时叫我～"
                    : "好的，没有执行任何支付～";
        }
        // 4. 支付请求：先报订单号+金额，要求用户确认，不直接扣款
        else if (isPayOrderIntent(msg)) {
            String orderNo = extractOrderNo(userMessage);
            Map<String, Object> payable = aiOrderTool.findPayableOrder(userId, orderNo);
            if (Boolean.TRUE.equals(payable.get("ok"))) {
                String no = String.valueOf(payable.get("orderNo"));
                Object amtObj = payable.get("totalAmount");
                BigDecimal amount = amtObj instanceof BigDecimal ? (BigDecimal) amtObj : BigDecimal.ZERO;
                if (userId != null) {
                    pendingPays.put(userId, new PendingPay(no, amount, System.currentTimeMillis() + PAY_CONFIRM_TTL_MS));
                }
                Map<String, Object> needConfirm = new HashMap<>();
                needConfirm.put("ok", false);
                needConfirm.put("needConfirm", true);
                needConfirm.put("orderNo", no);
                needConfirm.put("totalAmount", amount);
                tools.add(wrapTool("payOrder", needConfirm));
                reply = "\n\n💳 待确认支付\n• 订单号：**" + no + "**\n• 支付金额：¥" + amount
                        + "\n\n支付属于资金操作，需要您亲自确认：回复\"**确认支付**\"我就继续（5 分钟内有效），回复\"取消\"则放弃。";
            } else {
                tools.add(wrapTool("payOrder", payable));
                reply = (String) payable.getOrDefault("displayText",
                        "嗯，" + payable.getOrDefault("message", "暂时没有可支付的订单") + "。");
            }
        }
        // 5. 下单（把购物车结算，或直接买某个商品）
        else if (isCreateOrderIntent(msg)) {
            String productName = extractOrderProductName(userMessage);
            Integer quantity = extractQuantity(msg);
            boolean fromCart = msg.contains("购物车") || productName.isEmpty();
            Map<String, Object> toolRes = aiOrderTool.createOrder(userId, fromCart ? "购物车" : productName, quantity);
            if (Boolean.TRUE.equals(toolRes.get("ok"))) {
                tools.add(wrapTool("createOrder", toolRes));
                reply = (String) toolRes.getOrDefault("displayText", "下单成功～");
            } else {
                tools.add(wrapTool("createOrder", toolRes));
                String text = (String) toolRes.get("displayText");
                if (text == null || text.trim().isEmpty()) {
                    text = "\n\n❌ " + toolRes.getOrDefault("message", "下单没有成功") + "。";
                    if (!fromCart) {
                        text += "\n您可以告诉我\"找一下" + productName + "\"，我先帮您确认商城有没有这款～";
                    }
                }
                reply = text;
            }
        }
        // 6. 查询订单 / 订单状态
        else if (isQueryOrderIntent(msg)) {
            Integer statusFilter = AiOrderTool.detectStatusFilter(msg);
            Map<String, Object> toolRes = aiOrderTool.listOrders(userId, statusFilter, 5);
            tools.add(wrapTool("getOrders", toolRes));
            reply = Boolean.TRUE.equals(toolRes.get("ok"))
                    ? (String) toolRes.getOrDefault("displayText", "好的，正在为您查询订单...")
                    : (String) toolRes.getOrDefault("displayText",
                        "查询订单出错了：" + toolRes.getOrDefault("message", "请稍后再试"));
        }
        // 7. 购物车相关
        else if (msg.contains("清空") && msg.contains("购物车")) {
            Map<String, Object> toolRes = doClearCart(userId);
            tools.add(wrapTool("clearCart", toolRes));
            reply = (String) toolRes.getOrDefault("displayText", "购物车已清空～");
        }
        else if (msg.contains("加购") || msg.contains("加入") || msg.contains("加购物车") || msg.contains("添加到购物车")) {
            // 提取商品名 + 数量
            // 注意：extractQuantity 内部已过滤"第N个"序数词模式，
            //      "把第一个加购2件" 仍然能正确返回 quantity=2
            Integer quantity = extractQuantity(msg);
            // 优先用"第N个"识别出来的具体商品名
            String productName = (nthProduct != null && !nthProduct.isEmpty())
                    ? nthProduct
                    : extractProductName(msg);
            if (nthProduct == null || nthProduct.isEmpty()) {
                productName = resolvePronoun(productName, msg, history);
            }
            Map<String, Object> args = new HashMap<>();
            args.put("productName", productName);
            args.put("quantity", quantity);
            Map<String, Object> toolRes = doAddCart(userId, args);
            // 智能降级：找不到精确商品时，自动列出热门商品让用户选
            if (Boolean.FALSE.equals(toolRes.get("ok"))
                    && (productName == null || productName.trim().isEmpty()
                        || productName.matches("^[他她它这那这个那个一二三四五六七八九十0-9个款]+$")
                        || productName.length() < 2)) {
                Map<String, Object> searchArgs = new HashMap<>();
                searchArgs.put("keyword", "");
                Map<String, Object> searchRes = doSearchProduct(searchArgs);
                tools.add(wrapTool("addCart", toolRes));
                tools.add(wrapTool("searchProduct", searchRes));
                // 自然对话
                StringBuilder sb = new StringBuilder();
                sb.append("嗯～ 没找到您说的商品。给您看看商城里现在最热销的几款吧，您告诉我哪款合心意，我帮您加购：\n\n");
                appendProductListNatural(sb, searchRes, 5);
                sb.append("\n比如您可以回复\"把第 1 个加入购物车\"～");
                reply = sb.toString();
            } else if (Boolean.TRUE.equals(toolRes.get("ok"))) {
                tools.add(wrapTool("addCart", toolRes));
                String pname = String.valueOf(toolRes.getOrDefault("productName", productName));
                Integer qty = (Integer) toolRes.getOrDefault("quantity", quantity);
                reply = "好的，已经把" + (qty != null && qty > 1 ? qty + "份 " : "") + "**" + pname + "** 加入购物车啦～ 还要继续挑点别的吗？";
            } else {
                tools.add(wrapTool("addCart", toolRes));
                reply = (String) toolRes.getOrDefault("displayText", "嗯，加购的时候遇到点问题，您换个商品名再试试？");
            }
        }
        // 8. 查商品规格 / 参数 / 配置版本
        else if (isSpecQueryIntent(msg)) {
            System.out.println("[LocalAiEngine] 命中规格查询分支: msg=" + msg);
            // 先剥掉规格查询专用修饰词，再抽商品名
            String stripped = userMessage;
            for (String kw : new String[]{"有哪些规格的", "有什么规格的", "规格的", "规格",
                    "有哪些参数的", "有什么参数的", "参数",
                    "有哪些配置的", "有什么配置的", "配置",
                    "有哪些版本的", "有什么版本的", "版本",
                    "有哪些", "都有哪些", "有什么", "有啥", "的尺寸", "的颜色", "的内存", "的容量", "的重量", "多厚"}) {
                stripped = stripped.replace(kw, " ");
            }
            stripped = stripped.replaceAll("\\s+", " ").trim();
            String productName = extractProductName(stripped);
            System.out.println("[LocalAiEngine] stripped='" + stripped + "' → productName='" + productName + "'");
            // 如果没抽到商品名但有上下文，尝试从历史里找
            if (productName == null || productName.isEmpty()) {
                productName = extractProductNameFromHistory(userMessage, history);
            }
            if (productName == null || productName.isEmpty()) {
                reply = "想查哪个商品的规格呢？比如「IQOO11 有哪些规格」～";
            } else {
                Map<String, Object> toolRes = aiOrderTool.getProductSpecs(productName);
                tools.add(wrapTool("getProductSpecs", toolRes));
                reply = (String) toolRes.getOrDefault("displayText", "好的，正在为您查看「" + productName + "」的规格...");
            }
        }
        // 9. 搜索/找商品
        else if (msg.contains("搜") || msg.contains("找") || msg.contains("推荐") || msg.contains("想要") || msg.contains("有没有") || msg.contains("元以下") || msg.contains("元以上") || msg.contains("元以内") || msg.contains("价格") || msg.contains("能买") || msg.contains("想买") || (msg.contains("买") && (msg.contains("什么") || msg.contains("哪些")))) {
            String keyword = extractKeyword(msg);
            BigDecimal maxPrice = extractMaxPrice(msg);
            BigDecimal minPrice = extractMinPrice(msg);
            // 语义推荐类需求（"我喜欢打手游，推荐我买什么"/"送女朋友什么好"）交给大模型：
            // 它能结合商品描述理解场景并说明推荐理由，本地引擎只会机械罗列商品清单
            if (isSemanticRecommend(msg)) return null;
            Map<String, Object> args = new HashMap<>();
            args.put("keyword", keyword);
            if (maxPrice != null) args.put("maxPrice", maxPrice);
            if (minPrice != null) args.put("minPrice", minPrice);
            Map<String, Object> toolRes = doSearchProduct(args);
            String matchType = String.valueOf(toolRes.getOrDefault("matchType", "none"));
            // 只有真按商品名/价格命中才算本地能答。
            // category 是"整类商品兜底"，hot 是"没货+销量榜"，这两种本地答出来都会答非所问
            // （比如搜"耳机"却列出 iPhone/笔记本），所以一律交给大模型用自然语言解释。
            boolean realMatch = "name".equals(matchType) || "price".equals(matchType);
            // 关键：没真正匹配到商品时，本地引擎不要硬答。
            // 否则会把用户的整句话当成商品名复述出去（如"本店暂时没有【喜欢打手游】这类商品"），
            // 返回 null 交给大模型结合 RAG 知识库 + 全量商品清单做语义推荐。
            if (!realMatch) return null;
            tools.add(wrapTool("searchProduct", toolRes));
            // 直接用 doSearchProduct 里拼好的 displayText
            reply = (String) toolRes.getOrDefault("displayText", "好的，正在为您查找商品...");
        }
        // 9. 价格询问
        else if (msg.contains("多少钱") || msg.contains("价格") || msg.contains("便宜") || msg.contains("贵")) {
            String keyword = extractKeyword(msg);
            Map<String, Object> args = new HashMap<>();
            args.put("keyword", keyword);
            Map<String, Object> toolRes = doSearchProduct(args);
            String matchType = String.valueOf(toolRes.getOrDefault("matchType", "none"));
            // 同上：商品库里没有该品类时不要拿别的品类冒充，交给大模型自然解释
            if (!"name".equals(matchType) && !"price".equals(matchType)) return null;
            tools.add(wrapTool("searchProduct", toolRes));
            reply = (String) toolRes.getOrDefault("displayText", "好的，为您查询价格...");
        }
        // 10. 售后/退换
        else if (msg.contains("退款") || msg.contains("退货") || msg.contains("售后")) {
            reply = "关于售后这块，给您梳理一下👇\n\n" +
                    "• 商品签收后 **7 天内**，如出现质量问题，可以申请退货退款。\n" +
                    "• 未发货的订单随时可以取消；已发货的需要联系客服处理。\n" +
                    "• 退款会在审核通过后 **3～7 个工作日**原路返回。\n" +
                    "• 申请流程：我的订单 → 申请退款 → 填写原因 → 等待审核。\n\n" +
                    "您具体是哪个订单遇到问题？告诉我订单号，我帮您看看～";
        }
        // 11. 优惠券
        else if (msg.contains("优惠券") || msg.contains("折扣")) {
            reply = "优惠券相关给您说清楚👇\n\n" +
                    "• 您可以在\"我的 → 优惠券中心\"领取可用的优惠券。\n" +
                    "• 每张优惠券都有使用门槛和有效期，记得在有效期内用掉～\n" +
                    "• 每个订单只能用一张优惠券，下单时自动抵扣。\n\n" +
                    "需要我帮您看看账户里现在有哪些券吗？";
        }
        // 12. 发货
        else if (msg.contains("发货") || msg.contains("物流") || msg.contains("快递")) {
            reply = "发货和物流这块给您说下👇\n\n" +
                    "• 商家会在下单后 **24 小时内**发货。\n" +
                    "• 发货后到\"我的订单 → 订单详情\"里就能看到物流状态了。\n" +
                    "• 收到货后记得点一下\"确认收货\"，交易就完成啦。\n\n" +
                    "想看哪个订单的物流？告诉我订单号～";
        }
        // 13. 问候
        else if (msg.contains("你好") || msg.contains("hi") || msg.equals("hello") || msg.contains("在吗") || msg.contains("你是谁") || msg.contains("叫什么")) {
            reply = "嗨～ 我是AI智能客服**小购**🛍️\n\n" +
                    "我可以帮您：\n" +
                    "• 🛒 加购商品 — 比如\"把iPhone加入购物车\"\n" +
                    "• ❤️ 玩转收藏 — \"我的收藏里有什么\"、\"把收藏全部加购\"\n" +
                    "• 🔍 搜索商品 — \"找 200 元以下的坚果\"、\"我喜欢打游戏，推荐一下\"\n" +
                    "• 💰 查价格、库存\n" +
                    "• 📝 直接下单 — \"把购物车里的东西下单\"、\"帮我买一个iPhone 15 Pro Max\"\n" +
                    "• 💳 支付订单 — \"帮我支付订单\"（我会先报金额，您回复\"确认支付\"才扣款）\n" +
                    "• 📦 查订单、售后、退款、发货、优惠券等常见问题\n\n" +
                    "请问您想了解什么？";
        }
        // 14. RAG 知识库兜底
        else if (looksLikeKnowledgeQuery(msg)) {
            String rag = ragService.answerFromKnowledge(userMessage);
            if (rag != null && !rag.isEmpty()) {
                reply = rag;
            } else {
                // 知识库检索不到足够相关的内容 → 返回 null，交给大模型结合完整知识库回答，
                // 不要用"还在学习中"这种敷衍话术糊弄用户
                return null;
            }
        }
        // 默认：自然语言闲聊 + 引导
        else {
            // 礼貌兜底：先复述用户问题，再引导到能做的功能
            String trimmed = userMessage == null ? "" : userMessage.trim();
            reply = "我大概明白您想说的是 \"" + trimmed + "\"～\n\n" +
                    "我目前能帮您做这些：\n" +
                    "• 🛒 加购 / 收藏 / 搜索商品\n" +
                    "• 📝 下单 / 支付 / 📦 查订单\n" +
                    "• 💰 查价格、查库存\n" +
                    "• 📦 售后、发货、优惠券\n\n" +
                    "要不您试试\"我想找XX\"或者\"把XX加入购物车\"？";
        }

        return new LocalResult(reply, tools);
    }

    public static class LocalResult {
        public final String reply;
        public final List<Map<String, Object>> tools;
        public LocalResult(String reply, List<Map<String, Object>> tools) {
            this.reply = reply;
            this.tools = tools;
        }
    }

    // ===== 自然对话辅助方法 =====

    /**
     * 自然拼接商品列表：突出前 N 个商品的名称、价格、销量
     */
    @SuppressWarnings("unchecked")
    private void appendProductListNatural(StringBuilder sb, Map<String, Object> toolRes, int max) {
        List<Product> prods = (List<Product>) toolRes.get("products");
        if (prods == null || prods.isEmpty()) return;
        int n = Math.min(max, prods.size());
        for (int i = 0; i < n; i++) {
            Product p = prods.get(i);
            sb.append("  ").append(i + 1).append(". **").append(p.getName()).append("**");
            sb.append(" — ¥").append(p.getPrice());
            if (p.getSales() != null && p.getSales() > 0) {
                sb.append("（已有 ").append(p.getSales()).append(" 位朋友入手了）");
            }
            sb.append("\n");
        }
    }

    /**
     * 代词解析：把"他/她/它/这个/那个/第N个"从历史消息中替换
     */
    private String resolvePronoun(String productName, String msg, List<AiMessageLite> history) {
        if (productName == null || productName.isEmpty()) return productName;
        String p = productName.trim();
        // 是否代词
        boolean isPronoun = p.matches("^[他她它这那这个那个一二三四五六七八九十0-9个款]+$")
                || p.equals("它") || p.equals("他") || p.equals("她")
                || p.startsWith("第") && p.length() <= 4;
        if (!isPronoun || history == null || history.isEmpty()) return productName;
        // 从历史 assistant 消息里抽最近一次提到的商品名
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessageLite m = history.get(i);
            if (!"assistant".equals(m.role)) continue;
            String content = m.content == null ? "" : m.content;
            // 模式 1: 第N个 (从第N个的assistant 找最近搜索结果的第N个)
            java.util.regex.Matcher numM = java.util.regex.Pattern.compile("第\\s*([一二三四五六七八九十0-9]+)\\s*个").matcher(p);
            if (numM.find()) {
                String idx = numM.group(1);
                int idxNum = parseChineseNumber(idx);
                // 从最近搜索结果里找
                List<Product> prods = extractProductsFromAssistantContent(content);
                if (!prods.isEmpty() && idxNum > 0 && idxNum <= prods.size()) {
                    return prods.get(idxNum - 1).getName();
                }
            }
            // 模式 2: 这个/那个 → 提取最近提到的商品（**xxx**）
            java.util.regex.Matcher nameM = java.util.regex.Pattern.compile("\\*\\*([^*【\\]]+)\\*\\*").matcher(content);
            if (nameM.find()) {
                return nameM.group(1).trim();
            }
            // 模式 3: 【商品】
            java.util.regex.Matcher bracketM = java.util.regex.Pattern.compile("【([^】]+)】").matcher(content);
            if (bracketM.find()) {
                return bracketM.group(1).trim();
            }
        }
        return productName;
    }

    /**
     * 解析中文数字
     */
    private int parseChineseNumber(String s) {
        if (s == null || s.isEmpty()) return -1;
        try { return Integer.parseInt(s); } catch (Exception ignored) {}
        java.util.Map<Character, Integer> map = new java.util.HashMap<>();
        map.put('一',1); map.put('二',2); map.put('三',3); map.put('四',4); map.put('五',5);
        map.put('六',6); map.put('七',7); map.put('八',8); map.put('九',9); map.put('十',10);
        if (s.length() == 1) return map.getOrDefault(s.charAt(0), -1);
        if (s.length() == 2 && s.charAt(1) == '十') return map.getOrDefault(s.charAt(0), 1) * 10;
        if (s.length() == 2 && s.charAt(0) == '十') return 10 + map.getOrDefault(s.charAt(1), 0);
        if (s.length() == 3 && s.charAt(1) == '十') return map.getOrDefault(s.charAt(0), 1) * 10 + map.getOrDefault(s.charAt(2), 0);
        return -1;
    }

    /**
     * 从 assistant 文本中提取商品列表（按出现顺序）
     * 兼容 LocalAiEngine 输出的
     *   1) "1. 【商品名】 ¥..."  （带编号 + 方括号）
     *   2) "- **商品名** - ¥..." （无编号，仅破折号开头 + 加粗）
     *   3) "1. **商品名** - ¥..." （带编号 + 加粗，LLM 偶尔也会这么写）
     * 同时 LLM 偶尔会输出 "—"/"-"/"–" 三种分隔符，全兼容
     */
    private List<Product> extractProductsFromAssistantContent(String content) {
        List<Product> list = new ArrayList<>();
        if (content == null) return list;
        // 模式 A: "1. 【商品名】 ¥123"  (LocalAiEngine 带编号版)
        java.util.regex.Matcher mA = java.util.regex.Pattern
                .compile("(?:^|\\n)\\s*\\d+\\.\\s*【([^】]+)】\\s*[^\\d\\s]*?\\s*([\\d,]+(?:\\.\\d+)?)")
                .matcher(content);
        while (mA.find()) {
            Product p = new Product();
            p.setName(mA.group(1).trim());
            try { p.setPrice(new BigDecimal(mA.group(2).replace(",", ""))); } catch (Exception ignored) {}
            list.add(p);
        }
        if (!list.isEmpty()) return list;
        // 模式 B: "- **商品名** - ¥123" / "1. **商品名** - ¥123" (LLM / LocalAiEngine 列表风格)
        // 价格前允许任意非数字字符（¥/￥/空/? 等），避免 DB 编码异常导致 ¥ 变成 ? 时匹配失败
        // 注意：不要加 (?<![*]) lookbehind，会让带千分逗号的价格（如 ?9,999.00）整体匹配失败
        java.util.regex.Matcher mB = java.util.regex.Pattern
                .compile("(?:^|\\n)\\s*(?:[-•*]\\s*|\\d+\\.\\s*)\\*\\*([^*\n]+?)\\*\\*\\s*[-—–]?\\s*[^\\d\\s]*?\\s*([\\d,]+(?:\\.\\d+)?)")
                .matcher(content);
        while (mB.find()) {
            String name = mB.group(1).trim();
            if (name.isEmpty()) continue;
            Product p = new Product();
            p.setName(name);
            try { p.setPrice(new BigDecimal(mB.group(2).replace(",", ""))); } catch (Exception ignored) {}
            list.add(p);
        }
        return list;
    }

    /**
     * 识别用户场景
     */
    private String guessScene(String msg) {
        if (msg.contains("打游戏") || msg.contains("玩游戏")) return "打游戏";
        if (msg.contains("办公")) return "办公";
        if (msg.contains("出差")) return "出差";
        if (msg.contains("拍照")) return "拍照";
        if (msg.contains("听歌")) return "听歌";
        if (msg.contains("运动")) return "运动";
        if (msg.contains("看书")) return "看书";
        if (msg.contains("画画")) return "画画";
        return "";
    }

    /**
     * 是否是知识型问题（需要 RAG 兜底）
     */
    private boolean looksLikeKnowledgeQuery(String msg) {
        String[] kws = {"怎么", "如何", "怎么", "怎样", "怎么办", "什么时候", "多久", "运费", "邮费",
                "开发票", "发票", "联系", "客服电话", "工作时间", "包邮", "满减", "活动", "积分",
                "会员", "vip", "怎么注册", "如何注册", "忘记密码", "支付方式", "微信支付", "支付宝",
                "尺码", "尺码表", "颜色", "保修", "维修", "真假", "正品"};
        for (String k : kws) if (msg.contains(k)) return true;
        return false;
    }

    /**
     * 轻量历史消息（避免把整个 AiMessage 实体塞进 engine）
     */
    public static class AiMessageLite {
        public final String role;
        public final String content;
        public AiMessageLite(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    // ===== 工具包装（与 AiToolService 同样的结果结构） =====
    private Map<String, Object> wrapTool(String name, Map<String, Object> res) {
        Map<String, Object> t = new HashMap<>();
        t.put("tool", name);
        t.putAll(res);
        return t;
    }

    // ===== 与 AiToolService 同样的内部实现 =====
    private Map<String, Object> doGetFavorites(Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> page = favoriteService.getFavoriteList(userId, 1, 50);
            List<Favorite> favs = (List<Favorite>) page.get("list");
            result.put("ok", true);
            result.put("favorites", favs);
            result.put("count", favs == null ? 0 : favs.size());
            if (favs == null || favs.isEmpty()) {
                result.put("displayText", "您当前还没有收藏任何商品哦~ 看到喜欢的商品可以点击商品页的\"♡收藏\"按钮加入收藏夹。");
            } else {
                StringBuilder sb = new StringBuilder("\n\n您当前收藏了 ").append(favs.size()).append(" 件商品：\n");
                int idx = 1;
                for (Favorite f : favs) {
                    String name = f.getName() == null ? "(商品已下架)" : f.getName();
                    String price = f.getPrice() == null ? "" : " ¥" + f.getPrice();
                    sb.append(idx++).append(". 【").append(name).append("】").append(price).append("\n");
                }
                sb.append("\n如果您想一次性把它们都加入购物车，请告诉我\"把收藏全部加购\"。");
                result.put("displayText", sb.toString());
            }
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "查询收藏失败: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> doAddFavoritesToCart(Long userId, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        try {
            String productName = (String) args.get("productName");
            Integer quantity = args.get("quantity") == null ? 1 : ((Number) args.get("quantity")).intValue();

            @SuppressWarnings("unchecked")
            Map<String, Object> page = favoriteService.getFavoriteList(userId, 1, 50);
            List<Favorite> favs = (List<Favorite>) page.get("list");
            if (favs == null || favs.isEmpty()) {
                result.put("ok", false);
                result.put("message", "您还没有收藏任何商品");
                return result;
            }

            List<Favorite> targets = new ArrayList<>();
            boolean addAll = productName == null || productName.trim().isEmpty()
                    || productName.equals("全部") || productName.equalsIgnoreCase("all") || productName.equals("*");
            if (addAll) {
                targets.addAll(favs);
            } else {
                String needle = productName.trim();
                for (Favorite f : favs) {
                    if (AiTextUtil.sameProduct(f.getName(), needle)) {
                        targets.add(f);
                    }
                }
                if (targets.isEmpty()) {
                    result.put("ok", false);
                    result.put("message", "在您的收藏里没有找到【" + productName + "】");
                    return result;
                }
            }

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

    private Map<String, Object> doAddCart(Long userId, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        try {
            String productName = (String) args.get("productName");
            Integer quantity = args.get("quantity") == null ? 1 : ((Number) args.get("quantity")).intValue();
            if (quantity == null || quantity <= 0) quantity = 1;
            if (productName == null || productName.trim().isEmpty()) {
                result.put("ok", false);
                result.put("message", "请告诉我商品名称");
                return result;
            }
            Product p = findProductByName(productName);
            if (p == null) {
                result.put("ok", false);
                result.put("message", "未找到商品: " + productName);
                result.put("displayText", "抱歉，没有找到【" + productName + "】这件商品，您可以换个名称试试～");
                return result;
            }
            Cart cart = cartService.addItem(userId, p.getId(), quantity);
            result.put("ok", true);
            result.put("message", "已将 " + quantity + " 件【" + p.getName() + "】加入购物车");
            result.put("productId", p.getId());
            result.put("displayText", "\n\n✅ 已将 " + quantity + " 件【" + p.getName() + "】（¥" + p.getPrice() + "）加入购物车。");
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "加购失败: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> doSearchProduct(Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        try {
            String keyword = args.get("keyword") == null ? "" : ((String) args.get("keyword")).trim();
            BigDecimal maxPrice = args.get("maxPrice") == null ? null : new BigDecimal(args.get("maxPrice").toString());
            BigDecimal minPrice = args.get("minPrice") == null ? null : new BigDecimal(args.get("minPrice").toString());

            List<Product> list = null;
            // matchType 用来区分"真的搜索到"还是"走销量榜兜底"
            //  - name    : 按商品名/描述匹配
            //  - category: 按分类名匹配
            //  - price   : 纯价格筛选
            //  - hot     : 上面都没匹配上，走销量榜兜底（说明用户想要的商品库没有）
            //  - none    : 兜底后仍然没结果（极端情况，库存全为空之类）
            String matchType = "none";

            // 智能匹配：把"打游戏"等意图词扩展为相关分类关键词
            String searchKw = expandKeyword(keyword);

            // 1) 按扩展后的关键词搜商品名/描述
            if (!searchKw.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> page = productService.search(searchKw, 1, 30, "sales");
                list = (List<Product>) page.get("list");
                if (list != null && !list.isEmpty()) matchType = "name";
            }
            // 2) 没找到 → 用原始 keyword 再试一次
            if ((list == null || list.isEmpty()) && !keyword.isEmpty() && !keyword.equals(searchKw)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> page = productService.search(keyword, 1, 30, "sales");
                list = (List<Product>) page.get("list");
                if (list != null && !list.isEmpty()) matchType = "name";
            }
            // 3) 还没找到 + 有价格区间 → 按价格过滤
            if ((list == null || list.isEmpty()) && (maxPrice != null || minPrice != null)) {
                list = productService.searchByPrice(minPrice, maxPrice, 30);
                if (list != null && !list.isEmpty()) matchType = "price";
            }
            // 4) 都没有 → 用场景词到分类名里匹配（电子产品/服装鞋帽…），拉该分类下所有商品
            if (list == null || list.isEmpty()) {
                String sceneCategory = resolveCategoryByScene(searchKw, keyword);
                if (!sceneCategory.isEmpty()) {
                    list = productService.searchByCategoryName(sceneCategory, 30);
                    if (list != null && !list.isEmpty()) matchType = "category";
                }
            }
            // 5) 真的没有 → 走销量榜（标记为 hot，让上层知道这是"无货+热门推荐"）
            if (list == null || list.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> page = productService.getPage(1, 30, "sales");
                list = (List<Product>) page.get("list");
                matchType = "hot";
            }
            // 价格过滤（再过一遍保险）
            if (list != null && !list.isEmpty() && (maxPrice != null || minPrice != null)) {
                List<Product> filtered = new ArrayList<>();
                for (Product p : list) {
                    BigDecimal price = p.getPrice();
                    if (price == null) continue;
                    if (maxPrice != null && price.compareTo(maxPrice) > 0) continue;
                    if (minPrice != null && price.compareTo(minPrice) < 0) continue;
                    filtered.add(p);
                }
                list = filtered;
            }
            // 截断 10 条
            if (list != null && list.size() > 10) list = list.subList(0, 10);

            result.put("ok", true);
            result.put("keyword", keyword);
            result.put("products", list);
            result.put("matchType", matchType);

            if (list == null || list.isEmpty()) {
                String keywordHint = keyword.isEmpty() ? "" : "【" + keyword + "】";
                result.put("displayText", "\n\n抱歉，本店暂时没有" + keywordHint + "这类型商品😢 我给您挑了几款店里热销的，您可以看看：");
            } else if ("hot".equals(matchType) && !keyword.isEmpty()) {
                // 用户明确搜索了某类商品，商品库里没有 → 走销量兜底，必须先说"没货"再推热品
                StringBuilder sb = new StringBuilder("\n\n抱歉，本店暂时没有【").append(keyword).append("】这类型商品😢 不过我给您挑了几款店里最热销的，您看看有没有合心意的：\n");
                int idx = 1;
                for (Product p : list) {
                    sb.append("  ").append(idx++).append(". **").append(p.getName()).append("**")
                      .append(" — ¥").append(p.getPrice());
                    if (p.getSales() != null && p.getSales() > 0) sb.append("（已有 ").append(p.getSales()).append(" 位朋友入手了）");
                    sb.append("\n");
                }
                sb.append("\n告诉我您要哪款，我立刻帮您加购～");
                result.put("displayText", sb.toString());
            } else {
                StringBuilder sb = new StringBuilder("\n\n");
                if (!keyword.isEmpty() || maxPrice != null || minPrice != null) {
                    sb.append("为您找到以下商品：\n");
                } else {
                    sb.append("🔥 商城热门商品推荐：\n");
                }
                int idx = 1;
                for (Product p : list) {
                    sb.append(idx++).append(". 【").append(p.getName()).append("】")
                      .append(" ¥").append(p.getPrice());
                    if (p.getSales() != null) sb.append("（已售 ").append(p.getSales()).append(" 件）");
                    sb.append("\n");
                }
                sb.append("\n如需购买请告诉我，例如：把第 1 个加入购物车。");
                result.put("displayText", sb.toString());
            }
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "搜索失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 把"打游戏"等场景词 → 商城真实分类名（电子产品/服装鞋帽 等）
     * 优先用内置场景映射，匹配不上再退化到商城真实分类名做包含匹配
     */
    private String resolveCategoryByScene(String sceneKw, String originalKeyword) {
        if (sceneKw == null) sceneKw = "";
        if (originalKeyword == null) originalKeyword = "";
        String text = (sceneKw + " " + originalKeyword).toLowerCase();
        // 1) 内置场景词 → 商城分类
        java.util.Map<String, String> sceneMap = new java.util.LinkedHashMap<>();
        sceneMap.put("电脑", "电子产品");
        sceneMap.put("打游戏", "电子产品");
        sceneMap.put("玩游戏", "电子产品");
        sceneMap.put("手游", "电子产品");
        sceneMap.put("游戏", "电子产品");
        sceneMap.put("办公", "电子产品");
        sceneMap.put("手机", "电子产品");
        sceneMap.put("拍照", "电子产品");
        sceneMap.put("出差", "电子产品");
        sceneMap.put("数码", "电子产品");
        sceneMap.put("电子", "电子产品");
        sceneMap.put("平板", "电子产品");
        sceneMap.put("耳机", "电子产品");
        sceneMap.put("听歌", "电子产品");
        sceneMap.put("运动", "服装鞋帽");
        sceneMap.put("衣服", "服装鞋帽");
        sceneMap.put("穿", "服装鞋帽");
        sceneMap.put("吃", "食品饮料");
        sceneMap.put("零食", "食品饮料");
        sceneMap.put("喝", "食品饮料");
        sceneMap.put("看书", "图书音像");
        sceneMap.put("学习", "图书音像");
        for (java.util.Map.Entry<String, String> e : sceneMap.entrySet()) {
            if (text.contains(e.getKey())) return e.getValue();
        }
        // 2) 兜底：拿商城真实分类名做包含匹配
        try {
            java.util.List<com.mall.entity.Category> list = categoryService.getList();
            if (list != null) {
                for (com.mall.entity.Category c : list) {
                    String name = c.getName();
                    if (name != null && !name.isEmpty() && text.contains(name)) return name;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 意图词扩展：把"打游戏"等场景词映射为更可能匹配商品的关键词
     */
    private String expandKeyword(String msg) {
        if (msg == null || msg.isEmpty()) return "";
        String s = msg;
        // 移除动作/礼貌词
        for (String kw : new String[]{"帮我", "麻烦", "请", "推荐", "搜索", "找", "想要", "有没有",
                "的", "一下", "看看", "啊", "呀", "呢", "吧", "吗",
                "能买", "能买哪些东西", "能买什么", "可以买", "想买", "买什么", "哪些", "东西",
                "预算", "块钱", "元以内", "元以下", "元以上", "以内", "以下", "以上"}) {
            s = s.replace(kw, " ");
        }
        s = s.replaceAll("\\d+", " ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "";

        // 搜索关键词：把"出差/拍照"等意图词直接映射成可被商品名命中的真实关键词
        // 注意：这里只能放"商品名里真有可能出现的词"，否则一旦搜不到就会污染分类召回
        java.util.Map<String, String> sceneMap = new java.util.HashMap<>();
        sceneMap.put("出差", "手机");
        sceneMap.put("拍照", "手机");
        sceneMap.put("手机", "手机");
        sceneMap.put("电脑", "电脑");
        // "看书/画画/听歌/运动/打游戏" 不在这里映射，留给 resolveCategoryByScene 走分类召回
        for (java.util.Map.Entry<String, String> e : sceneMap.entrySet()) {
            if (s.contains(e.getKey())) return e.getValue();
        }
        return s;
    }

    private Map<String, Object> doClearCart(Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            cartService.clearCart(userId);
            result.put("ok", true);
            result.put("message", "购物车已清空");
            result.put("displayText", "\n\n✅ 购物车已清空～");
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    private Product findProductByName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String q = name.trim();
        List<Product> list = searchProducts(q);
        // 用户可能输入 "iQOO 11" / "IQOO-11" 这类带空格或连字符的型号，
        // 归一化后再查一次，避免被数据库 LIKE 的空格挡住
        if (list == null || list.isEmpty()) {
            list = searchProducts(AiTextUtil.normalize(q));
        }
        if (list == null) return null;
        // 归一化后接受完全匹配或包含匹配，避免"第 个"误命中销量第一的商品
        for (Product p : list) {
            if (AiTextUtil.sameProduct(p.getName(), q)) {
                return p;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Product> searchProducts(String keyword) {
        Map<String, Object> page = productService.search(keyword, 1, 10, null);
        if (page == null) return null;
        return (List<Product>) page.get("list");
    }

    // ===== 订单相关意图识别 =====

    /** 是否"下单/结算/直接购买"意图 */
    private boolean isSpecQueryIntent(String msg) {
        if (msg == null) return false;
        // 规格相关关键词
        for (String k : new String[]{"规格", "参数", "配置", "版本", "有哪", "都有哪"}) {
            if (msg.contains(k)) {
                // 排除"推荐有哪款""有什么样的"这种不是查规格的
                if (msg.contains("推荐") || msg.contains("什么样") || msg.contains("哪款好")) return false;
                return true;
            }
        }
        // 明确问尺寸/颜色/内存/容量/尺码/重量，也算查规格
        for (String k : new String[]{"有什么颜色", "有哪些颜色", "有什么内存", "有哪些内存",
                "有什么容量", "有哪些容量", "有什么尺码", "有哪些尺码",
                "尺寸多大", "重量多少", "多厚"}) {
            if (msg.contains(k)) return true;
        }
        return false;
    }

    private boolean isCreateOrderIntent(String msg) {
        if (msg == null) return false;
        // 询价/推荐类不算下单，避免误下单
        for (String no : new String[]{"买什么", "买哪些", "买啥", "推荐", "什么好", "哪款", "怎么买", "如何买"}) {
            if (msg.contains(no)) return false;
        }
        for (String k : new String[]{"下单", "结算", "提交订单", "立即购买", "马上买", "直接买",
                "帮我买", "替我买", "我要买", "确认购买", "买一个", "买一件", "买一份", "买台", "买部",
                "来一个", "来一件", "要一个"}) {
            if (msg.contains(k)) return true;
        }
        return false;
    }

    /** 是否"确认支付"（必须是用户明确确认，才允许扣款） */
    private boolean isPayConfirmIntent(String msg) {
        if (msg == null) return false;
        for (String k : new String[]{"确认支付", "确认付款", "确认一下支付", "确认支付吧", "同意支付"}) {
            if (msg.contains(k)) return true;
        }
        String t = msg.replaceAll("[\\s。！!，,～~]", "");
        return t.equals("确认") || t.equals("确定") || t.equals("确认吧") || t.equals("确定吧")
                || t.equals("支付吧") || t.equals("付款吧") || t.equals("可以支付") || t.equals("同意");
    }

    /** 是否"取消支付" */
    private boolean isPayCancelIntent(String msg) {
        if (msg == null) return false;
        for (String k : new String[]{"取消支付", "取消付款", "不付了", "先不付", "不用付了",
                "不支付了", "取消吧", "算了"}) {
            if (msg.contains(k)) return true;
        }
        return false;
    }

    /** 是否"支付/付款"意图（排除"支付方式""待付款订单"这类咨询/查询） */
    private boolean isPayOrderIntent(String msg) {
        if (msg == null) return false;
        for (String no : new String[]{"支付方式", "怎么支付", "如何支付", "付款方式", "怎么付款", "如何付款",
                "待付款", "未付款", "付款订单", "待支付", "未支付", "付款了吗", "支付了吗"}) {
            if (msg.contains(no)) return false;
        }
        for (String k : new String[]{"支付订单", "支付一下", "帮我支付", "去支付", "帮我付", "帮我付款",
                "我要付款", "付款吧", "付一下", "结账", "付钱"}) {
            if (msg.contains(k)) return true;
        }
        return false;
    }

    /** 是否"查订单/订单状态"意图 */
    private boolean isQueryOrderIntent(String msg) {
        if (msg == null) return false;
        for (String k : new String[]{"我的订单", "查订单", "查询订单", "查一下订单", "查下订单", "看看订单",
                "订单列表", "订单状态", "订单号", "订单到哪", "订单进度", "订单怎么", "下过什么单", "买过什么",
                "我的快递到哪", "待付款订单", "待发货订单", "待收货订单", "已完成订单", "已取消订单",
                "退款订单", "所有订单"}) {
            if (msg.contains(k)) return true;
        }
        return false;
    }

    /** 从用户原话中抽取订单号（ORD + 数字） */
    private String extractOrderNo(String rawMsg) {
        if (rawMsg == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("ORD\\d{10,}").matcher(rawMsg);
        return m.find() ? m.group() : "";
    }

    /**
     * 下单用的商品名抽取：与 extractProductName 不同，这里保留型号里的数字
     * （"帮我买一个 iPhone 15 Pro Max" → "iPhone 15 Pro Max"）
     */
    private String extractOrderProductName(String rawMsg) {
        if (rawMsg == null) return "";
        String s = rawMsg.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[「『\"']([^」』\"']+)[」』\"']").matcher(s);
        if (m.find()) return m.group(1).trim();
        for (String kw : new String[]{"帮我下单", "我要买", "我想买", "帮我买", "替我买", "给我",
                "购买", "买一个", "买一件", "买一份", "买台", "买部", "下单", "结算", "提交订单",
                "立即", "马上", "直接", "帮我", "麻烦", "请", "我要", "我想",
                "把", "将", "一个", "一件", "一份", "购物车", "全部", "所有", "商品", "东西",
                "里", "的", "吧", "呢", "啊", "呀", "哦", "可以吗", "吗", "谢谢", "。", "，", ","}) {
            s = s.replace(kw, " ");
        }
        // 去掉开头的数量（"2个iPhone" → "iPhone"），但保留型号中间的数字
        s = s.replaceAll("^\\s*[一二三四五六七八九十\\d]+\\s*(?:个|件|份|台|只)?", " ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 30) s = s.substring(0, 30);
        return s;
    }

    // ===== 简易文本解析 =====
    private Integer extractQuantity(String msg) {
        // 匹配 "2件"、"三份"、"3个"
        // 先把"第N个"这种序数词模式从消息中剥掉，避免"第三个"被误识别为数量 3
        String stripped = msg.replaceAll("第\\s*[一二三四五六七八九十0-9]+\\s*个?", "");
        try {
            String[] cn = {"零","一","二","三","四","五","六","七","八","九","十"};
            for (int i = 0; i < cn.length; i++) {
                if (stripped.contains(cn[i] + "件") || stripped.contains(cn[i] + "份") || stripped.contains(cn[i] + "个")) {
                    return i;
                }
            }
            // 数字必须带量词（"2件/3个/1台"），或紧跟在"买/要/来/加购"后面，
            // 否则会把型号里的数字（iPhone 15 Pro Max 的 15）误当成数量
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(?:件|份|个|只|台)").matcher(stripped);
            boolean found = m.find();
            if (!found) {
                m = java.util.regex.Pattern.compile("(?:买|要|来|加购)\\s*(\\d+)").matcher(stripped);
                found = m.find();
            }
            if (found) {
                int n = Integer.parseInt(m.group(1));
                if (n > 0 && n < 100) return n;
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private String extractProductName(String msg) {
        // 优先提取引号里的内容
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[「『\"']([^」』\"']+)[」』\"']").matcher(msg);
        if (m.find()) return m.group(1).trim();
        // 否则去掉动作词后剩下的
        String s = msg;
        for (String kw : new String[]{"帮我", "麻烦", "请", "把", "将", "给我", "我要", "我想",
                "看看", "看下", "查一下", "查看",
                "购买", "买",
                "加购", "加进", "加入", "加购物车", "加进购物车", "加入到购物车", "加入购物车", "添加到购物车",
                "全部", "收藏里的", "收藏的", "收藏",
                "购物车", "一下", "一个", "一些", "那个", "这个", "商品", "东西", "第", "的"}) {
            s = s.replace(kw, " ");
        }
        // 只清"数量+单位"（2件/三个/1台），保留型号里的数字（iPhone 15 Pro Max）
        s = s.replaceAll("[一二三四五六七八九十\\d]+\\s*(?:件|份|个|台|只)", " ");
        // 再去掉开头的纯数量（"2 iPhone" → "iPhone"）
        s = s.replaceAll("^\\s*\\d+\\s*", " ").trim();
        s = s.replaceAll("\\s+", " ");
        if (s.length() > 20) s = s.substring(0, 20);
        return s.isEmpty() ? "" : s;
    }

    /** 从历史对话里找最近提到的商品名（用户没说商品名但上下文有） */
    private String extractProductNameFromHistory(String rawMsg, List<AiMessageLite> history) {
        if (history == null || history.isEmpty()) return "";
        // 先去掉"规格/参数/配置"等词再走一次 extractProductName（用户可能说"它的规格"）
        String hint = rawMsg.toLowerCase().replaceAll("的?规格|的?参数|的?配置|的?版本|有哪些|都有哪些|有什么|有啥|的尺寸|的颜色|的内存", " ").trim();
        hint = hint.replaceAll("\\s+", " ");
        if (!hint.isEmpty() && hint.length() > 1 && hint.length() < 20) {
            return hint; // 能抽出"它"/"这个"之类的指代，或者就是个商品名
        }
        // 否则从最近 assistant 消息里抓商品名（用【商品名】标记）
        for (int i = history.size() - 1; i >= Math.max(0, history.size() - 5); i--) {
            AiMessageLite h = history.get(i);
            if (h.role != null && h.role.equalsIgnoreCase("assistant") && h.content != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("【([^】]{2,30})】").matcher(h.content);
                if (m.find()) return m.group(1).trim();
            }
        }
        return "";
    }

    /**
     * 识别"第N个 / 第一个 / 最后一个"等指代表达，从历史 assistant 列出的商品里取对应商品名
     * 解决 "把第三个加入购物车" 这类指代式指令在 extractProductName 阶段被过滤掉的问题
     */
    private String extractNthProductName(String rawMsg, List<AiMessageLite> history) {
        if (rawMsg == null || history == null || history.isEmpty()) return "";
        // 从最近 assistant 消息抽取商品列表（按时间正序排）
        List<Product> prods = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0 && prods.size() < 20; i--) {
            AiMessageLite m = history.get(i);
            if (!"assistant".equals(m.role)) continue;
            List<Product> got = extractProductsFromAssistantContent(m.content);
            prods.addAll(0, got);
        }
        if (prods.isEmpty()) return "";
        String msg = rawMsg.trim();
        // 第N个 / 第N款
        java.util.regex.Matcher numM = java.util.regex.Pattern.compile("第\\s*([一二三四五六七八九十0-9]+)\\s*个?").matcher(msg);
        if (numM.find()) {
            int n = parseChineseNumber(numM.group(1));
            if (n > 0 && n <= prods.size()) return prods.get(n - 1).getName();
        }
        // 最后一个
        if (msg.contains("最后一个") || msg.contains("末位") || msg.contains("末了一个")) {
            return prods.get(prods.size() - 1).getName();
        }
        return "";
    }

    /** 是否为"按喜好/场景/送礼"的语义推荐需求——这类需求本地引擎答不好，交给大模型 */
    private boolean isSemanticRecommend(String msg) {
        if (msg == null) return false;
        String[] cues = {"喜欢", "爱好", "爱玩", "平时", "经常", "适合", "送", "礼物",
                "推荐我", "推荐点", "推荐些", "推荐几个", "推荐一下有", "推荐什么好",
                "买什么好", "用什么好", "哪款好", "哪款更", "选哪个", "怎么选", "比较好"};
        for (String c : cues) {
            if (msg.contains(c)) return true;
        }
        return false;
    }

    private String extractKeyword(String msg) {
        String s = msg;
        // 先去掉意图前缀、量词、疑问词等噪声，让真实品类词留下来
        for (String kw : new String[]{"帮我", "麻烦", "请", "推荐", "搜索", "找", "想要", "有没有",
                "的", "一下", "看看", "元以下", "元以上", "块以下", "块以上", "价格", "多少钱",
                "我想", "我想要", "要点", "想买", "想", "买", "点",
                "能买", "能", "可以", "我", "您", "你", "要",
                "什么", "哪些", "啥", "哪种", "哪类", "哪些个",
                "点啥", "点什么东西", "点啥东西", "点什么东西", "点什么",
                "东西", "商品", "产品", "物品", "货",
                // 喜好/习惯类口语词，避免"我喜欢打手游"整句被当成商品名
                "喜欢", "爱好", "爱玩", "爱用", "爱", "平时", "经常", "最近", "常常", "一般",
                "比较喜欢", "比较", "倾向", "偏好", "习惯", "打算", "准备",
                "比较好", "比较合适", "合适", "好", "呢", "啊", "呗", "嘛", "吧", "哦", "呀"}) {
            s = s.replace(kw, " ");
        }
        s = s.replaceAll("[\\d,.]+", " ").replaceAll("\\s+", " ").trim();
        // 去掉标点符号（逗号/句号/问号/感叹号），避免关键词里混入"，。"之类的字符
        s = s.replaceAll("[，,。.！!？?、；;：:~～\\-—_\\[\\]（）()【】]", " ").replaceAll("\\s+", " ").trim();
        // 去掉后是空（比如"推荐点东西"/"推荐什么"），返回空让 searchProduct 自己走空 keyword 热品兜底
        if (s.isEmpty()) return "";
        if (s.length() > 20) s = s.substring(0, 20);
        return s;
    }

    private BigDecimal extractMaxPrice(String msg) {
        // 500以下 / 500元以内 / 500块之内
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|￥|¥)?\\s*(?:以下|之内|以内)").matcher(msg);
        if (m.find()) return new BigDecimal(m.group(1));
        // 我有500 / 500预算 / 500块钱 / 500块  → 视为预算上限
        m = java.util.regex.Pattern.compile("(?:有|是|大约|约|大概)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:块|块钱|元|预算|钱)?\\s*(?:预算)?\\s*[,，。]?\\s*(?:能|可|想|要)?(?:买|花|花销)?").matcher(msg);
        // 简化：在含"预算/块钱/钱"时，取句中第一个数字
        if (msg.contains("预算") || msg.contains("块钱") || msg.contains("钱") || msg.contains("块")) {
            m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(msg);
            if (m.find()) {
                try {
                    return new BigDecimal(m.group(1));
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private BigDecimal extractMinPrice(String msg) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|￥|¥)?\\s*(?:以上|之上|以外)").matcher(msg);
        if (m.find()) return new BigDecimal(m.group(1));
        return null;
    }
}
