package com.mall.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * 通用 OpenAI 兼容客户端
 * - DeepSeek 官方 / 通义千问 DashScope（OpenAI 兼容模式）/ Ollama（OpenAI 兼容模式） 都可以直接复用
 * - 通过 application.yml 配置 baseUrl + apiKey + model 即可启用
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    @Value("${llm.deepseek.api-key:${deepseek.api-key:}}")
    private String deepseekKey;

    @Value("${llm.deepseek.api-url:${deepseek.api-url:https://api.deepseek.com/v1/chat/completions}}")
    private String deepseekUrl;

    @Value("${llm.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${llm.qwen.api-key:}")
    private String qwenKey;

    @Value("${llm.qwen.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String qwenUrl;

    @Value("${llm.qwen.model:qwen-plus}")
    private String qwenModel;

    @Value("${llm.ollama.api-url:http://localhost:11434/v1/chat/completions}")
    private String ollamaUrl;

    @Value("${llm.ollama.model:qwen2:7b}")
    private String ollamaModel;

    @Value("${llm.ollama.api-key:ollama}")
    private String ollamaKey;

    // ===== 三套"配置"：给 router 用 =====
    private Provider deepseek;
    private Provider qwen;
    private Provider ollama;

    @PostConstruct
    public void init() {
        this.deepseek = new Provider("deepseek", deepseekModel, deepseekUrl, deepseekKey);
        this.qwen     = new Provider("qwen",     qwenModel,     qwenUrl,     qwenKey);
        this.ollama   = new Provider("ollama",   ollamaModel,   ollamaUrl,   ollamaKey);
    }

    public Provider getDeepseekConfig() { return deepseek; }
    public Provider getQwenConfig()     { return qwen; }
    public Provider getOllamaConfig()   { return ollama; }

    // ============== LlmClient 默认走 deepseek（保持向后兼容） ==============

    @Override public String getProvider() { return "deepseek"; }
    @Override public String getModel()    { return deepseekModel; }
    @Override public boolean isEnabled()  { return deepseek.isEnabled(); }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return chatWithHistory(systemPrompt, java.util.Collections.emptyList(), userMessage);
    }

    @Override
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        Provider p = pickDefault();
        return callOnce(p, systemPrompt, history, userMessage, false, null);
    }

    @Override
    public String chatStream(String systemPrompt, List<Map<String, String>> history, String userMessage, StreamCallback onChunk) {
        Provider p = pickDefault();
        return callOnce(p, systemPrompt, history, userMessage, true, onChunk);
    }

    /**
     * 给 Router 调：按 provider 选具体实现
     */
    public String callByProvider(String provider, String systemPrompt, List<Map<String, String>> history, String userMessage,
                                 boolean stream, StreamCallback onChunk) {
        Provider p = pickByName(provider);
        return callOnce(p, systemPrompt, history, userMessage, stream, onChunk);
    }

    public Provider pickByName(String name) {
        if (name == null) return pickDefault();
        switch (name.toLowerCase()) {
            case "qwen":
            case "tongyi":
            case "dashscope":
                return qwen;
            case "ollama":
            case "local":
                return ollama;
            case "deepseek":
            default:
                return deepseek;
        }
    }

    private Provider pickDefault() {
        if (deepseek.isEnabled()) return deepseek;
        if (qwen.isEnabled())     return qwen;
        if (ollama.isEnabled())   return ollama;
        return deepseek; // 没配就返回 deepseek，调用时会返回错误信息
    }

    // =====================================================================

    private String callOnce(Provider p, String systemPrompt, List<Map<String, String>> history,
                            String userMessage, boolean stream, StreamCallback onChunk) {
        if (p == null || !p.isEnabled()) {
            return "[" + (p == null ? "null" : p.provider) + "] 模型未配置 API Key，无法调用";
        }
        try {
            URL url = new URL(p.url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + p.apiKey);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", p.model);

            JSONArray messages = new JSONArray();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(messageObj("system", systemPrompt));
            }
            if (history != null) {
                for (Map<String, String> h : history) {
                    messages.add(messageObj(h.get("role"), h.get("content")));
                }
            }
            messages.add(messageObj("user", userMessage));
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2048);
            if (stream) body.put("stream", true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toJSONString().getBytes("UTF-8"));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = readAll(conn.getErrorStream());
                return "[" + p.provider + "] 调用失败 " + code + ": " + truncate(err, 200);
            }

            if (!stream) {
                JSONObject resp = JSON.parseObject(readAll(conn.getInputStream()));
                return resp.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
            }

            // 流式
            StringBuilder full = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("data:") && !line.startsWith("data: ")) continue;
                    String data = line.startsWith("data: ") ? line.substring(6) : line.substring(5);
                    if (data.isEmpty() || "[DONE]".equals(data.trim())) continue;
                    try {
                        JSONObject obj = JSON.parseObject(data);
                        JSONObject delta = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
                        String content = delta.getString("content");
                        if (content != null) {
                            full.append(content);
                            if (onChunk != null) onChunk.onChunk(content);
                        }
                    } catch (Exception ignored) {}
                }
            }
            return full.toString();
        } catch (Exception e) {
            return "[" + p.provider + "] 服务异常: " + e.getMessage();
        }
    }

    private JSONObject messageObj(String role, String content) {
        JSONObject m = new JSONObject();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String readAll(java.io.InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    /**
     * 单个模型的配置 + 元数据
     */
    public static class Provider {
        public final String provider;
        public final String model;
        public final String url;
        public final String apiKey;

        public Provider(String provider, String model, String url, String apiKey) {
            this.provider = provider;
            this.model = model;
            this.url = url;
            this.apiKey = apiKey;
        }
        public boolean isEnabled() { return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("none"); }
        @Override public String toString() {
            return provider + "(" + model + ")" + (isEnabled() ? "" : "[未配置]");
        }
    }
}
