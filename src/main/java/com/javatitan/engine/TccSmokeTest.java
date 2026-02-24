package com.javatitan.engine;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class TccSmokeTest {
    public static void main(String[] args) throws Exception {
        String baseUrl = envOrDefault("JAVATITAN_BASE_URL", "http://localhost:8080");
        String token = System.getenv("JAVATITAN_JWT_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("JAVATITAN_JWT_TOKEN nao definido");
            System.exit(1);
        }

        run(baseUrl, token.trim());
        System.out.println("[SMOKE] OK");
    }

    public static void run(String baseUrl, String token) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

        validarHealth(client, baseUrl);
        validarCalculo(client, baseUrl, token);
    }

    private static void validarHealth(HttpClient client, String baseUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/health"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertStatus(response.statusCode(), 200, "health status");

        String status = JsonUtils.readString(response.body(), "status");
        if (!"UP".equalsIgnoreCase(status)) {
            throw new IllegalStateException("health status invalido: " + status);
        }
    }

    private static void validarCalculo(HttpClient client, String baseUrl, String token) throws Exception {
        String payload = "{\"idCliente\":\"e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1\"," +
            "\"valorBruto\":1000.00,\"plano\":\"PRO\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/calcular"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertStatus(response.statusCode(), 200, "calcular status");

        String body = response.body();
        String idProposta = JsonUtils.readRequiredString(body, "idProposta");
        UUID.fromString(idProposta);

        BigDecimal valorLiquido = JsonUtils.readRequiredBigDecimal(body, "valorLiquido");
        BigDecimal taxaAplicada = JsonUtils.readRequiredBigDecimal(body, "taxaAplicada");
        String status = JsonUtils.readRequiredString(body, "status");

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
    }

    private static void assertStatus(int actual, int expected, String label) {
        if (actual != expected) {
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
}
