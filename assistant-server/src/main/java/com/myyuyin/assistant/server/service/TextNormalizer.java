package com.myyuyin.assistant.server.service;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .trim();
    }
}
