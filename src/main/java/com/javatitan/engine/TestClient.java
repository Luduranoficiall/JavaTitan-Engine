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

        CryptoConfig cryptoConfig = CryptoConfig.fromEnv();
        ClientTlsConfig tlsConfig = ClientTlsConfig.fromEnv(TlsConfig.fromEnv());

        run(baseUrl, token.trim(), cryptoConfig, tlsConfig);
    }

    public static void run(String baseUrl, String token, CryptoConfig cryptoConfig, ClientTlsConfig tlsConfig) throws Exception {
        HttpClient client = HttpClientFactory.create(tlsConfig);

        callHealth(client, baseUrl);
        callCalcular(client, baseUrl, token, cryptoConfig);
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

    private static void callCalcular(HttpClient client, String baseUrl, String token, CryptoConfig cryptoConfig) throws Exception {
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
        System.out.println("---- " + path + " ----");
        System.out.println("HTTP " + response.statusCode());

        if (cryptoConfig != null && cryptoConfig.secureMode() && response.statusCode() == 200) {
            CryptoUtils.EncryptedPayload encrypted = CryptoUtils.readPayload(response.body());
            String decrypted = CryptoUtils.decrypt(encrypted, cryptoConfig.aesKey());
            System.out.println(decrypted);
        } else {
            System.out.println(response.body());
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
