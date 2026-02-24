package com.javatitan.engine;

import java.nio.file.Files;
import java.nio.file.Path;

public class OneClickRunner {
    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);

        AppConfig appConfig = AppConfig.fromEnv();
        DbConfig dbConfig = DbConfig.fromEnv();

        String jwtSecret = envOrDefault("JAVATITAN_JWT_SECRET", "super-secret");
        JwtConfig jwtConfig = new JwtConfig(jwtSecret, trimOrNull(System.getenv("JAVATITAN_JWT_ISS")), trimOrNull(System.getenv("JAVATITAN_JWT_AUD")), true, 30L);

        byte[] aesKey = AesKeyGenerator.generateKeyBytes(options.aesKeySize());
        String aesKeyBase64 = java.util.Base64.getEncoder().encodeToString(aesKey);
        CryptoConfig cryptoConfig = new CryptoConfig(true, aesKey);

        Path securityDir = options.securityDir();
        Files.createDirectories(securityDir);
        String password = options.password();

        KeystoreGenerator.main(new String[] {
            "--out-dir=" + securityDir,
            "--type=" + options.storeType(),
            "--password=" + password,
            "--mtls"
        });

        String extension = options.storeType().equalsIgnoreCase("PKCS12") ? ".p12" : ".jks";
        Path serverKeystore = securityDir.resolve("server-keystore" + extension);
        Path serverTruststore = securityDir.resolve("server-truststore" + extension);
        Path clientKeystore = securityDir.resolve("client-keystore" + extension);
        Path clientTruststore = securityDir.resolve("client-truststore" + extension);

        writeEnvFile(options.envPath(), appConfig, jwtConfig, jwtSecret, options, aesKeyBase64, serverKeystore, serverTruststore, clientKeystore, clientTruststore);

        TlsConfig tlsConfig = new TlsConfig(true,
            serverKeystore.toString(),
            password,
            options.storeType(),
            null,
            serverTruststore.toString(),
            password,
            options.storeType(),
            true
        );

        ClientTlsConfig clientTls = new ClientTlsConfig(true,
            clientKeystore.toString(),
            password,
            options.storeType(),
            null,
            clientTruststore.toString(),
            password,
            options.storeType()
        );

        MotorFinanceiro.ServerHandle handle = MotorFinanceiro.startServer(appConfig, jwtConfig, dbConfig, cryptoConfig, tlsConfig);
        try {
            Thread.sleep(600);
            String baseUrl = "https://localhost:" + appConfig.port();
            String token = TokenGenerator.generateToken(jwtSecret, options.plan(), options.ttlSeconds(), jwtConfig.issuer(), jwtConfig.audience());

            TccSmokeTest.SmokeReport report = TccSmokeTest.runReport(baseUrl, token, cryptoConfig, clientTls);

            Files.createDirectories(options.reportDir());
            Path jsonPath = options.reportDir().resolve(options.reportName() + ".json");
            Path csvPath = options.reportDir().resolve(options.reportName() + ".csv");
            Path txtPath = options.reportDir().resolve(options.reportName() + ".txt");
            Files.writeString(jsonPath, report.toJson());
            Files.writeString(csvPath, report.toCsvHeader() + System.lineSeparator() + report.toCsvRow());
            Files.writeString(txtPath, report.toTxtSummary());

            System.out.println("AES_KEY_BASE64=" + aesKeyBase64);
            System.out.println("Server keystore: " + serverKeystore);
            System.out.println("Server truststore: " + serverTruststore);
            System.out.println("Client keystore: " + clientKeystore);
            System.out.println("Client truststore: " + clientTruststore);
            System.out.println("Env file: " + options.envPath().toAbsolutePath());
            System.out.println("Report JSON: " + jsonPath.toAbsolutePath());
            System.out.println("Report CSV: " + csvPath.toAbsolutePath());
            System.out.println("Report TXT: " + txtPath.toAbsolutePath());

            if (!report.passed()) {
                System.exit(1);
            }

            if (options.keepRunning()) {
                System.out.println("Servidor em execucao. Ctrl+C para encerrar.");
                Thread.sleep(Long.MAX_VALUE);
            }
        } finally {
            handle.close();
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void writeEnvFile(Path envPath, AppConfig appConfig, JwtConfig jwtConfig, String jwtSecret, Options options, String aesKeyBase64,
                                    Path serverKeystore, Path serverTruststore, Path clientKeystore, Path clientTruststore) throws Exception {
        java.nio.file.Path target = envPath.toAbsolutePath().normalize();
        java.nio.file.Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Gerado pelo OneClickRunner (nao commitar)").append(System.lineSeparator());
        appendEnv(sb, "JAVATITAN_PORT", String.valueOf(appConfig.port()));
        appendEnv(sb, "JAVATITAN_ALLOW_PLAIN", "false");
        appendEnv(sb, "JAVATITAN_MAX_BODY_KB", String.valueOf(Math.max(1, appConfig.maxBodyBytes() / 1024)));
        appendEnv(sb, "JAVATITAN_PROCESS_TIMEOUT_MS", String.valueOf(appConfig.processingTimeoutMs()));
        appendEnv(sb, "JAVATITAN_RATE_LIMIT_PER_MIN", String.valueOf(appConfig.rateLimitPerMinute()));
        appendEnv(sb, "JAVATITAN_METRICS_ENABLED", String.valueOf(appConfig.metricsEnabled()));
        appendEnv(sb, "JAVATITAN_SECURE_MODE", "true");
        appendEnv(sb, "JAVATITAN_AES_KEY", aesKeyBase64);
        appendEnv(sb, "JAVATITAN_JWT_SECRET", jwtSecret);
        if (jwtConfig.issuer() != null) {
            appendEnv(sb, "JAVATITAN_JWT_ISS", jwtConfig.issuer());
        }
        if (jwtConfig.audience() != null) {
            appendEnv(sb, "JAVATITAN_JWT_AUD", jwtConfig.audience());
        }
        appendEnv(sb, "JAVATITAN_JWT_PLAN", options.plan());
        appendEnv(sb, "JAVATITAN_JWT_TTL", String.valueOf(options.ttlSeconds()));

        appendEnv(sb, "JAVATITAN_TLS_REQUIRE_CLIENT_AUTH", "true");
        appendEnv(sb, "JAVATITAN_TLS_KEYSTORE_PATH", normalizePath(serverKeystore));
        appendEnv(sb, "JAVATITAN_TLS_KEYSTORE_PASSWORD", options.password());
        appendEnv(sb, "JAVATITAN_TLS_KEYSTORE_TYPE", options.storeType());
        appendEnv(sb, "JAVATITAN_TLS_TRUSTSTORE_PATH", normalizePath(serverTruststore));
        appendEnv(sb, "JAVATITAN_TLS_TRUSTSTORE_PASSWORD", options.password());
        appendEnv(sb, "JAVATITAN_TLS_TRUSTSTORE_TYPE", options.storeType());

        appendEnv(sb, "JAVATITAN_CLIENT_KEYSTORE_PATH", normalizePath(clientKeystore));
        appendEnv(sb, "JAVATITAN_CLIENT_KEYSTORE_PASSWORD", options.password());
        appendEnv(sb, "JAVATITAN_CLIENT_KEYSTORE_TYPE", options.storeType());
        appendEnv(sb, "JAVATITAN_CLIENT_TRUSTSTORE_PATH", normalizePath(clientTruststore));
        appendEnv(sb, "JAVATITAN_CLIENT_TRUSTSTORE_PASSWORD", options.password());
        appendEnv(sb, "JAVATITAN_CLIENT_TRUSTSTORE_TYPE", options.storeType());

        Files.writeString(target, sb.toString());
    }

    private static void appendEnv(StringBuilder sb, String key, String value) {
        if (value == null) {
            return;
        }
        sb.append(key).append("=").append(escapeEnv(value)).append(System.lineSeparator());
    }

    private static String escapeEnv(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private record Options(Path securityDir, Path reportDir, Path envPath, String reportName, String storeType, String password, int aesKeySize, String plan, long ttlSeconds, boolean keepRunning) {
        static Options parse(String[] args) {
            Path securityDir = Path.of("security");
            Path reportDir = Path.of("reports");
            Path envPath = Path.of(".env.tcc");
            String reportName = "tcc-oneclick";
            String storeType = "JKS";
            String password = envOrDefault("JAVATITAN_KEYSTORE_PASSWORD", "changeit");
            int aesKeySize = 32;
            String plan = envOrDefault("JAVATITAN_JWT_PLAN", "PRO");
            long ttl = envLong("JAVATITAN_JWT_TTL", 3600L);
            boolean keepRunning = false;

            for (String arg : args) {
                if (arg.startsWith("--security-dir=")) {
                    securityDir = Path.of(arg.substring("--security-dir=".length()));
                } else if (arg.startsWith("--report-dir=")) {
                    reportDir = Path.of(arg.substring("--report-dir=".length()));
                } else if (arg.startsWith("--env-path=")) {
                    envPath = Path.of(arg.substring("--env-path=".length()));
                } else if (arg.startsWith("--report-name=")) {
                    reportName = arg.substring("--report-name=".length());
                } else if (arg.startsWith("--store-type=")) {
                    storeType = arg.substring("--store-type=".length());
                } else if (arg.startsWith("--password=")) {
                    password = arg.substring("--password=".length());
                } else if (arg.startsWith("--aes-size=")) {
                    aesKeySize = parseInt("--aes-size", arg.substring("--aes-size=".length()));
                } else if (arg.startsWith("--plan=")) {
                    plan = arg.substring("--plan=".length());
                } else if (arg.startsWith("--ttl=")) {
                    ttl = parseLong("--ttl", arg.substring("--ttl=".length()));
                } else if (arg.equals("--keep-running")) {
                    keepRunning = true;
                } else if (arg.equals("--help")) {
                    printHelpAndExit();
                }
            }

            return new Options(securityDir, reportDir, envPath, reportName, storeType, password, aesKeySize, plan, ttl, keepRunning);
        }

        private static int parseInt(String name, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static long parseLong(String name, String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static void printHelpAndExit() {
            System.out.println("OneClickRunner (TCC)");
            System.out.println("  --security-dir=DIR   Diretorio de keystores (default security)");
            System.out.println("  --report-dir=DIR     Diretorio de relatorios (default reports)");
            System.out.println("  --env-path=ARQUIVO   Caminho do .env.tcc (default .env.tcc)");
            System.out.println("  --report-name=NOME   Nome base do relatorio (default tcc-oneclick)");
            System.out.println("  --store-type=JKS     Tipo de keystore (JKS|PKCS12)");
            System.out.println("  --password=PASS      Senha do keystore (default changeit)");
            System.out.println("  --aes-size=32        Tamanho da chave AES (16/24/32)");
            System.out.println("  --plan=PLANO         Plano do token (default PRO)");
            System.out.println("  --ttl=SEGUNDOS       TTL do token (default 3600)");
            System.out.println("  --keep-running       Mantem o servidor ativo apos o teste");
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
    }
}
