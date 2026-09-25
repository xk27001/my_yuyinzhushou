package com.myyuyin.assistant.server.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final DeviceWebSocketHandler handler;
    private final String allowedOrigins;

    public WebSocketConfig(DeviceWebSocketHandler handler,
                           @Value("${assistant.web.allowed-origins:*}") String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/devices")
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }
}
