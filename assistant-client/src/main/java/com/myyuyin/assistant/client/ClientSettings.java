package com.myyuyin.assistant.client;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ClientSettings(String serverUrl, String deviceCode, String clientName) {
    public static ClientSettings load() {
        String serverUrl = firstNonBlank(System.getProperty("assistant.server.url"), System.getenv("ASSISTANT_SERVER_URL"));
        String deviceCode = firstNonBlank(System.getProperty("assistant.device.code"), System.getenv("ASSISTANT_DEVICE_CODE"));
        String clientName = firstNonBlank(System.getProperty("assistant.client.name"), System.getenv("ASSISTANT_CLIENT_NAME"));
        Path config = Path.of(System.getProperty("user.home"), ".myyuyin", "assistant-client.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(config)) {
            try (var input = Files.newInputStream(config)) {
                properties.load(input);
            } catch (Exception ignored) {
                // Bootstrap settings fall back to defaults when the local file is unreadable.
            }
        }
        serverUrl = firstNonBlank(serverUrl, properties.getProperty("server.url"), "http://umbrel.local:8080");
        deviceCode = firstNonBlank(deviceCode, properties.getProperty("device.code"), "client-001");
        clientName = firstNonBlank(clientName, properties.getProperty("client.name"), localHostName());
        return new ClientSettings(serverUrl.replaceAll("/+$", ""), deviceCode, clientName);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "语音助手客户端";
        }
    }
}
