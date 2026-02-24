package com.javatitan.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestClient {
    public static void main(String[] args) throws Exception {
        String baseUrl = envOrDefault("JAVATITAN_BASE_URL", "http://localhost:8080");
        String token = System.getenv("JAVATITAN_JWT_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("JAVATITAN_JWT_TOKEN nao definido");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

        callHealth(client, baseUrl);
        callCalcular(client, baseUrl, token.trim());
    }

    private static void callHealth(HttpClient client, String baseUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/health"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("---- /health ----");
        System.out.println("HTTP " + response.statusCode());
        System.out.println(response.body());
    }

    private static void callCalcular(HttpClient client, String baseUrl, String token) throws Exception {
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
        System.out.println("---- /api/calcular ----");
        System.out.println("HTTP " + response.statusCode());
        System.out.println(response.body());
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
