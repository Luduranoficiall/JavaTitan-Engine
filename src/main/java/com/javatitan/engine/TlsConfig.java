package com.javatitan.engine;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public record TlsConfig(
    boolean enabled,
    String keyStorePath,
    String keyStorePassword,
    String keyStoreType,
    String keyPassword,
    String trustStorePath,
    String trustStorePassword,
    String trustStoreType,
    boolean requireClientAuth
) {
    public static TlsConfig fromEnv() {
        String keyStorePath = env("JAVATITAN_TLS_KEYSTORE_PATH");
        if (keyStorePath == null || keyStorePath.isBlank()) {
            return new TlsConfig(false, null, null, null, null, null, null, null, false);
        }
        String keyStorePassword = required("JAVATITAN_TLS_KEYSTORE_PASSWORD");
        String keyStoreType = envOrDefault("JAVATITAN_TLS_KEYSTORE_TYPE", "JKS");
        String keyPassword = env("JAVATITAN_TLS_KEY_PASSWORD");
        String trustStorePath = env("JAVATITAN_TLS_TRUSTSTORE_PATH");
        String trustStorePassword = env("JAVATITAN_TLS_TRUSTSTORE_PASSWORD");
        String trustStoreType = envOrDefault("JAVATITAN_TLS_TRUSTSTORE_TYPE", "JKS");
        boolean requireClientAuth = envBool("JAVATITAN_TLS_REQUIRE_CLIENT_AUTH", false);

        if (requireClientAuth && (trustStorePath == null || trustStorePath.isBlank())) {
            throw new IllegalStateException("Truststore obrigatorio quando client auth esta ativo");
        }
        return new TlsConfig(true, keyStorePath, keyStorePassword, keyStoreType, keyPassword, trustStorePath, trustStorePassword, trustStoreType, requireClientAuth);
    }

    public SSLContext createServerContext() {
        if (!enabled) {
            throw new IllegalStateException("TLS nao habilitado");
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                keyStore.load(fis, keyStorePassword.toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            char[] keyPass = (keyPassword == null || keyPassword.isBlank())
                ? keyStorePassword.toCharArray()
                : keyPassword.toCharArray();
            kmf.init(keyStore, keyPass);

            TrustManagerFactory tmf = null;
            if (trustStorePath != null && !trustStorePath.isBlank()) {
                KeyStore trustStore = KeyStore.getInstance(trustStoreType);
                try (FileInputStream fis = new FileInputStream(trustStorePath)) {
                    trustStore.load(fis, trustStorePassword.toCharArray());
                }
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
            return context;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao configurar TLS: " + ex.getMessage(), ex);
        }
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = env(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static boolean envBool(String name, boolean defaultValue) {
        String value = env(name);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }

    private static String required(String name) {
        String value = env(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " obrigatorio");
        }
        return value.trim();
    }
}
