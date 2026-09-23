package com.mall.ai;

import com.mall.entity.Cart;
import com.mall.entity.Category;
import com.mall.entity.KnowledgeSection;
import com.mall.entity.Product;
import com.mall.service.CartService;
import com.mall.service.CategoryService;
import com.mall.service.IKnowledgeSectionService;
import com.mall.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库服务
 * 把商城的商品/分类/规则注入到 system prompt，让 LLM 真正"看到"商城能卖什么
 *
 * ★ 改造点（2026-06-23）：
 *   "商城简介/购物须知/售后规则/优惠券说明/常见问题"5 段静态规则不再写死在 Java 里，
 *   改为从 knowledge_section 表读取，后台 /admin/knowledge 可在线 CRUD。
 *   "AI 助手具备的工具"段是 LLM 的工具调用 prompt 模板，保留硬编码（不让管理员改）。
 */
@Service
public class RAGService {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private IKnowledgeSectionService knowledgeSectionService;

    @Autowired
    private CartService cartService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String getShoppingMallKnowledge() {
        return getShoppingMallKnowledge(null, null, null);
    }

    /**
     * 带模型身份的 system prompt。
     * 传 null/空 时使用通用身份段（不告诉 LLM 它的具体名字），
     * 传具体 provider/model 时会把"你是什么模型"写进 prompt，避免 LLM 答"我不知道"。
     */
    public String getShoppingMallKnowledge(String provider, String model) {
        return getShoppingMallKnowledge(provider, model, null);
    }

    /**
     * 完整版：模型身份 + 用户购物车实时数据。
     * 传 userId 时会把当前用户的购物车商品/数量/价格注入 prompt，
     * 让 LLM 能直接看到并回答"我购物车里有什么"。
     */
    public String getShoppingMallKnowledge(String provider, String model, Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是AI智能购物商城的智能客服助手，名叫\"小购\"。请根据以下商城知识回答用户问题。\n\n");

        // ============ 0. 模型身份（让 LLM 知道自己在用什么模型）============
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("【你的身份信息】\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        if (provider != null && !provider.isEmpty() && !"none".equalsIgnoreCase(provider)) {
            String label = labelOf(provider);
            sb.append("- 服务厂商：").append(label).append("\n");
            if (model != null && !model.isEmpty() && !"none".equalsIgnoreCase(model)) {
                sb.append("- 模型代号：").append(model).append("\n");
            }
            sb.append("- 如果用户问\"你是什么模型/谁家的/你的身份\"，**必须如实回答**：");
            sb.append("\"我是由 ").append(label);
            if (model != null && !model.isEmpty()) sb.append(" 提供的 ").append(model);
            sb.append("，为 AI 智能购物商城定制的智能客服助手'小购'。\"\n");
        } else {
            sb.append("- 如果用户问\"你是什么模型/谁家的\"，告诉他：\"我是 AI 智能购物商城的智能客服助手'小购'。\"\n");
        }
        sb.append("\n");

        // ============ 1. 知识库段落（数据库读取）============
        try {
            List<KnowledgeSection> sections = knowledgeSectionService.listEnabled();
            for (KnowledgeSection s : sections) {
                // 工具说明段在前台 Knowledge.vue 展示用，但 LLM prompt 里的工具格式不能改
                if ("AI 助手具备的工具".equals(s.getName())) continue;
                sb.append(renderSectionForPrompt(s));
            }
        } catch (Exception e) {
            // 数据库读失败时回退到基础提示，不让 prompt 拼不出来
            sb.append("【提示】知识库加载失败，请按通用电商客服话术回答。\n\n");
        }

