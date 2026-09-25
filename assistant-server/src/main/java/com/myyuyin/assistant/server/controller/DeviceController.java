package com.myyuyin.assistant.server.controller;

import com.myyuyin.assistant.common.ApiResponse;
import com.myyuyin.assistant.common.DeviceHeartbeatRequest;
import com.myyuyin.assistant.common.DeviceStatusResponse;
import com.myyuyin.assistant.common.InteractionLogView;
import com.myyuyin.assistant.server.repository.ConfigRepository;
import com.myyuyin.assistant.server.repository.DeviceRecord;
import com.myyuyin.assistant.server.repository.DeviceRepository;
import com.myyuyin.assistant.server.repository.InteractionLogRepository;
import com.myyuyin.assistant.server.service.AlarmService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceRepository deviceRepository;
    private final InteractionLogRepository logRepository;
    private final ConfigRepository configRepository;
    private final AlarmService alarmService;

    public DeviceController(DeviceRepository deviceRepository, InteractionLogRepository logRepository,
                            ConfigRepository configRepository, AlarmService alarmService) {
        this.deviceRepository = deviceRepository;
        this.logRepository = logRepository;
        this.configRepository = configRepository;
        this.alarmService = alarmService;
    }

    @GetMapping("/{deviceCode}")
    public ApiResponse<DeviceStatusResponse> status(@PathVariable String deviceCode) {
        List<DeviceRecord> devices = deviceRepository.findActive(deviceCode);
        if (devices.isEmpty()) {
            return ApiResponse.ok(new DeviceStatusResponse(deviceCode, null, false,
                    "UNKNOWN", 0, null));
        }
        DeviceRecord device = devices.getFirst();
        long timeout = configRepository.getInt("device.online_timeout_ms", 90000);
        boolean online = device.lastSeenAt() != null
                && System.currentTimeMillis() - device.lastSeenAt() <= timeout;
        return ApiResponse.ok(new DeviceStatusResponse(device.deviceCode(), device.clientName(),
                online, device.state(), device.lastSeenAt() == null ? 0 : device.lastSeenAt(),
                alarmService.active(deviceCode)));
    }

    @PostMapping("/{deviceCode}/heartbeat")
    public ApiResponse<DeviceStatusResponse> heartbeat(@PathVariable String deviceCode,
                                                        @RequestBody(required = false) DeviceHeartbeatRequest request) {
        String clientName = request == null ? null : request.clientName();
        String state = request == null || request.state() == null ? "IDLE" : request.state();
        DeviceRecord device = deviceRepository.findOrCreate(deviceCode, clientName);
        deviceRepository.touch(device.id(), state);
        return status(deviceCode);
    }

    @DeleteMapping("/{deviceCode}")
    public ApiResponse<Boolean> delete(@PathVariable String deviceCode) {
        return ApiResponse.ok(deviceRepository.softDelete(deviceCode));
    }

    @GetMapping("/{deviceCode}/logs")
    public ApiResponse<List<InteractionLogView>> logs(@PathVariable String deviceCode,
                                                       @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(logRepository.list(deviceCode, limit));
    }
}
