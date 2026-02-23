public record JwtConfig(String secret, String issuer, String audience, boolean requireExp) {
    public static JwtConfig fromEnv() {
        String secret = env("JAVATITAN_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JAVATITAN_JWT_SECRET nao definido");
        }
        String issuer = env("JAVATITAN_JWT_ISS");
        String audience = env("JAVATITAN_JWT_AUD");
        boolean requireExp = envBool("JAVATITAN_JWT_REQUIRE_EXP", true);
        return new JwtConfig(secret, normalize(issuer), normalize(audience), requireExp);
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
}
