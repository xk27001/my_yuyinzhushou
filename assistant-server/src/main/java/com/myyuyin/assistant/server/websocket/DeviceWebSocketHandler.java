package com.myyuyin.assistant.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyuyin.assistant.common.WebSocketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public DeviceWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String deviceCode = queryParameter(session, "deviceCode");
        if (deviceCode == null || deviceCode.isBlank()) {
            session.close(CloseStatus.BAD_DATA.withReason("deviceCode is required"));
            return;
        }
        session.getAttributes().put("deviceCode", deviceCode);
        sessions.computeIfAbsent(deviceCode, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"deviceCode\":\"" + deviceCode + "\"}"));
        log.info("设备 WebSocket 已连接: {}", deviceCode);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object code = session.getAttributes().get("deviceCode");
        if (code == null) {
            return;
        }
        Set<WebSocketSession> deviceSessions = sessions.get(code.toString());
        if (deviceSessions != null) {
            deviceSessions.remove(session);
            if (deviceSessions.isEmpty()) {
                sessions.remove(code.toString());
            }
        }
        log.info("设备 WebSocket 已断开: {}", code);
    }

    public void send(String deviceCode, WebSocketEvent event) {
        Set<WebSocketSession> deviceSessions = sessions.get(deviceCode);
        if (deviceSessions == null || deviceSessions.isEmpty()) {
            log.info("设备 {} 当前没有 WebSocket 连接，报警事件已写入日志", deviceCode);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            for (WebSocketSession session : deviceSessions) {
                if (!session.isOpen()) {
                    continue;
                }
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (IOException ex) {
            log.warn("发送 WebSocket 事件失败: {}", ex.getMessage());
        }
    }

    private String queryParameter(WebSocketSession session, String name) {
        if (session.getUri() == null || session.getUri().getRawQuery() == null) {
            return null;
        }
        for (String pair : session.getUri().getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && name.equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
