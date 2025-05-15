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

import jakarta.validation.constraints.Min;
import lombok.Setter;
import lombok.val;
import org.saidone.config.EncryptionConfig;
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
 * Implements symmetric encryption/decryption using AES-GCM mode via Java Cryptography Architecture (JCA).
 * <p>
 * Key features:
 * - AES encryption in GCM authenticated mode
 * - Configurable salt and IV lengths
 * - Support for PBKDF2, HKDF and Argon2 key derivation
 * - Stream-based operation for efficient memory usage
 * - Base64 text encoding/decoding support
 * <p>
 * Configuration:
 * - Prefix: application.service.vault.encryption.jca
 * - Enabled when encryption.enabled=true and encryption.impl=jca
 * <p>
 * Security measures:
 * - Per-operation random salt and IV generation
 * - Password-based key derivation with strong KDFs
 * - Authenticated encryption with GCM mode
 * - No padding for precise size control
 */
@Service
@Setter
@ConfigurationProperties(prefix = "application.service.vault.encryption.jca")
@ConditionalOnExpression("${application.service.vault.encryption.enabled:true} == true && '${application.service.vault.encryption.impl:}' == 'jca'")
public class JcaCryptoServiceImpl extends AbstractCryptoService implements CryptoService {

    @Min(16)
    private int saltLength;
    @Min(12)
    private int ivLength;
    private static final int TAG_LENGTH = 128;
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom secureRandom = new SecureRandom();

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
     * The output stream format is: [salt][IV][encrypted data]
     *
     * @param inputStream The plaintext input data to be encrypted
     * @return An InputStream containing concatenated salt, IV and encrypted data
     * @throws RuntimeException if any error occurs during the encryption process
     */
    @Override
    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random salt for PBKDF2
            byte[] salt = new byte[saltLength];
            secureRandom.nextBytes(salt);

            // Generate random IV for GCM mode
            byte[] iv = new byte[ivLength];
            secureRandom.nextBytes(iv);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = deriveSecretKey(KEY_ALGORITHM, salt);
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

            // Read salt from stream
            byte[] salt = inputStream.readNBytes(saltLength);

            // Read IV from stream
            byte[] iv = inputStream.readNBytes(ivLength);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(KEY_ALGORITHM, salt, keyVersion);

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