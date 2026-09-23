package com.mall.controller;

import com.mall.ai.AiToolService;
import com.mall.ai.DeepSeekClient;
import com.mall.ai.LocalAiEngine;
import com.mall.ai.RAGService;
import com.mall.ai.llm.LlmRouter;
import com.mall.ai.ratelimit.RateLimiter;
import com.mall.common.result.R;
import com.mall.entity.AiMessage;
import com.mall.entity.AiSession;
import com.mall.service.AiMessageService;
import com.mall.service.AiSessionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
@Api(tags = "AI智能客服接口")
public class AiController {

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private RAGService ragService;

    @Autowired
    private AiSessionService aiSessionService;

    @Autowired
    private AiMessageService aiMessageService;

    @Autowired
    private AiToolService aiToolService;

    @Autowired
    private LocalAiEngine localAiEngine;

    @Autowired
    private LlmRouter llmRouter;

    @Autowired
    private RateLimiter rateLimiter;

    // AI 限流：每用户每分钟最多 30 次
    private static final long RL_WINDOW_MS = 60_000L;
    private static final int  RL_MAX_COUNT = 30;

    private String rlKey(Long userId, HttpServletRequest req) {
        if (userId != null) return "ratelimit:ai:user:" + userId;
        String ip = req == null ? "unknown" : req.getRemoteAddr();
        return "ratelimit:ai:ip:" + ip;
    }

    @PostMapping("/chat")
    @ApiOperation("AI对话")
    public R<Map<String, Object>> chat(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String message = (String) params.get("message");
        Long sessionId = params.get("sessionId") == null ? null : Long.valueOf(params.get("sessionId").toString());
        String model = params.get("model") == null ? "auto" : String.valueOf(params.get("model")); // 多模型集成：模型选择

        if (message == null || message.trim().isEmpty()) {
            return R.error("消息不能为空");
        }

        // 限流：每用户每分钟 30 次
        String key = rlKey(userId, request);
        if (!rateLimiter.tryAcquire(key, RL_WINDOW_MS, RL_MAX_COUNT)) {
            long remain = rateLimiter.remaining(key, RL_WINDOW_MS, RL_MAX_COUNT);
            return R.error("请求太频繁，请 1 分钟后再试（剩余 " + remain + " 次）");
        }

        if (sessionId == null) {
            AiSession session = aiSessionService.createSession(userId, message.length() > 20 ? message.substring(0, 20) + "..." : message);
            sessionId = session.getId();
        }

        aiMessageService.saveMessage(sessionId, "user", message);

        List<AiMessage> historyMessages = aiMessageService.getSessionMessages(sessionId);
        List<Map<String, String>> history = new ArrayList<>();
        for (AiMessage msg : historyMessages) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            history.add(m);
        }

        String[] peeked = llmRouter.peekFirstAvailable(model);
        String systemPrompt = ragService.getShoppingMallKnowledge(
                peeked == null ? null : peeked[0],
                peeked == null ? null : peeked[1],
                userId);

        // 同步版本也加 LocalAiEngine 预检（与 /chat/stream 行为一致）：
        //   用户问"想要/搜索/水果/加购"等本地能稳定识别的意图时，
        //   直接走 LocalAiEngine，绕开 DeepSeek 抽风/不听话的问题
        List<LocalAiEngine.AiMessageLite> liteHistory = new ArrayList<>();
        int fromIdx = Math.max(0, historyMessages.size() - 6);
        for (int i = fromIdx; i < historyMessages.size(); i++) {
            AiMessage hm = historyMessages.get(i);
            liteHistory.add(new LocalAiEngine.AiMessageLite(hm.getRole(), hm.getContent()));
        }
        try {
            LocalAiEngine.LocalResult local = localAiEngine.process(message, userId, liteHistory);
            if (local != null && local.tools != null && !local.tools.isEmpty()) {
                String localReply = local.reply == null ? "" : local.reply;
                aiMessageService.saveMessage(sessionId, "assistant", localReply);
                Map<String, Object> localRes = new HashMap<>();
                localRes.put("sessionId", sessionId);
                localRes.put("reply", localReply);
                localRes.put("provider", "local");
                localRes.put("usedModel", "LocalAiEngine");
                localRes.put("tools", local.tools);
                return R.ok(localRes);
            }
        } catch (Exception e) {
            System.err.println("[AI] /chat LocalAiEngine 预检失败（继续走大模型）: " + e.getMessage());
        }

