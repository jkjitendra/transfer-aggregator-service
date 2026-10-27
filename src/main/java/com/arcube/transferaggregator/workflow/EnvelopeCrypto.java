package com.arcube.transferaggregator.workflow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts sensitive workflow payloads before they reach PostgreSQL or backups. */
@Component
@Profile("!demo & !test")
public class EnvelopeCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public EnvelopeCrypto(@Value("${workflow.encryption-key:}") String encodedKey) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("WORKFLOW_ENCRYPTION_KEY must be a base64-encoded 32-byte key", exception);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("WORKFLOW_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + nonce.length + ciphertext.length)
                .put((byte) 1).put(nonce).put(ciphertext).array();
        } catch (Exception exception) {
            throw new IllegalStateException("Workflow payload encryption failed", exception);
        }
    }

    public String decrypt(byte[] envelope) {
        try {
            if (envelope == null || envelope.length < 29 || envelope[0] != 1) {
                throw new IllegalArgumentException("Invalid encrypted workflow payload");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, envelope, 1, 12));
            return new String(cipher.doFinal(envelope, 13, envelope.length - 13), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Workflow payload decryption failed", exception);
        }
    }
}
