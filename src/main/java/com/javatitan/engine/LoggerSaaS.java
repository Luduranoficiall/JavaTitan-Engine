package com.javatitan.engine;

import java.time.LocalDateTime;

public class LoggerSaaS {
    public static void log(String level, String message) {
        String timestamp = LocalDateTime.now().toString();
        System.out.printf("[%s] [%s] %s%n", timestamp, level, message);
    }

    public static void log(String level, String requestId, String message) {
        String timestamp = LocalDateTime.now().toString();
        String id = (requestId == null) ? "-" : requestId;
        System.out.printf("[%s] [%s] [%s] %s%n", timestamp, level, id, message);
    }
}
