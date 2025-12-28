/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.service.crypto;

import jakarta.validation.constraints.Min;
import lombok.Setter;
import lombok.val;
import org.saidone.config.EncryptionConfig;
import org.saidone.service.SecretService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * {@link CryptoService} implementation based on the JCA provider. It encrypts
 * and decrypts data using AES in GCM mode. The bean is active when both
 * {@code application.service.vault.encryption.enabled} is set to {@code true}
 * and {@code application.service.vault.encryption.impl} is set to {@code jca}.
 * Random salt and IV values are produced for every operation and the secret key
 * is derived using the configured KDF implementation.
 */
@Service
@Setter
@ConfigurationProperties(prefix = "application.service.vault.encryption.jca")
@ConditionalOnExpression(
        "${application.service.vault.encryption.enabled}.equals(true) and '${application.service.vault.encryption.impl}'.equals('jca')"
)
public class JcaCryptoServiceImpl extends AbstractCryptoService implements CryptoService {

    @Autowired
    private SecretService secretService;

    @Min(16)
    private int saltLength;
    @Min(12)
    private int ivLength;
    private static final int TAG_LENGTH = 128;
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Injects encryption configuration properties.
     *
     * @param properties resolved encryption configuration
     */
    @Autowired
    public void configure(EncryptionConfig properties) {
        this.kdf = properties.getKdf();
        this.saltLength = properties.getJca().getSaltLength();
        this.ivLength = properties.getJca().getIvLength();
    }

    /**
     * Encrypts a data stream using AES-GCM authenticated encryption.
     * <p>
     * The encryption process follows these steps:
     * 1. Generates random salt and IV
     * 2. Derives encryption key from salt using configured KDF
     * 3. Initializes AES-GCM cipher
     * 4. Prepends key version+salt+IV to encrypted stream
     * <p>
     * The output stream format is: [key version][salt][IV][encrypted data]
     *
     * @param inputStream The plaintext input data to be encrypted
     * @param secret      secret material used to derive the encryption key
     * @return An InputStream containing concatenated salt, IV and encrypted data
     * @throws RuntimeException if any error occurs during the encryption process
     */
    @Override
    public InputStream encrypt(InputStream inputStream, Secret secret) {
        try {
            // Generate random salt for PBKDF2
            byte[] salt = new byte[saltLength];
            SECURE_RANDOM.nextBytes(salt);

            // Generate random IV for GCM mode
            byte[] iv = new byte[ivLength];
            SECURE_RANDOM.nextBytes(iv);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = deriveSecretKey(secret, KEY_ALGORITHM, salt);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey.getLeft(), spec);

            // Concatenate key version, salt and IV
            byte[] keyVersionSaltAndIv = new byte[4 + saltLength + ivLength];
            System.arraycopy(ByteBuffer.allocate(4).putInt(secretKey.getRight()).array(), 0, keyVersionSaltAndIv, 0, 4);
            System.arraycopy(salt, 0, keyVersionSaltAndIv, 4, saltLength);
            System.arraycopy(iv, 0, keyVersionSaltAndIv, 4 + saltLength, ivLength);

            // Prepend key version, salt and IV to the input stream
            return new SequenceInputStream(
                    new ByteArrayInputStream(keyVersionSaltAndIv),
                    new CipherInputStream(inputStream, cipher)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption", e);
        }
    }

    /**
     * Decrypts an AES-GCM encrypted stream.
     * <p>
     * The decryption process follows these steps:
     * 1. Reads key version, salt and IV from stream header
     * 2. Derives decryption key from salt
     * 3. Initializes cipher for decryption
     * 4. Returns decrypting stream for remaining data
     * <p>
     * Expected input format: [key version][salt][IV][encrypted data]
     * where:
     * - key version length = 4 bytes
     * - salt length = saltLength bytes
     * - IV length = ivLength bytes
     *
     * @param inputStream InputStream containing encrypted data with prepended salt and IV
     * @return An InputStream yielding the decrypted data
     * @throws RuntimeException if any error occurs during the decryption process
     */
    @Override
    public InputStream decrypt(InputStream inputStream) {
        try {
            // Read key version from stream
            val keyVersion = ByteBuffer.wrap(inputStream.readNBytes(4)).getInt();

            val secret = secretService.getSecret(keyVersion);

            // Read salt from stream
            byte[] salt = inputStream.readNBytes(saltLength);

            // Read IV from stream
            byte[] iv = inputStream.readNBytes(ivLength);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(secret, KEY_ALGORITHM, salt);

            // Initialize AES-GCM cipher for decryption
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey.getLeft(), spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }

}