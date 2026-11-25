package com.mall.ai;

import java.util.Locale;

/**
 * AI 导购文本归一化工具。
 * 用于消除商品名匹配时的大小写、空格、连字符等格式差异，
 * 例如 "iQOO 11" / "iqoo11" / "IQOO-11" 视为同一个商品。
 */
public final class AiTextUtil {

    private AiTextUtil() {}

    /** 归一化：转小写并去掉空白、连字符、常见分隔符 */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-_—–·、,，.。/\\\\]+", "")
                .trim();
    }

    /** 商品名宽松匹配：归一化后完全相等或互相包含 */
    public static boolean sameProduct(String productName, String query) {
        String n1 = normalize(productName);
        String n2 = normalize(query);
        if (n1.isEmpty() || n2.isEmpty()) return false;
        return n1.equals(n2) || n1.contains(n2) || n2.contains(n1);
    }
}
