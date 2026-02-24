package com.javatitan.engine;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class HttpRequestReader {
    private HttpRequestReader() {}

    public static String readBodyLimited(HttpExchange exchange, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            return readAll(exchange.getRequestBody());
        }
        String lengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (lengthHeader != null && !lengthHeader.isBlank()) {
            try {
                long length = Long.parseLong(lengthHeader.trim());
                if (length > maxBytes) {
                    throw new RequestValidationException(413, "Payload maior que o limite permitido");
                }
            } catch (NumberFormatException ex) {
                throw new RequestValidationException(400, "Content-Length invalido");
            }
        }

        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = exchange.getRequestBody()) {
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new RequestValidationException(413, "Payload maior que o limite permitido");
                }
                baos.write(buffer, 0, read);
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
