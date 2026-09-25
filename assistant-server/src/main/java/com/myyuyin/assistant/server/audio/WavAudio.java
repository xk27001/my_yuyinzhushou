package com.myyuyin.assistant.server.audio;

public record WavAudio(int sampleRate, int channels, int bitsPerSample, byte[] pcmData) {
}
