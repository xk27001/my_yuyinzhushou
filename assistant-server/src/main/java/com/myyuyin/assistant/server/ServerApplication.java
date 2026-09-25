package com.myyuyin.assistant.server;

import com.myyuyin.assistant.server.config.DatabaseBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        DatabaseBootstrap.ensureDatabase();
        SpringApplication.run(ServerApplication.class, args);
    }
}
