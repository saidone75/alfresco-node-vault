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
import org.saidone.component.BaseComponent;
import org.springframework.beans.factory.annotation.Value;
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
public class JcaCryptoServiceImpl extends BaseComponent implements CryptoService {

    private final SecretKeySpec secretKey;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private static final String KEY_DIGEST_ALGORITHM = "AES";
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    public JcaCryptoServiceImpl(@Value("${application.service.vault.encryption.key}") String key) {
        try {
            val sha = MessageDigest.getInstance(KEY_DIGEST_ALGORITHM);
            byte[] keyBytes = Arrays.copyOf(sha.digest(key.getBytes(StandardCharsets.UTF_8)), 32);
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to initialize CryptoService", e);
        }
    }

    @Override
    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random IV for GCM mode
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
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

    @Override
    public InputStream decrypt(InputStream inputStream) {
        try {
            // Read IV from the beginning of the stream
            byte[] iv = inputStream.readNBytes(IV_LENGTH);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }

}