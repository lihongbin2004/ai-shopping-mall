package com.mall.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    private CustomerServiceWebSocketHandler customerServiceWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // AI 智能聊天
        registry.addHandler(chatWebSocketHandler, "/ws/chat/{userId}")
                .setAllowedOrigins("*");
        // 人工客服（客户侧 + 客服侧共用，通过 query 参数 role 区分）
        registry.addHandler(customerServiceWebSocketHandler, "/ws/kf")
                .setAllowedOrigins("*");
    }
}
