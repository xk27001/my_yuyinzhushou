package com.myyuyin.assistant.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyuyin.assistant.common.WebSocketEvent;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.function.Consumer;

public class AlarmRealtimeClient extends WebSocketClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Consumer<WebSocketEvent> eventConsumer;
    private final Consumer<String> statusConsumer;

    public AlarmRealtimeClient(URI serverUri, Consumer<WebSocketEvent> eventConsumer,
                               Consumer<String> statusConsumer) {
        super(serverUri);
        this.eventConsumer = eventConsumer;
        this.statusConsumer = statusConsumer;
        setConnectionLostTimeout(20);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        statusConsumer.accept("实时通道已连接");
    }

    @Override
    public void onMessage(String message) {
        try {
            WebSocketEvent event = objectMapper.readValue(message, WebSocketEvent.class);
            eventConsumer.accept(event);
        } catch (Exception ex) {
            statusConsumer.accept("实时消息解析失败: " + ex.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        statusConsumer.accept("实时通道已断开" + (reason == null || reason.isBlank() ? "" : ": " + reason));
    }

    @Override
    public void onError(Exception ex) {
        statusConsumer.accept("实时通道错误: " + ex.getMessage());
    }
}
