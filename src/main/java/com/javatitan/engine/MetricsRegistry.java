package com.javatitan.engine;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsRegistry {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successRequests = new LongAdder();
    private final LongAdder failureRequests = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxDurationMs = new AtomicLong(0);
    private final AtomicLong lastRequestAtMs = new AtomicLong(0);

    public void record(boolean success, long durationMs) {
        totalRequests.increment();
        if (success) {
            successRequests.increment();
        } else {
            failureRequests.increment();
        }
        totalDurationMs.add(durationMs);
        lastRequestAtMs.set(System.currentTimeMillis());
        updateMin(durationMs);
        updateMax(durationMs);
    }

    public String toJson(boolean secureMode) {
        long total = totalRequests.sum();
        long success = successRequests.sum();
        long failure = failureRequests.sum();
        long totalDuration = totalDurationMs.sum();
        long avg = total == 0 ? 0 : totalDuration / total;
        long min = minDurationMs.get();
        long max = maxDurationMs.get();
        long last = lastRequestAtMs.get();
        String lastIso = last == 0 ? "" : Instant.ofEpochMilli(last).toString();

        return "{" +
            "\"totalRequests\":" + total + "," +
            "\"successRequests\":" + success + "," +
            "\"failureRequests\":" + failure + "," +
            "\"avgDurationMs\":" + avg + "," +
            "\"minDurationMs\":" + (min == Long.MAX_VALUE ? 0 : min) + "," +
            "\"maxDurationMs\":" + max + "," +
            "\"secureMode\":" + secureMode + "," +
            "\"lastRequestAt\":\"" + JsonUtils.escapeJson(lastIso) + "\"" +
            "}";
    }

    private void updateMin(long value) {
        long current;
        do {
            current = minDurationMs.get();
            if (value >= current) {
                return;
            }
        } while (!minDurationMs.compareAndSet(current, value));
    }

    private void updateMax(long value) {
        long current;
        do {
            current = maxDurationMs.get();
            if (value <= current) {
                return;
            }
        } while (!maxDurationMs.compareAndSet(current, value));
    }
}
