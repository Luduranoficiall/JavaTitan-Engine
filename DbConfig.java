public record DbConfig(String url, String user, String password, String driver) {
    public static DbConfig fromEnv() {
        String url = normalize(env("JAVATITAN_DB_URL"));
        String user = normalize(env("JAVATITAN_DB_USER"));
        String password = env("JAVATITAN_DB_PASS");
        String driver = normalize(env("JAVATITAN_DB_DRIVER"));
        return new DbConfig(url, user, password, driver);
    }

    public boolean isEnabled() {
        return url != null && !url.isBlank();
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
}
