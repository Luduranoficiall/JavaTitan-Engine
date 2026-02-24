package com.javatitan.engine;

import java.nio.file.Files;
import java.nio.file.Path;

public class TccReportGenerator {
    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);

        JwtConfig jwtConfig = buildJwtConfig();
        CryptoConfig cryptoConfig = CryptoConfig.fromEnv();
        TlsConfig tlsConfig = TlsConfig.fromEnv();
        ClientTlsConfig clientTls = ClientTlsConfig.fromEnv(tlsConfig);

        String baseUrl = options.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            String scheme = tlsConfig.enabled() ? "https" : "http";
            baseUrl = scheme + "://localhost:" + options.port();
        }

        String token = TokenGenerator.generateToken(jwtConfig.secret(), options.plan(), options.ttlSeconds(), jwtConfig.issuer(), jwtConfig.audience());

        TccSmokeTest.SmokeReport report = TccSmokeTest.runReport(baseUrl, token, cryptoConfig, clientTls);

        Files.createDirectories(options.outDir());
        Path jsonPath = options.outDir().resolve(options.name() + ".json");
        Path csvPath = options.outDir().resolve(options.name() + ".csv");
        Path txtPath = options.outDir().resolve(options.name() + ".txt");

        Files.writeString(jsonPath, report.toJson());
        String csv = report.toCsvHeader() + System.lineSeparator() + report.toCsvRow();
        Files.writeString(csvPath, csv);
        Files.writeString(txtPath, report.toTxtSummary());

        System.out.println("JSON: " + jsonPath.toAbsolutePath());
        System.out.println("CSV: " + csvPath.toAbsolutePath());
        System.out.println("TXT: " + txtPath.toAbsolutePath());

        if (!report.passed()) {
            System.exit(1);
        }
    }

    private static JwtConfig buildJwtConfig() {
        String secret = envOrDefault("JAVATITAN_JWT_SECRET", "super-secret");
        String issuer = trimOrNull(System.getenv("JAVATITAN_JWT_ISS"));
        String audience = trimOrNull(System.getenv("JAVATITAN_JWT_AUD"));
        boolean requireExp = envBool("JAVATITAN_JWT_REQUIRE_EXP", true);
        long clockSkew = envLong("JAVATITAN_JWT_CLOCK_SKEW", 30L);
        return new JwtConfig(secret, issuer, audience, requireExp, clockSkew);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static boolean envBool(String name, boolean defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
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

    private record Options(Path outDir, String name, String baseUrl, String plan, long ttlSeconds, int port) {
        static Options parse(String[] args) {
            Path outDir = Path.of("reports");
            String name = "tcc-report";
            String baseUrl = null;
            String plan = envOrDefault("JAVATITAN_JWT_PLAN", "PRO");
            long ttl = envLong("JAVATITAN_JWT_TTL", 3600L);
            int port = envInt("JAVATITAN_PORT", 8080);

            for (String arg : args) {
                if (arg.startsWith("--out-dir=")) {
                    outDir = Path.of(arg.substring("--out-dir=".length()));
                } else if (arg.startsWith("--name=")) {
                    name = arg.substring("--name=".length());
                } else if (arg.startsWith("--base-url=")) {
                    baseUrl = arg.substring("--base-url=".length());
                } else if (arg.startsWith("--plan=")) {
                    plan = arg.substring("--plan=".length());
                } else if (arg.startsWith("--ttl=")) {
                    ttl = parseLongArg("--ttl", arg.substring("--ttl=".length()));
                } else if (arg.startsWith("--port=")) {
                    port = parseIntArg("--port", arg.substring("--port=".length()));
                } else if (arg.equals("--help")) {
                    printHelpAndExit();
                }
            }

            return new Options(outDir, name, baseUrl, plan, ttl, port);
        }

        private static long parseLongArg(String name, String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static int parseIntArg(String name, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static void printHelpAndExit() {
            System.out.println("TccReportGenerator");
            System.out.println("  --out-dir=DIR   Diretorio de saida (default reports)");
            System.out.println("  --name=NOME     Nome base (default tcc-report)");
            System.out.println("  --base-url=URL  Base URL do servidor");
            System.out.println("  --plan=PLANO    Plano do token");
            System.out.println("  --ttl=SEGUNDOS  TTL do token");
            System.exit(0);
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

        private static int envInt(String name, int defaultValue) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }
    }
}
