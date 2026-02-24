package com.javatitan.engine;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public record ClientTlsConfig(
    boolean enabled,
    String keyStorePath,
    String keyStorePassword,
    String keyStoreType,
    String keyPassword,
    String trustStorePath,
    String trustStorePassword,
    String trustStoreType
) {
    public static ClientTlsConfig fromEnv(TlsConfig serverTls) {
        if (serverTls == null || !serverTls.enabled()) {
            return new ClientTlsConfig(false, null, null, null, null, null, null, null);
        }

        String keyStorePath = env("JAVATITAN_CLIENT_KEYSTORE_PATH");
        String keyStorePassword = env("JAVATITAN_CLIENT_KEYSTORE_PASSWORD");
        String keyStoreType = envOrDefault("JAVATITAN_CLIENT_KEYSTORE_TYPE", "JKS");
        String keyPassword = env("JAVATITAN_CLIENT_KEY_PASSWORD");

        if (keyStorePath == null || keyStorePath.isBlank()) {
            keyStorePath = serverTls.keyStorePath();
            keyStorePassword = serverTls.keyStorePassword();
            keyStoreType = serverTls.keyStoreType();
            keyPassword = serverTls.keyPassword();
        }

        String trustStorePath = env("JAVATITAN_CLIENT_TRUSTSTORE_PATH");
        String trustStorePassword = env("JAVATITAN_CLIENT_TRUSTSTORE_PASSWORD");
        String trustStoreType = envOrDefault("JAVATITAN_CLIENT_TRUSTSTORE_TYPE", "JKS");

        if (trustStorePath == null || trustStorePath.isBlank()) {
            trustStorePath = serverTls.trustStorePath();
            trustStorePassword = serverTls.trustStorePassword();
            trustStoreType = serverTls.trustStoreType();
        }

        return new ClientTlsConfig(true, keyStorePath, keyStorePassword, keyStoreType, keyPassword, trustStorePath, trustStorePassword, trustStoreType);
    }

    public SSLContext createClientContext() {
        if (!enabled) {
            throw new IllegalStateException("TLS cliente nao habilitado");
        }
        try {
            KeyManagerFactory kmf = null;
            if (keyStorePath != null && !keyStorePath.isBlank()) {
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                    keyStore.load(fis, keyStorePassword.toCharArray());
                }
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                char[] keyPass = (keyPassword == null || keyPassword.isBlank())
                    ? keyStorePassword.toCharArray()
                    : keyPassword.toCharArray();
                kmf.init(keyStore, keyPass);
            }

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
            context.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
            return context;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao configurar TLS cliente: " + ex.getMessage(), ex);
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
}
