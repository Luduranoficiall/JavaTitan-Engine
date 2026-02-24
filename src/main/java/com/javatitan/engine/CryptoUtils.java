package com.javatitan.engine;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private static final int IV_SIZE = 12;
    private static final int TAG_BITS = 128;

    private CryptoUtils() {}

    public static EncryptedPayload encrypt(String plaintext, byte[] key) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Payload vazio");
        }
        try {
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPayload(base64(iv), base64(encrypted));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao criptografar payload: " + ex.getMessage(), ex);
        }
    }

    public static String decrypt(EncryptedPayload payload, byte[] key) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload criptografado vazio");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(payload.iv());
            byte[] data = Base64.getDecoder().decode(payload.data());

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));

            byte[] decrypted = cipher.doFinal(data);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao descriptografar payload: " + ex.getMessage(), ex);
        }
    }

    public static EncryptedPayload readPayload(String json) {
        String iv = JsonUtils.readRequiredString(json, "iv");
        String data = JsonUtils.readRequiredString(json, "data");
        return new EncryptedPayload(iv, data);
    }

    public static String writePayload(EncryptedPayload payload) {
        return "{\"iv\":\"" + JsonUtils.escapeJson(payload.iv()) + "\"," +
            "\"data\":\"" + JsonUtils.escapeJson(payload.data()) + "\"}";
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public record EncryptedPayload(String iv, String data) {}
}
