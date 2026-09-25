package com.myyuyin.assistant.server.controller;

import com.myyuyin.assistant.common.ApiResponse;
import com.myyuyin.assistant.common.TextProcessRequest;
import com.myyuyin.assistant.common.VoiceProcessResponse;
import com.myyuyin.assistant.server.audio.VoskSttService;
import com.myyuyin.assistant.server.service.CommandDispatchService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
public class AssistantController {
    private final VoskSttService sttService;
    private final CommandDispatchService dispatchService;

    public AssistantController(VoskSttService sttService, CommandDispatchService dispatchService) {
        this.sttService = sttService;
        this.dispatchService = dispatchService;
    }

    @PostMapping(value = "/voice/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VoiceProcessResponse> processVoice(@RequestParam @NotBlank String deviceCode,
                                                           @RequestParam(required = false) String clientName,
                                                           @RequestPart MultipartFile audio) throws IOException {
        String recognized = sttService.recognize(audio.getBytes());
        if (recognized.isBlank()) {
            return ApiResponse.ok(new VoiceProcessResponse(deviceCode, "", null,
                    com.myyuyin.assistant.common.CommandType.IGNORED, true,
                    null, null, System.currentTimeMillis()));
        }
        return ApiResponse.ok(dispatchService.processText(deviceCode, clientName, recognized, "VOICE"));
    }

    @PostMapping("/text/process")
    public ApiResponse<VoiceProcessResponse> processText(@RequestBody TextProcessRequest request) {
        return ApiResponse.ok(dispatchService.processText(request.deviceCode(), request.clientName(),
                request.text(), "TEXT"));
    }
}
