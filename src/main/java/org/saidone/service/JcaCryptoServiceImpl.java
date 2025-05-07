/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.service;

import lombok.val;
import org.apache.commons.lang3.ArrayUtils;
import org.saidone.component.BaseComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@Service
@ConditionalOnProperty(name = "application.service.vault.encryption.enabled", havingValue = "true")
public class JcaCryptoServiceImpl extends BaseComponent implements CryptoService {

    @Value("${application.service.vault.encryption.key}")
    private String key;

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private static final String KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private SecretKeySpec getSecretKey(byte[] salt) {
        try {
            val spec = new PBEKeySpec(key.toCharArray(), salt, 100000, 256);
            val skf = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return new SecretKeySpec(skf.generateSecret(spec).getEncoded(), ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        }
    }

    @Override
    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random salt for PBKDF2
            byte[] salt = new byte[SALT_LENGTH];
            new SecureRandom().nextBytes(salt);

            // Generate random IV for GCM mode
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = getSecretKey(salt);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Prepend salt and IV to the input stream
            return new SequenceInputStream(
                    new ByteArrayInputStream(ArrayUtils.addAll(salt, iv)),
                    new CipherInputStream(inputStream, cipher)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption", e);
        }
    }

    @Override
    public InputStream decrypt(InputStream inputStream) {
        try {
            // Read salt from the the stream
            byte[] salt = inputStream.readNBytes(SALT_LENGTH);

            // Read IV from the the stream
            byte[] iv = inputStream.readNBytes(IV_LENGTH);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = getSecretKey(salt);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }

    @Override
    public String encryptText(String text) {
        try {
            byte[] encryptedBytes = encrypt(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).readAllBytes();
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error during text encryption", e);
        }
    }

    @Override
    public String decryptText(String encryptedText) {
        try {
            byte[] decryptedBytes = decrypt(new ByteArrayInputStream(Base64.getDecoder().decode(encryptedText))).readAllBytes();
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error during text decryption", e);
        }
    }

}