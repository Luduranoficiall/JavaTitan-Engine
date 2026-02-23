package com.javatitan.engine;

public record AppConfig(int port, int httpThreads, int workerThreads) {
    public static AppConfig fromEnv() {
        int port = envInt("JAVATITAN_PORT", 8080, 1, 65535);
        int httpThreads = envInt("JAVATITAN_HTTP_THREADS", Math.max(4, Runtime.getRuntime().availableProcessors()), 1, 512);
        int workerThreads = envInt("JAVATITAN_WORKER_THREADS", Math.max(2, Runtime.getRuntime().availableProcessors()), 1, 512);
        return new AppConfig(port, httpThreads, workerThreads);
    }

    private static int envInt(String name, int defaultValue, int min, int max) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(name + " fora do intervalo: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }
}
