package com.myyuyin.assistant.client;

import com.myyuyin.assistant.common.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainFrame extends JFrame {
    private final JTextField serverField;
    private final JTextField deviceField;
    private final JTextField clientNameField;
    private final JLabel connectionLabel = valueLabel("未连接");
    private final JLabel realtimeLabel = valueLabel("未连接");
    private final JLabel listeningLabel = valueLabel("已停止");
    private final JLabel latencyLabel = valueLabel("-");
    private final JLabel alarmLabel = valueLabel("无");
    private final JLabel runtimeLabel = valueLabel("-");
    private final JLabel recognizedLabel = valueLabel("-");
    private final JLabel replyLabel = valueLabel("-");
    private final JTextField textInput = new JTextField();
    private final JComboBox<String> microphoneBox = new JComboBox<>();
    private final JTextArea logArea = new JTextArea(9, 80);
    private final ConfigTableModel configModel = new ConfigTableModel();
    private final JTable configTable = new JTable(configModel);
    private final AudioCaptureService captureService = new AudioCaptureService();
    private final AudioPlayerService playerService = new AudioPlayerService();
    private final Map<String, String> configCache = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "assistant-client-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ApiClient apiClient;
    private volatile AlarmRealtimeClient realtimeClient;
    private volatile boolean continuousListening;
    private volatile String clientState = "IDLE";

    public MainFrame(ClientSettings settings) {
        super("语音交互助手客户端");
        serverField = new JTextField(settings.serverUrl(), 22);
        deviceField = new JTextField(settings.deviceCode(), 12);
        clientNameField = new JTextField(settings.clientName(), 14);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 650));
        setSize(1180, 780);
        setLocationRelativeTo(null);
        buildUi();
        loadMicrophones();
        scheduler.scheduleAtFixedRate(this::heartbeat, 5, 30, TimeUnit.SECONDS);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                shutdown();
            }
        });
        connect();
    }

    private void buildUi() {
        setLayout(new BorderLayout(10, 10));
        add(buildTopPanel(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("运行监控", buildMonitorPanel());
        tabs.addTab("参数配置", buildConfigPanel());
        tabs.addTab("文本测试", buildTextPanel());
        add(tabs, BorderLayout.CENTER);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane logs = new JScrollPane(logArea);
        logs.setBorder(BorderFactory.createTitledBorder("运行日志"));
        add(logs, BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("服务端连接"));
        panel.add(new JLabel("服务端:"));
        panel.add(serverField);
        panel.add(new JLabel("设备编号:"));
        panel.add(deviceField);
        panel.add(new JLabel("客户端名称:"));
        panel.add(clientNameField);
        JButton connect = new JButton("连接 / 重连");
        connect.addActionListener(event -> connect());
        panel.add(connect);
        connectionLabel.setForeground(Color.RED);
        panel.add(connectionLabel);
        return panel;
    }

    private JPanel buildMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel status = new JPanel(new GridLayout(4, 2, 8, 8));
        status.setBorder(BorderFactory.createTitledBorder("运行状态"));
        status.add(new JLabel("服务端连接"));
        status.add(connectionLabel);
        status.add(new JLabel("实时告警通道"));
        status.add(realtimeLabel);
        status.add(new JLabel("麦克风监听"));
        status.add(listeningLabel);
        status.add(new JLabel("接口延迟"));
        status.add(latencyLabel);
        status.add(new JLabel("循环闹钟"));
        status.add(alarmLabel);
        status.add(new JLabel("客户端资源"));
        status.add(runtimeLabel);
        status.add(new JLabel("识别内容"));
        status.add(recognizedLabel);
        status.add(new JLabel("服务端回复"));
        status.add(replyLabel);
        panel.add(status, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        controls.setBorder(BorderFactory.createTitledBorder("操作"));
        controls.add(new JLabel("麦克风:"));
        controls.add(microphoneBox);
        JButton continuous = new JButton("开始连续监听");
        continuous.addActionListener(event -> toggleContinuous(continuous));
        controls.add(continuous);
        JButton pushToTalk = new JButton("按住说话");
        pushToTalk.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                startPushToTalk();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                captureService.stop();
            }
        });
        controls.add(pushToTalk);
        JButton cancelAlarm = new JButton("取消闹钟");
        cancelAlarm.addActionListener(event -> runAsync(() -> {
            Map<String, Object> result = client().cancelAlarm();
            appendLog("取消闹钟: " + result.get("replyText"));
            refreshStatus();
        }));
        controls.add(cancelAlarm);
        JButton refresh = new JButton("刷新状态");
        refresh.addActionListener(event -> runAsync(this::refreshStatus));
        controls.add(refresh);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        configTable.setRowHeight(25);
        configTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        configTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        configTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        configTable.getColumnModel().getColumn(3).setPreferredWidth(350);
        configTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        panel.add(new JScrollPane(configTable), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("重新加载");
        refresh.addActionListener(event -> runAsync(this::loadConfigs));
        JButton save = new JButton("保存修改");
        save.addActionListener(event -> runAsync(this::saveConfigs));
        buttons.add(refresh);
        buttons.add(save);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTextPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel input = new JPanel(new BorderLayout(8, 8));
        input.setBorder(BorderFactory.createTitledBorder("文本测试（启用唤醒时先输入“三角洲”）"));
        input.add(textInput, BorderLayout.CENTER);
        JButton send = new JButton("发送");
        send.addActionListener(event -> sendText());
        textInput.addActionListener(event -> sendText());
        input.add(send, BorderLayout.EAST);
        panel.add(input, BorderLayout.NORTH);
        return panel;
    }

    private void connect() {
        captureService.stop();
        continuousListening = false;
        listeningLabel.setText("已停止");
        closeRealtime();
        apiClient = new ApiClient(serverField.getText().trim(), deviceField.getText().trim(),
                clientNameField.getText().trim());
        runAsync(() -> {
            long start = System.nanoTime();
            boolean healthy = apiClient.health();
            long latency = (System.nanoTime() - start) / 1_000_000;
            if (!healthy) {
                throw new ApiException("无法连接服务端，请检查地址与 Umbrel 服务");
            }
            updateConnection(true, "已连接");
            latencyLabel.setText(latency + " ms");
            loadConfigs();
            refreshStatus();
            connectRealtime();
            appendLog("已连接服务端 " + serverField.getText().trim());
        });
    }

    private void connectRealtime() {
        closeRealtime();
        ApiClient client = client();
        AlarmRealtimeClient websocket = new AlarmRealtimeClient(URI.create(client.webSocketUrl()),
                this::handleRealtimeEvent,
                message -> SwingUtilities.invokeLater(() -> realtimeLabel.setText(message)));
        realtimeClient = websocket;
        websocket.connect();
    }

    private void closeRealtime() {
        AlarmRealtimeClient websocket = realtimeClient;
        realtimeClient = null;
        if (websocket != null) {
            try {
                websocket.close();
            } catch (Exception ignored) {
                // The socket may already be closed.
            }
        }
    }

    private void loadMicrophones() {
        microphoneBox.removeAllItems();
        try {
            captureService.listMicrophones().forEach(microphoneBox::addItem);
        } catch (Exception ex) {
            microphoneBox.addItem("系统默认");
            appendLog("枚举麦克风失败: " + ex.getMessage());
        }
    }

    private void toggleContinuous(JButton button) {
        if (continuousListening) {
            captureService.stop();
            continuousListening = false;
            clientState = "IDLE";
            listeningLabel.setText("已停止");
            button.setText("开始连续监听");
            appendLog("连续监听已停止");
            return;
        }
        try {
            captureService.startContinuous((String) microphoneBox.getSelectedItem(),
                    configInt("client.audio.threshold", 700),
                    configInt("client.audio.silence_ms", 900),
                    configInt("client.audio.min_speech_ms", 350),
                    configInt("client.audio.max_record_ms", 10000),
                    this::sendAudio);
            continuousListening = true;
            clientState = "LISTENING";
            listeningLabel.setText("连续监听中");
            button.setText("停止连续监听");
            appendLog("连续监听已启动，请说“三角洲”");
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void startPushToTalk() {
        try {
            captureService.startPushToTalk((String) microphoneBox.getSelectedItem(), this::sendAudio);
            clientState = "RECORDING";
            listeningLabel.setText("正在录音");
            appendLog("按住说话：正在录音，松开后发送");
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void sendAudio(byte[] wavBytes) {
        runAsync(() -> {
            long start = System.nanoTime();
            VoiceProcessResponse response = client().processAudio(wavBytes);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            latencyLabel.setText(elapsed + " ms");
            applyResponse(response);
        });
    }

    private void sendText() {
        String text = textInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        textInput.setText("");
        runAsync(() -> {
            VoiceProcessResponse response = client().processText(text);
            applyResponse(response);
        });
    }

    private void applyResponse(VoiceProcessResponse response) {
        SwingUtilities.invokeLater(() -> {
            recognizedLabel.setText(response.recognizedText() == null ? "-" : response.recognizedText());
            replyLabel.setText(response.replyText() == null ? "-" : response.replyText());
            if (response.replyText() != null && !response.replyText().isBlank()) {
                appendLog("识别: " + response.recognizedText() + " | 回复: " + response.replyText());
            }
            if (response.audioWavBase64() != null && !response.audioWavBase64().isBlank()) {
                playerService.play(Base64.getDecoder().decode(response.audioWavBase64()));
            }
        });
        runAsync(this::refreshStatus);
    }

    private void loadConfigs() {
        List<ConfigItem> items = client().configs();
        Map<String, String> values = new HashMap<>();
        for (ConfigItem item : items) {
            values.put(item.key(), item.value());
        }
        SwingUtilities.invokeLater(() -> {
            configCache.clear();
            configCache.putAll(values);
            configModel.setItems(items);
        });
    }

    private void saveConfigs() {
        for (ConfigItem item : configModel.items()) {
            if (item.editable()) {
                client().updateConfig(item.key(), item.value());
            }
        }
        loadConfigs();
        appendLog("参数已保存到服务端数据库");
    }

    private void refreshStatus() {
        DeviceStatusResponse status = client().status();
        if (status == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            updateConnection(status.online(), status.online() ? "在线" : "离线");
            if (status.activeAlarm() == null) {
                alarmLabel.setText("无");
            } else {
                long remaining = Math.max(0, status.activeAlarm().nextFireAt() - System.currentTimeMillis());
                long minutes = (remaining + 59_999) / 60_000;
                alarmLabel.setText(status.activeAlarm().durationMinutes() + " 分钟循环，还剩 "
                        + Math.max(1, minutes) + " 分钟");
            }
        });
    }

    private void heartbeat() {
        ApiClient client = apiClient;
        if (client == null) {
            return;
        }
        try {
            DeviceStatusResponse status = client.heartbeat(clientState);
            SwingUtilities.invokeLater(() -> {
                updateConnection(true, "已连接");
                runtimeLabel.setText(heapText());
                if (status != null && status.activeAlarm() != null) {
                    long remaining = Math.max(0, status.activeAlarm().nextFireAt() - System.currentTimeMillis());
                    alarmLabel.setText(status.activeAlarm().durationMinutes() + " 分钟循环，还剩 "
                            + Math.max(1, (remaining + 59_999) / 60_000) + " 分钟");
                }
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> updateConnection(false, "离线: " + ex.getMessage()));
        }
    }

    private void handleRealtimeEvent(WebSocketEvent event) {
        if ("CONNECTED".equals(event.type())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            appendLog("实时事件 " + event.type() + ": " + event.message());
            replyLabel.setText(event.message() == null ? "-" : event.message());
            if (event.audioWavBase64() != null && !event.audioWavBase64().isBlank()) {
                playerService.play(Base64.getDecoder().decode(event.audioWavBase64()));
            }
            runAsync(this::refreshStatus);
        });
    }

    private int configInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(configCache.getOrDefault(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private ApiClient client() {
        ApiClient client = apiClient;
        if (client == null) {
            throw new ApiException("尚未配置服务端连接");
        }
        return client;
    }

    private void runAsync(Runnable runnable) {
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception ex) {
                showError(ex);
            }
        }, "assistant-client-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        SwingUtilities.invokeLater(() -> {
            appendLog("错误: " + message);
            JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void updateConnection(boolean online, String text) {
        connectionLabel.setText(text);
        connectionLabel.setForeground(online ? new Color(0, 128, 0) : Color.RED);
    }

    private void appendLog(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendLog(text));
            return;
        }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + time + "] " + text + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String heapText() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long max = runtime.maxMemory() / 1024 / 1024;
        return "堆 " + used + "/" + max + " MB，CPU " + runtime.availableProcessors() + " 核";
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private void shutdown() {
        captureService.stop();
        closeRealtime();
        playerService.stop();
        scheduler.shutdownNow();
    }
}
