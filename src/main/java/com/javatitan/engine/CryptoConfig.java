package com.javatitan.engine;

import java.util.Base64;

public record CryptoConfig(boolean secureMode, byte[] aesKey) {
    public static CryptoConfig fromEnv() {
        String keyBase64 = env("JAVATITAN_AES_KEY");
        boolean secureMode = envBool("JAVATITAN_SECURE_MODE", keyBase64 != null);

        if (secureMode && (keyBase64 == null || keyBase64.isBlank())) {
            throw new IllegalStateException("JAVATITAN_AES_KEY obrigatorio quando secure mode esta ativo");
        }

        byte[] key = null;
        if (keyBase64 != null && !keyBase64.isBlank()) {
            key = decodeKey(keyBase64.trim());
        }
        return new CryptoConfig(secureMode, key);
    }

    private static byte[] decodeKey(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            int len = decoded.length;
            if (len != 16 && len != 24 && len != 32) {
                throw new IllegalArgumentException("Tamanho de chave AES invalido: " + len);
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JAVATITAN_AES_KEY invalida: " + ex.getMessage(), ex);
        }
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static boolean envBool(String name, boolean defaultValue) {
        String value = env(name);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }
}
