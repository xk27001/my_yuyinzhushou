package com.myyuyin.assistant.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseBootstrap {
    private DatabaseBootstrap() {
    }

    public static void ensureDatabase() {
        if (!Boolean.parseBoolean(env("DB_BOOTSTRAP_ENABLED", "true"))) {
            return;
        }
        String host = requiredEnv("DB_HOST");
        String port = env("DB_PORT", "3306");
        String database = env("DB_NAME", "myyuyinzhushou");
        String user = requiredEnv("DB_USER");
        String password = requiredEnv("DB_PASSWORD");
        if (!database.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("DB_NAME 只能包含字母、数字和下划线");
        }
        String url = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (SQLException ex) {
            throw new IllegalStateException("无法创建或检查 MySQL 数据库 " + database
                    + "，请确认账号具备 CREATE 权限，或先执行 scripts/init.sql", ex);
        }
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + key + "，请参考 .env.example 配置 .env");
        }
        return value;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
