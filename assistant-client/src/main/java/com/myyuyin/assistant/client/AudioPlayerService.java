package com.myyuyin.assistant.client;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;

public class AudioPlayerService {
    private volatile Clip current;

    public void play(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length == 0) {
            return;
        }
        stop();
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes))) {
            Clip clip = AudioSystem.getClip();
            clip.open(input);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            current = clip;
            clip.start();
        } catch (Exception ex) {
            throw new IllegalStateException("播放语音失败: " + ex.getMessage(), ex);
        }
    }

    public void stop() {
        Clip clip = current;
        if (clip != null) {
            clip.stop();
            clip.close();
            current = null;
        }
    }
}
