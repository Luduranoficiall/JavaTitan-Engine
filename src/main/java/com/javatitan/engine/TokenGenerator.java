package com.javatitan.engine;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TokenGenerator {
    public static void main(String[] args) {
        String secret = System.getenv("JAVATITAN_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            System.err.println("JAVATITAN_JWT_SECRET nao definido");
            System.exit(1);
        }

        String plan = envOrDefault("JAVATITAN_JWT_PLAN", "PRO");
        String iss = trimOrNull(System.getenv("JAVATITAN_JWT_ISS"));
        String aud = trimOrNull(System.getenv("JAVATITAN_JWT_AUD"));
        long ttl = envLong("JAVATITAN_JWT_TTL", 3600L);

        String token = generateToken(secret, plan, ttl, iss, aud);
        System.out.println(token);
    }

    public static String generateToken(String secret, String plan, long ttlSeconds, String iss, String aud) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Secret obrigatorio");
        }
        String safePlan = (plan == null || plan.isBlank()) ? "PRO" : plan.trim();
        long exp = Instant.now().getEpochSecond() + Math.max(0, ttlSeconds);

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = buildPayload(safePlan, exp, iss, aud);

        String header64 = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload64 = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String message = header64 + "." + payload64;
        String signature = hmacSha256Base64Url(secret, message);

        return message + "." + signature;
    }

    private static String buildPayload(String plan, long exp, String iss, String aud) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"user\":\"demo\",\"plan\":\"")
            .append(escape(plan))
            .append("\",\"exp\":")
            .append(exp);

        if (iss != null) {
            sb.append(",\"iss\":\"").append(escape(iss)).append("\"");
        }
        if (aud != null) {
            sb.append(",\"aud\":\"").append(escape(aud)).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hmacSha256Base64Url(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return base64Url(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar token: " + ex.getMessage(), ex);
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static long envLong(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
