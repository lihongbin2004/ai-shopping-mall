package com.mall.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * 大模型客户端统一接口（多模型集成）
 * - 屏蔽不同厂商的差异：DeepSeek / 通义千问 / Ollama / OpenAI 全部走这个
 * - 由 LlmRouter 负责挑选 / 降级
 */
public interface LlmClient {

    /**
     * 厂商标识（用于配置项、日志、前端展示）
     * 如：deepseek / qwen / ollama
     */
    String getProvider();

    /**
     * 模型名（用于请求体 model 字段）
     * 如：deepseek-chat / qwen-plus / llama3
     */
    String getModel();

    /**
     * 是否启用（未配置 API Key 时禁用，自动降级）
     */
    boolean isEnabled();

    /**
     * 单轮对话
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 多轮对话（流式，内部拼成完整内容返回；非流式接口使用）
     */
    String chatWithHistory(String systemPrompt, List<Map<String, String>> history, String userMessage);

    /**
     * 流式多轮对话 - 逐块回调，回调内可直接写 SSE
     * @param onChunk  每收到一段增量文本就会调用一次
     * @return         完整内容
     */
    String chatStream(String systemPrompt, List<Map<String, String>> history, String userMessage, StreamCallback onChunk);

    @FunctionalInterface
    interface StreamCallback {
        void onChunk(String delta) throws Exception;
    }
}
