package com.javatitan.engine;

public class TccRunner {
    public static void main(String[] args) throws Exception {
        RunnerOptions options = RunnerOptions.parse(args);

        AppConfig appConfig = AppConfig.fromEnv();
        DbConfig dbConfig = DbConfig.fromEnv();
        JwtConfig jwtConfig = buildJwtConfig();

        MotorFinanceiro.ServerHandle handle = MotorFinanceiro.startServer(appConfig, jwtConfig, dbConfig);
        LoggerSaaS.log("INFO", "[TCC] Servidor iniciado para validacao.");

        try {
            Thread.sleep(600);
            if (!options.noTest()) {
                String baseUrl = options.baseUrl();
                if (baseUrl == null || baseUrl.isBlank()) {
                    baseUrl = "http://localhost:" + appConfig.port();
                }

                String plan = options.plan();
                long ttl = options.ttlSeconds();
                String token = TokenGenerator.generateToken(jwtConfig.secret(), plan, ttl, jwtConfig.issuer(), jwtConfig.audience());

                if (options.smokeTest()) {
                    TccSmokeTest.run(baseUrl, token);
                } else {
                    TestClient.run(baseUrl, token);
                }
            }

            if (options.keepRunning()) {
                LoggerSaaS.log("INFO", "[TCC] Servidor em execucao. Ctrl+C para encerrar.");
                Thread.sleep(Long.MAX_VALUE);
            }
        } finally {
            handle.close();
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

    private record RunnerOptions(boolean keepRunning, boolean noTest, boolean smokeTest, String baseUrl, String plan, long ttlSeconds) {
        static RunnerOptions parse(String[] args) {
            boolean keepRunning = false;
            boolean noTest = false;
            boolean smokeTest = false;
            String baseUrl = null;
            String plan = envOrDefault("JAVATITAN_JWT_PLAN", "PRO");
            long ttl = envLong("JAVATITAN_JWT_TTL", 3600L);

            for (String arg : args) {
                if ("--keep-running".equals(arg)) {
                    keepRunning = true;
                } else if ("--no-test".equals(arg)) {
                    noTest = true;
                } else if (arg.startsWith("--base-url=")) {
                    baseUrl = arg.substring("--base-url=".length());
                } else if (arg.startsWith("--plan=")) {
                    plan = arg.substring("--plan=".length());
                } else if (arg.startsWith("--ttl=")) {
                    ttl = parseLongArg("--ttl", arg.substring("--ttl=".length()));
                } else if ("--smoke-test".equals(arg)) {
                    smokeTest = true;
                } else if ("--help".equals(arg)) {
                    printHelpAndExit();
                }
            }

            return new RunnerOptions(keepRunning, noTest, smokeTest, baseUrl, plan, ttl);
        }

        private static long parseLongArg(String name, String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static void printHelpAndExit() {
            System.out.println("Uso: java -cp out com.javatitan.engine.TccRunner [opcoes]");
            System.out.println("  --keep-running   Mantem o servidor ativo apos o teste");
            System.out.println("  --no-test        Nao executa o TestClient");
            System.out.println("  --smoke-test     Executa o TccSmokeTest (validacoes estritas)");
            System.out.println("  --base-url=URL   Base URL para o TestClient");
            System.out.println("  --plan=PLANO     Plano para gerar token (default PRO)");
            System.out.println("  --ttl=SEGUNDOS   TTL do token (default 3600)");
            System.exit(0);
        }
    }
}
