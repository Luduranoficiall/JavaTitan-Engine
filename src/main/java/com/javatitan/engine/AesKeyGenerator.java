package com.javatitan.engine;

import java.security.SecureRandom;
import java.util.Base64;

public class AesKeyGenerator {
    public static void main(String[] args) {
        int size = envInt("JAVATITAN_AES_KEY_SIZE", 32);
        if (size != 16 && size != 24 && size != 32) {
            throw new IllegalArgumentException("Tamanho invalido: use 16, 24 ou 32");
        }
        byte[] key = new byte[size];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        System.out.println(encoded);
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }
}
