package com.myyuyin.assistant.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.myyuyin.assistant.common.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ApiClient {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String serverUrl;
    private final String deviceCode;
    private final String clientName;

    public ApiClient(String serverUrl, String deviceCode, String clientName) {
        this.serverUrl = serverUrl.replaceAll("/+$", "");
        this.deviceCode = deviceCode;
        this.clientName = clientName;
    }

    public boolean health() {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri("/actuator/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            return "UP".equalsIgnoreCase(objectMapper.readTree(response.body()).path("status").asText());
        } catch (Exception ex) {
            return false;
        }
    }

    public VoiceProcessResponse processAudio(byte[] wavBytes) {
        String boundary = "----MyYuyin" + System.nanoTime();
        byte[] body = multipartBody(boundary, wavBytes);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/voice/process"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return sendApi(request, new TypeReference<ApiResponse<VoiceProcessResponse>>() { });
    }

    public VoiceProcessResponse processText(String text) {
        Map<String, Object> body = Map.of("deviceCode", deviceCode, "clientName", clientName, "text", text);
        return postJson("/api/v1/text/process", body, new TypeReference<ApiResponse<VoiceProcessResponse>>() { });
    }

    public DeviceStatusResponse status() {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/devices/" + deviceCode))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return sendApi(request, new TypeReference<ApiResponse<DeviceStatusResponse>>() { });
    }

    public DeviceStatusResponse heartbeat(String state) {
        Map<String, Object> body = Map.of("clientName", clientName, "state", state);
        return postJson("/api/v1/devices/" + deviceCode + "/heartbeat", body,
                new TypeReference<ApiResponse<DeviceStatusResponse>>() { });
    }

    public List<ConfigItem> configs() {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/config"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return sendApi(request, new TypeReference<ApiResponse<List<ConfigItem>>>() { });
    }

    public ConfigItem updateConfig(String key, String value) {
        Map<String, Object> body = Map.of("value", value);
        return putJson("/api/v1/config/" + key, body, new TypeReference<ApiResponse<ConfigItem>>() { });
    }

    public AlarmView activeAlarm() {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/alarms/" + deviceCode + "/active"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return sendApi(request, new TypeReference<ApiResponse<AlarmView>>() { });
    }

    public Map<String, Object> cancelAlarm() {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/alarms/" + deviceCode + "/active"))
                .timeout(Duration.ofSeconds(5)).DELETE().build();
        return sendApi(request, new TypeReference<ApiResponse<Map<String, Object>>>() { });
    }

    public String webSocketUrl() {
        String ws = serverUrl.startsWith("https://") ? serverUrl.replaceFirst("https://", "wss://")
                : serverUrl.replaceFirst("http://", "ws://");
        return ws + "/ws/devices?deviceCode=" + urlEncode(deviceCode);
    }

    private <T> T postJson(String path, Object body, TypeReference<ApiResponse<T>> type) {
        return sendApi(jsonRequest(path, body, "POST"), type);
    }

    private <T> T putJson(String path, Object body, TypeReference<ApiResponse<T>> type) {
        return sendApi(jsonRequest(path, body, "PUT"), type);
    }

    private HttpRequest jsonRequest(String path, Object body, String method) {
        try {
            return HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
        } catch (Exception ex) {
            throw new ApiException("构造请求失败", ex);
        }
    }

    private <T> T sendApi(HttpRequest request, TypeReference<ApiResponse<T>> type) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("服务端 HTTP " + response.statusCode() + ": " + response.body());
            }
            ApiResponse<T> wrapper = objectMapper.readValue(response.body(), type);
            if (!wrapper.success()) {
                throw new ApiException(wrapper.message());
            }
            return wrapper.data();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("请求服务端失败: " + ex.getMessage(), ex);
        }
    }

    private byte[] multipartBody(String boundary, byte[] audio) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(audio.length + 512);
            writeField(output, boundary, "deviceCode", deviceCode);
            writeField(output, boundary, "clientName", clientName);
            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"audio\"; filename=\"recording.wav\"\r\n");
            writeAscii(output, "Content-Type: audio/wav\r\n\r\n");
            output.write(audio);
            writeAscii(output, "\r\n--" + boundary + "--\r\n");
            return output.toByteArray();
        } catch (Exception ex) {
            throw new ApiException("构造语音上传请求失败", ex);
        }
    }

    private void writeField(ByteArrayOutputStream output, String boundary, String name, String value) throws Exception {
        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        writeAscii(output, "\r\n");
    }

    private void writeAscii(ByteArrayOutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private URI uri(String path) {
        return URI.create(serverUrl + path);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
