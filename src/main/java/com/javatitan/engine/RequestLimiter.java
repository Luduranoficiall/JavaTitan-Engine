package com.javatitan.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestLimiter {
    private final int maxPerWindow;
    private final long windowMs;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RequestLimiter(int maxPerWindow, long windowMs) {
        this.maxPerWindow = maxPerWindow;
        this.windowMs = windowMs;
    }

    public boolean enabled() {
        return maxPerWindow > 0;
    }

    public boolean tryAcquire(String key) {
        if (!enabled()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));
        boolean allowed;
        synchronized (window) {
            if (now - window.windowStart >= windowMs) {
                window.windowStart = now;
                window.count = 0;
            }
            allowed = window.count < maxPerWindow;
            if (allowed) {
                window.count++;
            }
        }
        if (!allowed && now - window.windowStart > windowMs * 2) {
            windows.remove(key, window);
        }
        return allowed;
    }

    private static class Window {
        private long windowStart;
        private int count;

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
