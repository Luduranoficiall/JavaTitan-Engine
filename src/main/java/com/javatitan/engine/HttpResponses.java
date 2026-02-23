package com.javatitan.engine;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public final class HttpResponses {
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    private HttpResponses() {}

    public static void sendJson(HttpExchange exchange, int status, String json, String requestId) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        if (requestId != null) {
            exchange.getResponseHeaders().set("X-Request-Id", requestId);
        }
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    public static String errorJson(int status, String message, String requestId) {
        String error = statusLabel(status);
        String timestamp = Instant.now().toString();
        return "{" +
            "\"status\":" + status + "," +
            "\"error\":\"" + JsonUtils.escapeJson(error) + "\"," +
            "\"message\":\"" + JsonUtils.escapeJson(message) + "\"," +
            "\"requestId\":\"" + JsonUtils.escapeJson(requestId) + "\"," +
            "\"timestamp\":\"" + timestamp + "\"" +
            "}";
    }

    public static Map<String, String> errorBody(int status, String message) {
        return Map.of(
            "status", String.valueOf(status),
            "error", statusLabel(status),
            "message", message
        );
    }

    private static String statusLabel(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }
}
