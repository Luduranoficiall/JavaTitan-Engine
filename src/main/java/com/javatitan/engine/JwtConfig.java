package com.javatitan.engine;

public record JwtConfig(
    String secret,
    String issuer,
    String audience,
    boolean requireExp,
    long clockSkewSeconds
) {
    public static JwtConfig fromEnv() {
        String secret = env("JAVATITAN_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JAVATITAN_JWT_SECRET nao definido");
        }
        String issuer = normalize(env("JAVATITAN_JWT_ISS"));
        String audience = normalize(env("JAVATITAN_JWT_AUD"));
        boolean requireExp = envBool("JAVATITAN_JWT_REQUIRE_EXP", true);
        long clockSkew = envLong("JAVATITAN_JWT_CLOCK_SKEW", 30L);
        return new JwtConfig(secret, issuer, audience, requireExp, clockSkew);
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean envBool(String name, boolean defaultValue) {
        String value = env(name);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }

    private static long envLong(String name, long defaultValue) {
        String value = env(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }
}