        // ============ 2. 实时商品/分类数据注入 ============
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("【商城实时商品清单（务必参考这里回答价格/库存问题）】\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        try {
            List<Category> categories = categoryService.getList();
            sb.append("【商品分类】\n");
            if (categories != null) {
                for (Category c : categories) {
                    sb.append("- ").append(c.getName());
                    if (c.getId() != null) sb.append(" (id=").append(c.getId()).append(")");
                    sb.append("\n");
                }
            }
            sb.append("\n");

            Map<String, Object> allProducts = productService.getPage(1, 100, "sales");
            @SuppressWarnings("unchecked")
            List<Product> products = (List<Product>) allProducts.get("list");
            sb.append("【全部商品（共 ").append(products == null ? 0 : products.size()).append(" 件，按销量排序）】\n");
            if (products != null) {
                for (Product p : products) {
                    sb.append("- 【").append(p.getName()).append("】");
                    sb.append(" ¥").append(p.getPrice());
                    sb.append("（已售 ").append(p.getSales() == null ? 0 : p.getSales()).append(" 件）");
                    sb.append("，分类id=").append(p.getCategoryId());
                    if (p.getDescription() != null && !p.getDescription().isEmpty()) {
                        sb.append("，描述：").append(p.getDescription());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("（商品数据加载失败：").append(e.getMessage()).append("）\n\n");
        }

        // ============ 2.5 用户购物车实时数据注入（让 AI 看到用户购物车里有什么）============
        if (userId != null) {
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("【当前用户的购物车（务必参考这里回答『购物车里有什么』等问题）】\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            try {
                List<Cart> cartList = cartService.getCartList(userId);
                if (cartList == null || cartList.isEmpty()) {
                    sb.append("（购物车为空）\n\n");
                } else {
                    int totalQty = 0;
                    java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;
                    sb.append("购物车共有 ").append(cartList.size()).append(" 件商品：\n");
                    int idx = 1;
                    for (Cart c : cartList) {
                        String name = c.getProductName() == null ? "未知商品" : c.getProductName();
                        java.math.BigDecimal price = c.getProductPrice() == null ? java.math.BigDecimal.ZERO : c.getProductPrice();
                        int qty = c.getQuantity() == null ? 0 : c.getQuantity();
                        Integer stock = c.getStock();
                        java.math.BigDecimal sub = price.multiply(new java.math.BigDecimal(qty));
                        totalQty += qty;
                        totalAmount = totalAmount.add(sub);
                        sb.append(idx++).append(". 【").append(name).append("】");
                        sb.append(" 单价¥").append(price);
                        sb.append(" ×").append(qty).append(" = ¥").append(sub);
                        if (stock != null) {
                            sb.append("（库存 ").append(stock).append("）");
                        }
                        if (c.getProductStatus() != null && c.getProductStatus() != 1) {
                            sb.append(" ⚠️已下架");
                        }
                        sb.append("\n");
                    }
                    sb.append("总计：").append(totalQty).append(" 件，合计 ¥").append(totalAmount).append("\n\n");
                }
            } catch (Exception e) {
                sb.append("（购物车数据加载失败：").append(e.getMessage()).append("）\n\n");
            }
            sb.append("【购物车类问题回答要求】\n");
            sb.append("当用户问『我购物车里有什么/有几件/多少钱/总价多少/有哪几个』时，**必须直接列出上方购物车清单**。\n");
            sb.append("不要回避、不要让用户自己去购物车看、不要说『无法直接查看』——你已经看到了。\n");
            sb.append("如果购物车为空，告诉用户『您当前购物车为空，告诉我您想找什么商品，我帮您挑选』。\n\n");
        }

        // ============ 3. 工具说明（硬编码，不让管理员改）============
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("【重要：你具备以下工具能力】\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        sb.append("1. 【加购物车工具 addCart】\n");
        sb.append("   当用户要求\"把XX加入购物车\"、\"买2件XX\"、\"加购XX\"时使用。\n");
        sb.append("   输出格式（必须严格遵守，且只能出现一次工具调用块）：\n");
        sb.append("   [[TOOL:addCart]]{\"productName\":\"商品名称\",\"quantity\":数量}[[/TOOL]]\n");
        sb.append("   示例：用户说\"把两份进口坚果礼盒加入购物车\" → \n");
        sb.append("   你先回复\"好的，正在为您添加...\"，然后输出：\n");
        sb.append("   [[TOOL:addCart]]{\"productName\":\"进口坚果礼盒\",\"quantity\":2}[[/TOOL]]\n\n");

        sb.append("2. 【商品搜索工具 searchProduct】\n");
        sb.append("   用于按关键词或价格筛选商品。\n");
        sb.append("   参数（keyword / maxPrice / minPrice 都是可选的）：\n");
        sb.append("   - keyword: 商品名/分类名/描述里的关键词\n");
        sb.append("   - maxPrice: 最高价格，例如 200 表示 200 元以下\n");
        sb.append("   - minPrice: 最低价格，例如 100 表示 100 元以上\n");
        sb.append("   输出格式：\n");
        sb.append("   [[TOOL:searchProduct]]{\"keyword\":\"坚果\",\"maxPrice\":200}[[/TOOL]]\n\n");

        sb.append("3. 【清空购物车 clearCart】\n");
        sb.append("   当用户说\"清空购物车\"时使用。\n");
        sb.append("   [[TOOL:clearCart]]{}[[/TOOL]]\n\n");

        sb.append("4. 【查看我的收藏 getFavorites】\n");
        sb.append("   当用户提到\"我的收藏/我收藏的/收藏夹\"时使用，能直接获取该用户收藏的所有商品列表。\n");
        sb.append("   [[TOOL:getFavorites]]{}[[/TOOL]]\n\n");

        sb.append("5. 【把收藏商品加入购物车 addFavoritesToCart】\n");
        sb.append("   当用户说\"把收藏加购物车/把收藏里的XX加购/把收藏全部加购\"时使用。\n");
        sb.append("   参数：\n");
        sb.append("   - productName: 商品名（必须与用户收藏里的商品真实名称匹配；\"全部\"/\"all\"/\"*\" 表示加购所有收藏）\n");
        sb.append("   - quantity: 数量(默认1)\n");
        sb.append("   输出格式：\n");
        sb.append("   [[TOOL:addFavoritesToCart]]{\"productName\":\"全部\",\"quantity\":1}[[/TOOL]]\n");
        sb.append("   示例：用户说\"把收藏全部加购\" → 你输出：\n");
        sb.append("   [[TOOL:addFavoritesToCart]]{\"productName\":\"全部\",\"quantity\":1}[[/TOOL]]\n\n");

        sb.append("【工具调用规则】\n");
        sb.append("- 必须使用上面严格定义的 JSON 格式\n");
        sb.append("- 每个请求最多调用一次工具\n");
        sb.append("- 调用前先用一两句话告知用户你将执行的操作\n");
        sb.append("- 商品名尽量简洁，用商城里的真实商品名称（参考上面的商品清单）\n");
        sb.append("- 如果用户没说明数量，默认为1\n\n");

        sb.append("【回答原则】\n");
        sb.append("- 价格、库存、是否在售等问题：你已经看到上面【商城实时商品清单】，可以直接根据清单回答，不需要调用工具。\n");
        sb.append("- 用户说\"我有X元/预算X元/不超过X元想买XX\"：用 searchProduct 工具，传入 maxPrice 参数。\n");
        sb.append("- 用户说\"推荐/搜索/想要\"某类商品：参考清单判断，必要时用 searchProduct 工具。\n");
        sb.append("- 退货、订单、优惠券等问题：参考上面的售后规则直接回答。\n");
        sb.append("- 答案要自然、口语化，像 DeepSeek 一样直接回答，不要过度调用工具。\n");
        sb.append("- 【重要·不要编造商品】本店的商品以上面【商城实时商品清单】为准，**严格依据清单回答**，不要凭印象发挥价格、名称或库存。\n");
        sb.append("- 【重要·无货要说没货】当用户要找的商品在清单里**找不到对应商品或对应分类**时，必须直接说\"抱歉，本店暂时没有您要的这类型商品\"，然后**用 searchProduct 工具（不传 keyword 或 keyword=\"\"）拉取商城热销商品**，从中挑选几款推荐给用户，**绝不能编造或乱推荐不相关商品**。\n\n");

        sb.append("请以友好、热情、专业的语气回答用户的问题。如果遇到无法回答的问题，请引导用户联系人工客服。\n\n");

        // ============ 4. 模型身份（放在最末尾，LLM 近因效应更强，更容易记住）============
        if (provider != null && !provider.isEmpty() && !"none".equalsIgnoreCase(provider)) {
            String label = labelOf(provider);
            sb.append("【自我认知（必须遵守）】\n");
            sb.append("你必须清楚知道：你的真实身份是由 ").append(label);
            if (model != null && !model.isEmpty()) sb.append(" 提供的 ").append(model);
            sb.append(" 大语言模型，正在以\"小购\"的身份为 AI 智能购物商城服务。\n");
            sb.append("当用户直接问\"你是什么模型/谁家的/你叫什么/你的版本\"时，请**第一句话就直说**你的身份，例如：");
            sb.append("\"我是 ").append(label);
            if (model != null && !model.isEmpty()) sb.append(" ").append(model);
            sb.append("，为 AI 智能购物商城提供服务的智能客服'小购'。\" 不要回避、不要绕弯、不要用括号或脚注。\n");
        }
        return sb.toString();
    }

    /** provider 编码 → 中文标签（与 LlmRouter.labelOf 保持一致） */
    private String labelOf(String p) {
        if (p == null) return "AI 大模型";
        switch (p.toLowerCase()) {
            case "deepseek": return "DeepSeek";
            case "qwen":     return "通义千问";
            case "ollama":   return "Ollama(本地)";
            default:         return p;
        }
    }

    /** 把一个 KnowledgeSection 拼到 LLM prompt 里 */
    private String renderSectionForPrompt(KnowledgeSection s) {
        StringBuilder sb = new StringBuilder();
        String name = s.getName();
        String type = s.getType();
        String content = s.getContent() == null ? "" : s.getContent();
        sb.append("【").append(name).append("】\n");
        if ("text".equals(type)) {
            sb.append(content).append("\n\n");
        } else if ("list".equals(type)) {
            try {
                List<String> list = MAPPER.readValue(content, new TypeReference<List<String>>(){});
                int i = 1;
                for (String item : list) {
                    sb.append(i++).append(". ").append(item).append("\n");
                }
                sb.append("\n");
            } catch (Exception e) {
                sb.append(content).append("\n\n");
            }
        } else if ("kv".equals(type)) {
            // kv 类型当成纯文本输出（LLM 看得懂）
            sb.append(content).append("\n\n");
        } else {
            sb.append(content).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * RAG 知识问答（无需 LLM）：根据关键词匹配知识库，给出自然答案
     * 适合 LocalAiEngine 兜底用
     */
    public String answerFromKnowledge(String userMessage) {
        if (userMessage == null) return "";
        String m = userMessage.trim();
        StringBuilder sb = new StringBuilder();

        // 退换货 / 退款
        if (m.contains("退款") || m.contains("退货") || m.contains("退换") || m.contains("售后")) {
            sb.append("退换货这块给您说下👇\n\n");
            sb.append("• 商品签收后 **7 天内**有质量问题可申请退货退款。\n");
            sb.append("• 未发货订单随时取消；已发货订单联系客服处理。\n");
            sb.append("• 退款会在审核通过后 **3～7 个工作日**原路返回。\n");
            sb.append("• 申请路径：我的订单 → 申请退款 → 填写原因 → 等待审核。\n");
            return sb.toString();
        }
        // 发票
        if (m.contains("发票")) {
            sb.append("开发票的相关说明：\n\n");
            sb.append("• 在订单详情页可以申请开具电子发票或纸质发票。\n");
            sb.append("• 发票抬头需与收货人信息一致；发票内容默认为商品明细。\n");
            sb.append("• 电子发票会发送到您预留的邮箱；纸质发票随商品一起寄出。\n");
            return sb.toString();
        }
        // 运费 / 邮费
        if (m.contains("运费") || m.contains("邮费") || m.contains("包邮")) {
            sb.append("运费政策：\n\n");
            sb.append("• 单笔订单满 **99 元包邮**，不足部分按实际重量/距离计算。\n");
            sb.append("• 部分商品支持\"免运费\"标签，活动期内全场包邮。\n");
            sb.append("• 下单页可实时查看具体运费金额。\n");
            return sb.toString();
        }
        // 支付方式
        if (m.contains("支付") || m.contains("微信") || m.contains("支付宝")) {
            sb.append("支付方式很灵活👇\n\n");
            sb.append("• 微信支付、支付宝、银行卡、余额等都能用。\n");
            sb.append("• 大额订单还可以选择分期支付。\n");
            sb.append("• 支付遇到问题可以联系在线客服～\n");
            return sb.toString();
        }
        // 会员 / 积分
        if (m.contains("会员") || m.contains("vip") || m.contains("积分")) {
            sb.append("会员和积分体系：\n\n");
            sb.append("• 消费 1 元积 1 分，积分可以在下次下单时抵现。\n");
            sb.append("• 不同等级的会员享受不同的折扣和专属活动。\n");
            sb.append("• 升级 VIP 后有生日礼包、专属客服等福利。\n");
            return sb.toString();
        }
        // 注册 / 忘记密码
        if (m.contains("注册") || m.contains("忘记密码") || m.contains("密码")) {
            sb.append("账号相关：\n\n");
            sb.append("• 注册：在登录页点击\"立即注册\"，手机号或邮箱即可。\n");
            sb.append("• 忘记密码：登录页点\"忘记密码\"，通过手机号/邮箱验证重置。\n");
            sb.append("• 修改密码：我的 → 账号安全 → 修改密码。\n");
            return sb.toString();
        }
        // 联系客服
        if (m.contains("联系") || m.contains("客服电话") || m.contains("工作时间")) {
            sb.append("联系我们：\n\n");
            sb.append("• 在线客服：当前页面 7×24 小时为您服务～\n");
            sb.append("• 客服电话：400-888-8888（工作日 9:00-18:00）\n");
            sb.append("• 邮箱：support@ai-mall.example.com\n");
            return sb.toString();
        }
        // 尺码 / 颜色
        if (m.contains("尺码") || m.contains("颜色") || m.contains("规格")) {
            sb.append("商品规格：\n\n");
            sb.append("• 进入商品详情页可查看尺码表、颜色选项。\n");
            sb.append("• 不确定时建议先咨询客服，或者看看其他买家的晒图～\n");
            return sb.toString();
        }
        // 保修 / 维修
        if (m.contains("保修") || m.contains("维修") || m.contains("坏了")) {
            sb.append("保修和维修：\n\n");
            sb.append("• 电子产品一般享受 1 年全国联保。\n");
            sb.append("• 凭订单号/发票即可在售后网点办理。\n");
            sb.append("• 人为损坏不在保修范围内哦～\n");
            return sb.toString();
        }
        // 真假 / 正品
        if (m.contains("真假") || m.contains("正品")) {
            sb.append("正品保证：\n\n");
            sb.append("• 商城所有商品均为正品，假一赔十。\n");
            sb.append("• 支持专柜验货，开具正规发票。\n");
            sb.append("• 7 天无理由退换，让您购物无忧～\n");
            return sb.toString();
        }
        return "";
    }

    /**
     * 返回结构化的知识库内容（供前端展示）
     * 现在数据完全来自数据库 + 实时商品/分类服务
     */
    public Map<String, Object> getKnowledgeDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "AI 智能购物商城 - 知识库");
        doc.put("intro", "AI 智能购物商城是一家综合性在线购物平台，提供各类商品。客服助手“小购”在回答时会实时参考本知识库，确保信息准确、与商城一致。");

        List<Map<String, Object>> sections = new ArrayList<>();

        // 1) 知识库段落（数据库）
        try {
            List<KnowledgeSection> list = knowledgeSectionService.listEnabled();
            for (KnowledgeSection s : list) {
                sections.add(toSection(s));
            }
        } catch (Exception ignored) {}

        // 2) 实时分类
        List<Map<String, Object>> categories = new ArrayList<>();
        try {
            List<Category> list = categoryService.getList();
            if (list != null) {
                for (Category c : list) {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("id", c.getId());
                    ci.put("name", c.getName());
                    categories.add(ci);
                }
            }
        } catch (Exception ignored) {}
        sections.add(sectionData("商品分类（实时）", categories));

        // 3) 实时商品
        List<Map<String, Object>> products = new ArrayList<>();
        try {
            Map<String, Object> page = productService.getPage(1, 100, "sales");
            @SuppressWarnings("unchecked")
            List<Product> list = (List<Product>) page.get("list");
            if (list != null) {
                for (Product p : list) {
                    Map<String, Object> pi = new LinkedHashMap<>();
                    pi.put("id", p.getId());
                    pi.put("name", p.getName());
                    pi.put("price", p.getPrice());
                    pi.put("sales", p.getSales() == null ? 0 : p.getSales());
                    pi.put("stock", p.getStock() == null ? 0 : p.getStock());
                    pi.put("categoryId", p.getCategoryId());
                    pi.put("image", p.getImage());
                    pi.put("description", p.getDescription());
                    products.add(pi);
                }
            }
        } catch (Exception ignored) {}
        sections.add(sectionData("商城实时商品（按销量排序，最多 100 件）", products));

        doc.put("sections", sections);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("categoryCount", categories.size());
        stats.put("productCount", products.size());
        stats.put("updatedAt", System.currentTimeMillis());
        doc.put("stats", stats);
        return doc;
    }

    /** 把 KnowledgeSection 转成前端 Knowledge.vue 用的 section 结构 */
    private Map<String, Object> toSection(KnowledgeSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getName());
        String type = s.getType();
        if ("text".equals(type)) {
            m.put("type", "text");
            m.put("body", s.getContent());
        } else if ("list".equals(type)) {
            m.put("type", "list");
            // 尝试解析 JSON 数组；解析失败就当作单元素列表
            List<String> items = new ArrayList<>();
            try {
                items = MAPPER.readValue(s.getContent(), new TypeReference<List<String>>(){});
            } catch (Exception e) {
                items.add(s.getContent());
            }
            m.put("items", items);
        } else if ("kv".equals(type)) {
            m.put("type", "list");
            // kv 当 list 显示更友好：每行 "key: value"
            List<String> items = new ArrayList<>();
            try {
                Map<String, Object> map = MAPPER.readValue(s.getContent(), new TypeReference<Map<String, Object>>(){});
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    items.add(e.getKey() + ": " + e.getValue());
                }
            } catch (Exception e) {
                items.add(s.getContent());
            }
            m.put("items", items);
        } else {
            m.put("type", "text");
            m.put("body", s.getContent());
        }
        return m;
    }

    private Map<String, Object> sectionData(String title, List<Map<String, Object>> data) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "data");
        s.put("title", title);
        s.put("items", data);
        return s;
    }
}
