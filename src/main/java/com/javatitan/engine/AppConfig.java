package com.javatitan.engine;

public record AppConfig(
    int port,
    int httpThreads,
    int workerThreads,
    long simulatedDelayMs,
    boolean allowPlainWhenSecure
) {
    public static AppConfig fromEnv() {
        int port = envInt("JAVATITAN_PORT", 8080, 1, 65535);
        int httpThreads = envInt("JAVATITAN_HTTP_THREADS", Math.max(4, Runtime.getRuntime().availableProcessors()), 1, 512);
        int workerThreads = envInt("JAVATITAN_WORKER_THREADS", Math.max(2, Runtime.getRuntime().availableProcessors()), 1, 512);
        long delayMs = envLong("JAVATITAN_SIMULATED_DELAY_MS", 0L, 0L, 60000L);
        boolean allowPlain = envBool("JAVATITAN_ALLOW_PLAIN", false);
        return new AppConfig(port, httpThreads, workerThreads, delayMs, allowPlain);
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

    private static long envLong(String name, long defaultValue, long min, long max) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(name + " fora do intervalo: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }

    private static boolean envBool(String name, boolean defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }
}
