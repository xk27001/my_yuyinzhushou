package com.myyuyin.assistant.client;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class AudioCaptureService {
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MILLIS = 20;
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1,
            SAMPLE_RATE * 2 / 1000 * FRAME_MILLIS, SAMPLE_RATE, false);

    private final Object lock = new Object();
    private volatile boolean running;
    private TargetDataLine line;
    private Thread captureThread;
    private int threshold = 700;
    private int silenceMillis = 900;
    private int minSpeechMillis = 350;
    private int maxRecordMillis = 10000;

    @FunctionalInterface
    public interface WavListener {
        void onWav(byte[] wavBytes);
    }

    public List<String> listMicrophones() {
        List<String> names = new ArrayList<>();
        names.add("系统默认");
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT))) {
                names.add(info.getName());
            }
        }
        return names;
    }

    public void startContinuous(String mixerName, int threshold, int silenceMillis,
                                int minSpeechMillis, int maxRecordMillis, WavListener listener) {
        stop();
        this.threshold = threshold;
        this.silenceMillis = silenceMillis;
        this.minSpeechMillis = minSpeechMillis;
        this.maxRecordMillis = maxRecordMillis;
        try {
            line = openLine(mixerName);
            line.start();
            running = true;
            captureThread = new Thread(() -> captureContinuous(listener), "voice-continuous-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (LineUnavailableException ex) {
            throw new IllegalStateException("无法打开麦克风: " + ex.getMessage(), ex);
        }
    }

    public void startPushToTalk(String mixerName, WavListener listener) {
        stop();
        try {
            line = openLine(mixerName);
            line.start();
            running = true;
            captureThread = new Thread(() -> recordPushToTalk(listener), "voice-push-to-talk-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (LineUnavailableException ex) {
            throw new IllegalStateException("无法打开麦克风: " + ex.getMessage(), ex);
        }
    }

    public void stop() {
        running = false;
        synchronized (lock) {
            if (line != null) {
                line.stop();
                line.close();
                line = null;
            }
            captureThread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private TargetDataLine openLine(String mixerName) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        Mixer selected = findMixer(mixerName);
        if (selected == null) {
            return (TargetDataLine) AudioSystem.getLine(info);
        }
        return (TargetDataLine) selected.getLine(info);
    }

    private Mixer findMixer(String name) {
        if (name == null || name.isBlank() || "系统默认".equals(name)) {
            return null;
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name)) {
                return AudioSystem.getMixer(info);
            }
        }
        return null;
    }

    private void captureContinuous(WavListener listener) {
        int frameBytes = Math.max(320, SAMPLE_RATE * 2 * FRAME_MILLIS / 1000);
        int bytesPerMilli = SAMPLE_RATE * 2 / 1000;
        byte[] buffer = new byte[frameBytes];
        ByteArrayOutputStream speech = new ByteArrayOutputStream();
        Deque<byte[]> preRoll = new ArrayDeque<>();
        int preRollFrames = Math.max(1, 300 / FRAME_MILLIS);
        boolean speaking = false;
        long silenceMs = 0;
        long speechMs = 0;
        try {
            while (running) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    continue;
                }
                byte[] frame = java.util.Arrays.copyOf(buffer, read);
                boolean voice = rms(frame) >= threshold;
                if (!speaking) {
                    preRoll.addLast(frame);
                    while (preRoll.size() > preRollFrames) {
                        preRoll.removeFirst();
                    }
                    if (voice) {
                        speaking = true;
                        silenceMs = 0;
                        speechMs = 0;
                        while (!preRoll.isEmpty()) {
                            byte[] pending = preRoll.removeFirst();
                            speech.write(pending);
                            speechMs += pending.length / bytesPerMilli;
                        }
                    }
                    continue;
                }
                speech.write(frame);
                speechMs += read / bytesPerMilli;
                silenceMs = voice ? 0 : silenceMs + FRAME_MILLIS;
                boolean reachedSilence = silenceMs >= silenceMillis && speechMs >= minSpeechMillis;
                boolean reachedMaximum = speechMs >= maxRecordMillis;
                if (reachedSilence || reachedMaximum) {
                    emit(speech.toByteArray(), listener);
                    speech.reset();
                    preRoll.clear();
                    speaking = false;
                    silenceMs = 0;
                    speechMs = 0;
                }
            }
        } catch (Exception ex) {
            if (running) {
                throw new IllegalStateException("读取麦克风失败: " + ex.getMessage(), ex);
            }
        } finally {
            running = false;
        }
    }

    private void recordPushToTalk(WavListener listener) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1600];
        try {
            while (running) {
                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
        } catch (Exception ex) {
            if (running) {
                throw new IllegalStateException("读取麦克风失败: " + ex.getMessage(), ex);
            }
        } finally {
            running = false;
            if (output.size() > 1600) {
                emit(output.toByteArray(), listener);
            }
        }
    }

    private void emit(byte[] pcm, WavListener listener) {
        if (listener != null && pcm.length > 0) {
            listener.onWav(WavFileUtil.toWav(pcm, SAMPLE_RATE, 1, 16));
        }
    }

    private double rms(byte[] pcm) {
        if (pcm.length < 2) {
            return 0;
        }
        double sum = 0;
        int samples = pcm.length / 2;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples);
    }
}