        LlmRouter.Result llmRes = llmRouter.chat(model, systemPrompt, history, message);
        String reply = llmRes.content;
        aiMessageService.saveMessage(sessionId, "assistant", reply);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        result.put("provider", llmRes.usedProvider);
        result.put("usedModel", llmRes.usedModel);
        return R.ok(result);
    }

    @GetMapping("/models")
    @ApiOperation("获取可用模型列表（多模型集成）")
    public R<List<Map<String, Object>>> models() {
        return R.ok(llmRouter.listModels());
    }

    @GetMapping("/ratelimit/status")
    @ApiOperation("查询 AI 对话限流状态")
    public R<Map<String, Object>> rateLimitStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String key = rlKey(userId, request);
        long remain = rateLimiter.remaining(key, RL_WINDOW_MS, RL_MAX_COUNT);
        Map<String, Object> data = new HashMap<>();
        data.put("limit", RL_MAX_COUNT);
        data.put("windowSeconds", RL_WINDOW_MS / 1000);
        data.put("remaining", remain);
        data.put("used", RL_MAX_COUNT - remain);
        return R.ok(data);
    }

    @PostMapping(value = "/chat/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("AI流式对话")
    public SseEmitter chatStream(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String message = (String) params.get("message");
        Long sessionId = params.get("sessionId") == null ? null : Long.valueOf(params.get("sessionId").toString());
        final String model = params.get("model") == null ? "auto" : String.valueOf(params.get("model")); // 多模型集成：模型选择

        SseEmitter emitter = new SseEmitter(60000L);

        if (message == null || message.trim().isEmpty()) {
            try {
                Map<String, Object> errData = new HashMap<>();
                errData.put("error", "消息不能为空");
                emitter.send(SseEmitter.event().name("error").data(errData));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        // 限流：每用户每分钟 30 次
        final String rlKey = rlKey(userId, request);
        if (!rateLimiter.tryAcquire(rlKey, RL_WINDOW_MS, RL_MAX_COUNT)) {
            long remain = rateLimiter.remaining(rlKey, RL_WINDOW_MS, RL_MAX_COUNT);
            try {
                Map<String, Object> errData = new HashMap<>();
                errData.put("error", "请求太频繁，请 1 分钟后再试（剩余 " + remain + " 次）");
                emitter.send(SseEmitter.event().name("error").data(errData));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        if (sessionId == null) {
            String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
            AiSession session = aiSessionService.createSession(userId, title);
            sessionId = session.getId();
        }

        final Long finalSessionId = sessionId;
        aiMessageService.saveMessage(finalSessionId, "user", message);

        // 只取历史 assistant 消息，避免与当前 user 消息重复
        List<AiMessage> historyMessages = aiMessageService.getSessionMessages(finalSessionId);
        List<Map<String, String>> history = new ArrayList<>();
        for (AiMessage msg : historyMessages) {
            if ("user".equals(msg.getRole())) continue; // 跳过已保存的 user 消息
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            history.add(m);
        }
        // 传给 LocalAiEngine 的精简历史（只保留最近 6 条，避免 token 爆炸）
        List<LocalAiEngine.AiMessageLite> liteHistory = new ArrayList<>();
        int from = Math.max(0, historyMessages.size() - 6);
        for (int i = from; i < historyMessages.size(); i++) {
            AiMessage hm = historyMessages.get(i);
            liteHistory.add(new LocalAiEngine.AiMessageLite(hm.getRole(), hm.getContent()));
        }

        final Long savedSessionId = finalSessionId;
        new Thread(() -> {
            // 优先尝试 LocalAiEngine 识别意图（DeepSeek 经常抽风/限流，本地规则更稳）
            try {
                LocalAiEngine.LocalResult local = localAiEngine.process(message, userId, liteHistory);
                if (local != null && local.tools != null && !local.tools.isEmpty()) {
                    // 本地能识别意图 → 直接走本地，绕开大模型
                    String content = local.reply == null ? "" : local.reply;
                    streamLocalReply(emitter, content, finalSessionId, local.tools, aiMessageService, null);
                    return;
                }
            } catch (Exception e) {
                System.err.println("[AI] LocalAiEngine 预检失败（继续走大模型）: " + e.getMessage());
            }

            // ===== 多模型集成：通过 LlmRouter 调用（默认降级链 deepseek→qwen→ollama）=====
            try {
                // 预先挑选一个可用模型，把"你是什么模型"写进 system prompt
                String[] peeked2 = llmRouter.peekFirstAvailable(model);
                String systemPrompt = ragService.getShoppingMallKnowledge(
                        peeked2 == null ? null : peeked2[0],
                        peeked2 == null ? null : peeked2[1],
                        userId);

                final StringBuilder fullContent = new StringBuilder();
                LlmRouter.Result llmRes = llmRouter.chatStream(model, systemPrompt, history, message, (delta) -> {
                    if (delta == null) return;
                    fullContent.append(delta);
                    Map<String, Object> data = new HashMap<>();
                    data.put("content", delta);
                    data.put("sessionId", savedSessionId);
                    data.put("provider", "router");
                    emitter.send(SseEmitter.event().name("message").data(data));
                });

                if (llmRes.allFailed) {
                    System.err.println("[AI] 所有大模型均不可用，降级 LocalAiEngine");
                    try {
                        LocalAiEngine.LocalResult local = localAiEngine.process(message, userId, liteHistory);
                        streamLocalReply(emitter, local == null ? "" : local.reply, savedSessionId,
                                local == null ? null : local.tools, aiMessageService, null);
                        return;
                    } catch (Exception fbEx) {
                        System.err.println("[AI] 本地降级失败: " + fbEx.getMessage());
                    }
                    Map<String, Object> errorData = new HashMap<>();
                    errorData.put("error", llmRes.content);
                    emitter.send(SseEmitter.event().name("error").data(errorData));
                    emitter.complete();
                    return;
                }

                aiMessageService.saveMessage(savedSessionId, "assistant", fullContent.toString());

                Map<String, Object> doneData = new HashMap<>();
                doneData.put("done", true);
                doneData.put("sessionId", savedSessionId);
                doneData.put("provider", llmRes.usedProvider);
                doneData.put("usedModel", llmRes.usedModel);
                emitter.send(SseEmitter.event().name("done").data(doneData));

                // 解析并执行 AI 工具调用（购物车、商品搜索等）
                try {
                    List<Map<String, Object>> toolResults = aiToolService.executeTools(fullContent.toString(), userId);
                    if (!toolResults.isEmpty()) {
                        Map<String, Object> toolData = new HashMap<>();
                        toolData.put("tools", toolResults);
                        toolData.put("cleanContent", aiToolService.cleanResponse(fullContent.toString()));
                        emitter.send(SseEmitter.event().name("tool_result").data(toolData));
                    }
                } catch (Exception toolEx) {
                    System.err.println("工具执行异常: " + toolEx.getMessage());
                }

                emitter.complete();
            } catch (Exception e) {
                System.err.println("[AI] 调用异常，降级到 LocalAiEngine: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                try {
                    LocalAiEngine.LocalResult local = localAiEngine.process(message, userId);
                    streamLocalReply(emitter, local == null ? "" : local.reply, savedSessionId,
                            local == null ? null : local.tools, aiMessageService, null);
                    return;
                } catch (Exception fallbackEx) {
                    System.err.println("[AI] LocalAiEngine 也失败: " + fallbackEx.getMessage());
                }
                try {
                    Map<String, Object> errorData = new HashMap<>();
                    errorData.put("error", "AI服务异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(errorData));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @PostMapping("/session")
    @ApiOperation("创建会话")
    public R<AiSession> createSession(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String title = (String) params.get("title");
        return R.ok(aiSessionService.createSession(userId, title));
    }

    @GetMapping("/session/list")
    @ApiOperation("会话列表")
    public R<List<AiSession>> sessionList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(aiSessionService.getUserSessions(userId));
    }

    @DeleteMapping("/session/{id}")
    @ApiOperation("删除会话")
    public R<Void> deleteSession(@PathVariable Long id) {
        aiSessionService.deleteSession(id);
        return R.ok();
    }

    @DeleteMapping("/session/clear")
    @ApiOperation("清空当前用户所有会话")
    public R<Void> clearAllSessions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        aiSessionService.deleteUserSessions(userId);
        return R.ok();
    }

    @GetMapping("/session/{id}/messages")
    @ApiOperation("查询会话消息")
    public R<List<AiMessage>> sessionMessages(@PathVariable Long id) {
        return R.ok(aiMessageService.getSessionMessages(id));
    }

    @GetMapping("/knowledge")
    @ApiOperation("查看 RAG 知识库内容（结构化）")
    public R<Map<String, Object>> knowledge() {
        return R.ok(ragService.getKnowledgeDocument());
    }

    /**
     * 本地 AI 引擎降级时的"逐字慢推"流式输出。
     * 每次推 1 个字符 + 70ms 间隔，让前端能明显看到打字机效果。
     */
    private void streamLocalReply(SseEmitter emitter, String content, Long sessionId,
                                  List<Map<String, Object>> tools,
                                  AiMessageService aiMessageService,
                                  Long unused) {
        try {
            if (content == null) content = "";
            for (int i = 0; i < content.length(); i++) {
                Map<String, Object> data = new HashMap<>();
                String ch = content.substring(i, i + 1);
                data.put("content", ch);
                data.put("sessionId", sessionId);
                emitter.send(SseEmitter.event().name("message").data(data));
                Thread.sleep(70);
            }
            aiMessageService.saveMessage(sessionId, "assistant", content);
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("done", true);
            doneData.put("sessionId", sessionId);
            doneData.put("localFallback", true);
            emitter.send(SseEmitter.event().name("done").data(doneData));
            if (tools != null && !tools.isEmpty()) {
                Map<String, Object> toolData = new HashMap<>();
                toolData.put("tools", tools);
                emitter.send(SseEmitter.event().name("tool_result").data(toolData));
            }
            emitter.complete();
        } catch (Exception e) {
            try {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("error", "本地流式推送失败: " + e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errorData));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
        }
    }
}
