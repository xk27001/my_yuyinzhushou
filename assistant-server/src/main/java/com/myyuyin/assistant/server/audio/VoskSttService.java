package com.myyuyin.assistant.server.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyuyin.assistant.server.repository.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class VoskSttService {
    private static final Logger log = LoggerFactory.getLogger(VoskSttService.class);

    private final ConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    private final String fallbackModelPath;
    private volatile String loadedModelPath;
    private volatile Model model;

    public VoskSttService(ConfigRepository configRepository, ObjectMapper objectMapper,
                          @Value("${assistant.stt.model-path:/opt/vosk-model-small-cn}") String fallbackModelPath) {
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
        this.fallbackModelPath = fallbackModelPath;
    }

    public String recognize(byte[] wavBytes) {
        WavAudio wav = WavAudioUtil.parse(wavBytes);
        if (wav.channels() != 1 || wav.bitsPerSample() != 16) {
            throw new SpeechRecognitionException("语音识别要求 16 kHz 单声道 16 位 PCM WAV");
        }
        Model currentModel = model();
        try (Recognizer recognizer = new Recognizer(currentModel, wav.sampleRate())) {
            recognizer.acceptWaveForm(wav.pcmData(), wav.pcmData().length);
            String json = recognizer.getFinalResult();
            JsonNode node = objectMapper.readTree(json);
            String text = node.path("text").asText("").replace(" ", "").trim();
            log.debug("识别结果: {}", text);
            return text;
        } catch (IOException ex) {
            throw new SpeechRecognitionException("解析语音识别结果失败", ex);
        } catch (RuntimeException ex) {
            throw new SpeechRecognitionException("语音识别失败: " + ex.getMessage(), ex);
        }
    }

    private Model model() {
        String path = configRepository.getString("stt.model.path", fallbackModelPath);
        if (model != null && path.equals(loadedModelPath)) {
            return model;
        }
        synchronized (this) {
            if (model != null && path.equals(loadedModelPath)) {
                return model;
            }
            if (!Files.isDirectory(Path.of(path))) {
                throw new SpeechRecognitionException("Vosk 中文模型目录不存在: " + path);
            }
            try {
                Model loaded = new Model(path);
                model = loaded;
                loadedModelPath = path;
                return loaded;
            } catch (IOException ex) {
                throw new SpeechRecognitionException("加载 Vosk 模型失败: " + path, ex);
            }
        }
    }
}
