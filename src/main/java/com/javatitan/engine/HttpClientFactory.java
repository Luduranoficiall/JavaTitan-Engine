package com.javatitan.engine;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {
    private HttpClientFactory() {}

    public static HttpClient create(ClientTlsConfig tlsConfig) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3));

        if (tlsConfig != null && tlsConfig.enabled()) {
            SSLContext context = tlsConfig.createClientContext();
            builder.sslContext(context);
        }

        return builder.build();
    }
}
