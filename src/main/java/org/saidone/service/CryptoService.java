package org.saidone.service;

import org.saidone.component.BaseComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
@ConditionalOnProperty(name = "application.service.vault.encryption.enabled", havingValue = "true")
public class CryptoService extends BaseComponent {

    private final SecretKeySpec secretKey;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    public CryptoService(@Value("${application.service.vault.encryption.key}") String key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = Arrays.copyOf(sha.digest(key.getBytes(StandardCharsets.UTF_8)), 32);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to initialize CryptoService", e);
        }
    }

    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random IV for GCM mode
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Prepend IV to the input stream
            return new SequenceInputStream(
                    new ByteArrayInputStream(iv),
                    new CipherInputStream(inputStream, cipher)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption", e);
        }
    }

    public InputStream decrypt(InputStream inputStream) {
        try {
            // Read IV from the beginning of the stream
            byte[] iv = inputStream.readNBytes(IV_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }
}