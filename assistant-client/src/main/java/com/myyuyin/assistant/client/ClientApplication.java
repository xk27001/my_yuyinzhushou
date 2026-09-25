package com.myyuyin.assistant.client;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

public final class ClientApplication {
    private ClientApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 10);
            ClientSettings settings = ClientSettings.load();
            new MainFrame(settings).setVisible(true);
        });
    }
}
