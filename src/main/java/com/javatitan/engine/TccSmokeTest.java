package com.javatitan.engine;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class TccSmokeTest {
    public static void main(String[] args) throws Exception {
        String baseUrl = envOrDefault("JAVATITAN_BASE_URL", "http://localhost:8080");
        String token = System.getenv("JAVATITAN_JWT_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("JAVATITAN_JWT_TOKEN nao definido");
            System.exit(1);
        }

        CryptoConfig cryptoConfig = CryptoConfig.fromEnv();
        ClientTlsConfig tlsConfig = ClientTlsConfig.fromEnv(TlsConfig.fromEnv());

        SmokeReport report = runReport(baseUrl, token.trim(), cryptoConfig, tlsConfig);
        report.print();
        report.writeIfConfigured();
        report.writeCsvIfConfigured();

        if (!report.passed()) {
            System.exit(1);
        }
    }

    public static SmokeReport run(String baseUrl, String token, CryptoConfig cryptoConfig, ClientTlsConfig tlsConfig) throws Exception {
        return runInternal(baseUrl, token, cryptoConfig, tlsConfig, true);
    }

    public static SmokeReport runReport(String baseUrl, String token, CryptoConfig cryptoConfig, ClientTlsConfig tlsConfig) throws Exception {
        return runInternal(baseUrl, token, cryptoConfig, tlsConfig, false);
    }

    private static SmokeReport runInternal(String baseUrl, String token, CryptoConfig cryptoConfig, ClientTlsConfig tlsConfig, boolean throwOnError) throws Exception {
        long start = System.currentTimeMillis();
        HttpClient client = HttpClientFactory.create(tlsConfig);

        SmokeReport report = new SmokeReport(baseUrl, cryptoConfig != null && cryptoConfig.secureMode());
        try {
            validarHealth(client, baseUrl, report);
            validarCalculo(client, baseUrl, token, cryptoConfig, report);
            if (envBool("JAVATITAN_SMOKE_CHECK_METRICS", false) && envBool("JAVATITAN_METRICS_ENABLED", true)) {
                validarMetrics(client, baseUrl, report);
            }
            report.setPassed(true);
        } catch (Exception ex) {
            report.setPassed(false);
            report.setError(ex.getMessage());
            if (throwOnError) {
                throw ex;
            }
        } finally {
            report.setDurationMs(System.currentTimeMillis() - start);
        }
        return report;
    }

    private static void validarHealth(HttpClient client, String baseUrl, SmokeReport report) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/health"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertStatus(response.statusCode(), 200, "health status", report);

        String status = JsonUtils.readString(response.body(), "status");
        if (!"UP".equalsIgnoreCase(status)) {
            throw new IllegalStateException("health status invalido: " + status);
        }
        report.addCheck("health", "ok");
    }

    private static void validarCalculo(HttpClient client, String baseUrl, String token, CryptoConfig cryptoConfig, SmokeReport report) throws Exception {
        String payload = "{\"idCliente\":\"e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1\"," +
            "\"valorBruto\":1000.00,\"plano\":\"PRO\"}";

        String path = "/api/calcular";
        String body = payload;

        if (cryptoConfig != null && cryptoConfig.secureMode()) {
            path = "/api/calcular-secure";
            CryptoUtils.EncryptedPayload encrypted = CryptoUtils.encrypt(payload, cryptoConfig.aesKey());
            body = CryptoUtils.writePayload(encrypted);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertStatus(response.statusCode(), 200, "calcular status", report);

        String bodyResponse = response.body();
        if (cryptoConfig != null && cryptoConfig.secureMode()) {
            CryptoUtils.EncryptedPayload encrypted = CryptoUtils.readPayload(bodyResponse);
            bodyResponse = CryptoUtils.decrypt(encrypted, cryptoConfig.aesKey());
        }

        String idProposta = JsonUtils.readRequiredString(bodyResponse, "idProposta");
        UUID.fromString(idProposta);

        BigDecimal valorLiquido = JsonUtils.readRequiredBigDecimal(bodyResponse, "valorLiquido");
        BigDecimal taxaAplicada = JsonUtils.readRequiredBigDecimal(bodyResponse, "taxaAplicada");
        String status = JsonUtils.readRequiredString(bodyResponse, "status");

        if (!"PROCESSADO_ASYNC".equalsIgnoreCase(status)) {
            throw new IllegalStateException("status inesperado: " + status);
        }

        BigDecimal esperadoTaxa = new BigDecimal("150.00");
        BigDecimal esperadoLiquido = new BigDecimal("850.00");
        if (taxaAplicada.compareTo(esperadoTaxa) != 0) {
            throw new IllegalStateException("taxaAplicada inesperada: " + taxaAplicada);
        }
        if (valorLiquido.compareTo(esperadoLiquido) != 0) {
            throw new IllegalStateException("valorLiquido inesperado: " + valorLiquido);
        }
        report.addCheck("calculo", "ok");
    }

    private static void validarMetrics(HttpClient client, String baseUrl, SmokeReport report) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/metrics"))
            .GET()
            .timeout(Duration.ofSeconds(3))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertStatus(response.statusCode(), 200, "metrics status", report);

        Long total = JsonUtils.readOptionalLong(response.body(), "totalRequests");
        if (total == null || total < 0) {
            throw new IllegalStateException("metrics totalRequests invalido");
        }
        report.addCheck("metrics", "ok");
    }

    private static void assertStatus(int actual, int expected, String label, SmokeReport report) {
        if (actual != expected) {
            report.addCheck(label, "status " + actual);
            throw new IllegalStateException(label + " esperado " + expected + " mas veio " + actual);
        }
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

    public static class SmokeReport {
        private final String baseUrl;
        private final boolean secureMode;
        private boolean passed;
        private long durationMs;
        private String error;
        private final StringBuilder checks = new StringBuilder();

        SmokeReport(String baseUrl, boolean secureMode) {
            this.baseUrl = baseUrl;
            this.secureMode = secureMode;
        }

        void addCheck(String name, String status) {
            if (checks.length() > 0) {
                checks.append(";");
            }
            checks.append(name).append(":").append(status);
        }

        void setPassed(boolean passed) {
            this.passed = passed;
        }

        void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        void setError(String error) {
            this.error = error;
        }

        boolean passed() {
            return passed;
        }

        void print() {
            System.out.println(toJson());
        }

        void writeIfConfigured() throws Exception {
            String path = System.getenv("JAVATITAN_SMOKE_REPORT_PATH");
            if (path == null || path.isBlank()) {
                return;
            }
            java.nio.file.Path reportPath = java.nio.file.Path.of(path);
            java.nio.file.Path parent = reportPath.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            java.nio.file.Files.writeString(reportPath, toJson());
        }

        void writeCsvIfConfigured() throws Exception {
            String path = System.getenv("JAVATITAN_SMOKE_REPORT_CSV");
            if (path == null || path.isBlank()) {
                return;
            }
            java.nio.file.Path reportPath = java.nio.file.Path.of(path);
            java.nio.file.Path parent = reportPath.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            String csv = toCsvHeader() + System.lineSeparator() + toCsvRow();
            java.nio.file.Files.writeString(reportPath, csv);
        }

        String toJson() {
            String timestamp = Instant.now().toString();
            return "{" +
                "\"tcc\":true," +
                "\"passed\":" + passed + "," +
                "\"secureMode\":" + secureMode + "," +
                "\"baseUrl\":\"" + JsonUtils.escapeJson(baseUrl) + "\"," +
                "\"durationMs\":" + durationMs + "," +
                "\"checks\":\"" + JsonUtils.escapeJson(checks.toString()) + "\"," +
                "\"error\":\"" + JsonUtils.escapeJson(error == null ? "" : error) + "\"," +
                "\"timestamp\":\"" + timestamp + "\"" +
                "}";
        }

        String toCsvHeader() {
            return "timestamp,passed,secureMode,baseUrl,durationMs,checks,error";
        }

        String toCsvRow() {
            String timestamp = Instant.now().toString();
            return String.join(",",
                csv(timestamp),
                csv(String.valueOf(passed)),
                csv(String.valueOf(secureMode)),
                csv(baseUrl),
                csv(String.valueOf(durationMs)),
                csv(checks.toString()),
                csv(error == null ? "" : error)
            );
        }

        String toTxtSummary() {
            String timestamp = Instant.now().toString();
            StringBuilder sb = new StringBuilder();
            sb.append("TCC Smoke Test Report").append(System.lineSeparator());
            sb.append("Timestamp: ").append(timestamp).append(System.lineSeparator());
            sb.append("Passed: ").append(passed).append(System.lineSeparator());
            sb.append("Secure Mode: ").append(secureMode).append(System.lineSeparator());
            sb.append("Base URL: ").append(baseUrl).append(System.lineSeparator());
            sb.append("Duration (ms): ").append(durationMs).append(System.lineSeparator());
            sb.append("Checks: ").append(checks.length() == 0 ? "-" : checks).append(System.lineSeparator());
            sb.append("Error: ").append(error == null || error.isBlank() ? "-" : error).append(System.lineSeparator());
            return sb.toString();
        }

        private String csv(String value) {
            String v = value == null ? "" : value;
            String escaped = v.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }
}
