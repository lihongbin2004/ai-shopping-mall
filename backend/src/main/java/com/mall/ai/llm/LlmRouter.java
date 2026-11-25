package com.mall.ai.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模型路由器
 * 职责：
 *   1) 前端 / 用户传参指定模型（deepseek / qwen / ollama）
 *   2) 选中的模型失败 → 按降级链自动切下一个
 *   3) 暴露 /api/ai/models 给前端下拉框
 */
@Component
public class LlmRouter {

    @Autowired
    private OpenAiCompatibleLlmClient client;

    /**
     * 默认降级顺序：deepseek → qwen → ollama
     */
    private final String[] fallbackChain = {"deepseek", "qwen", "ollama"};

    /**
     * 同步对话：优先用选中的模型，失败自动降级
     */
    public Result chat(String preferredProvider, String systemPrompt, List<Map<String, String>> history, String userMessage) {
        return chat(preferredProvider, systemPrompt, history, userMessage, null);
    }

    /**
     * 多模态同步对话：images 非空时把图片一起送给模型（DeepSeek 新版支持视觉输入）
     */
    public Result chat(String preferredProvider, String systemPrompt, List<Map<String, String>> history,
                       String userMessage, List<String> images) {
        String[] order = buildOrder(preferredProvider);
        for (String name : order) {
            OpenAiCompatibleLlmClient.Provider p = client.pickByName(name);
            if (p == null || !p.isEnabled()) continue;
            String content = client.callByProvider(name, systemPrompt, history, userMessage, images, false, null);
            if (isOk(content)) {
                return new Result(content, p.provider, p.model, false);
            }
        }
        return new Result("所有大模型都不可用，请稍后再试。", "none", "none", true);
    }

    /**
     * 流式对话：选中的模型不可用时，尝试降级到下一个（流式只对最终选中的那个生效）
     * 真实走 provider 的 SSE 流式接口，每个 token 一个 chunk 回调给前端。
     * 流式中途异常 → 自动降级到非流式调用，避免大段内容丢失。
     */
    public Result chatStream(String preferredProvider, String systemPrompt, List<Map<String, String>> history,
                             String userMessage, LlmClient.StreamCallback onChunk) {
        return chatStream(preferredProvider, systemPrompt, history, userMessage, null, onChunk);
    }

    /**
     * 多模态流式对话：images 非空时把图片随用户消息一起送给视觉模型
     */
    public Result chatStream(String preferredProvider, String systemPrompt, List<Map<String, String>> history,
                             String userMessage, List<String> images, LlmClient.StreamCallback onChunk) {
        String[] order = buildOrder(preferredProvider);
        for (String name : order) {
            OpenAiCompatibleLlmClient.Provider p = client.pickByName(name);
            if (p == null || !p.isEnabled()) continue;
            try {
                // 真正走流式：每个 delta 都会通过 onChunk 实时推给前端
                String full = client.callByProvider(name, systemPrompt, history, userMessage, images, true, onChunk);
                if (full != null && isOk(full)) {
                    return new Result(full, p.provider, p.model, false);
                }
                // 流式拿回空或错误信息 → 降级到非流式再来一次
                String fallback = client.callByProvider(name, systemPrompt, history, userMessage, images, false, null);
                if (isOk(fallback)) {
                    if (onChunk != null) {
                        try { onChunk.onChunk(fallback); } catch (Exception ignored) {}
                    }
                    return new Result(fallback, p.provider, p.model, false);
                }
            } catch (Exception e) {
                System.err.println("[LlmRouter] " + name + " 流式失败，降级非流式: " + e.getMessage());
                try {
                    String fallback = client.callByProvider(name, systemPrompt, history, userMessage, images, false, null);
                    if (isOk(fallback)) {
                        if (onChunk != null) {
                            try { onChunk.onChunk(fallback); } catch (Exception ignored) {}
                        }
                        return new Result(fallback, p.provider, p.model, false);
                    }
                } catch (Exception ignored) {}
            }
        }
        return new Result("所有大模型都不可用，请稍后再试。", "none", "none", true);
    }

    /**
     * 仅挑选第一个可用的 provider（不发起 LLM 调用）。
     * 用途：AiController 在拼 system prompt 之前先用这个挑出"主用模型"，
     *       把模型身份信息写进 prompt，让 LLM 知道自己是谁。
     * 没找到时返回 null。
     */
    public String[] peekFirstAvailable(String preferred) {
        String[] order = buildOrder(preferred);
        for (String name : order) {
            OpenAiCompatibleLlmClient.Provider p = client.pickByName(name);
            if (p != null && p.isEnabled()) {
                return new String[]{p.provider, p.model};
            }
        }
        return null;
    }

    /**
     * 给前端展示用：列出所有模型 + 状态
     */
    public List<Map<String, Object>> listModels() {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String name : fallbackChain) {
            OpenAiCompatibleLlmClient.Provider p = client.pickByName(name);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider", p.provider);
            m.put("model", p.model);
            m.put("enabled", p.isEnabled());
            m.put("label", labelOf(p.provider));
            out.add(m);
        }
        return out;
    }

    private String labelOf(String p) {
        switch (p) {
            case "deepseek": return "DeepSeek";
            case "qwen":     return "通义千问";
            case "ollama":   return "Ollama(本地)";
            default:         return p;
        }
    }

    private String[] buildOrder(String preferred) {
        if (preferred == null || preferred.isEmpty() || "auto".equalsIgnoreCase(preferred)) {
            return fallbackChain;
        }
        String[] order = new String[fallbackChain.length];
        order[0] = preferred.toLowerCase();
        int idx = 1;
        for (String f : fallbackChain) {
            if (!f.equalsIgnoreCase(preferred)) {
                order[idx++] = f;
            }
        }
        return order;
    }

    private boolean isOk(String content) {
        if (content == null) return false;
        String s = content.trim();
        if (s.isEmpty()) return false;
        // 我们的错误信息都有"调用失败" / "服务异常" / "未配置" 关键字
        return !s.contains("调用失败") && !s.contains("服务异常") && !s.contains("未配置");
    }

    public static class Result {
        public final String content;
        public final String usedProvider;
        public final String usedModel;
        public final boolean allFailed;

        public Result(String content, String usedProvider, String usedModel, boolean allFailed) {
            this.content = content;
            this.usedProvider = usedProvider;
            this.usedModel = usedModel;
            this.allFailed = allFailed;
        }
    }
}
