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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.saidone.config.EncryptionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.Security;

/**
 * Implements symmetric encryption/decryption using ChaCha20-Poly1305 via the Bouncy Castle provider.
 * <p>
 * Key features:
 * - ChaCha20 encryption with Poly1305 authentication
 * - Configurable salt and nonce lengths
 * - Support for PBKDF2, HKDF and Argon2 key derivation
 * - Stream-based operation for efficient memory usage
 * - Base64 text encoding/decoding support
 * <p>
 * Configuration:
 * - Prefix: application.service.vault.encryption.bc
 * - Enabled when encryption.enabled=true and encryption.impl=bc
 * <p>
 * Security measures:
 * - Per-operation random salt and nonce generation
 * - Password-based key derivation with strong KDFs
 * - Authenticated encryption with Poly1305 mode
 * - Efficient processing of large data streams
 */
@Service
@Setter
@ConfigurationProperties(prefix = "application.service.vault.encryption.bc")
@ConditionalOnExpression("${application.service.vault.encryption.enabled:true} == true && '${application.service.vault.encryption.impl:}' == 'bc'")
public class BcCryptoServiceImpl extends AbstractCryptoService implements CryptoService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Min(16)
    private int saltLength;
    @Min(12)
    private int nonceLength;
    private static final String CIPHER_TRANSFORMATION = "ChaCha20-Poly1305";
    private static final String KEY_ALGORITHM = "ChaCha20";

    private static final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public void configure(EncryptionConfig properties) {
        this.kdf = properties.getKdf();
        this.saltLength = properties.getBc().getSaltLength();
        this.nonceLength = properties.getBc().getNonceLength();
    }

    /**
     * Encrypts a data stream using ChaCha20-Poly1305 authenticated encryption.
     * <p>
     * The encryption process follows these steps:
     * 1. Generates random salt and nonce
     * 2. Derives encryption key from salt using configured KDF
     * 3. Initializes ChaCha20-Poly1305 cipher
     * 4. Prepends salt+nonce to encrypted stream
     * <p>
     * The output stream format is: [salt][nonce][encrypted data]
     *
     * @param inputStream The plaintext input data to be encrypted
     * @return An InputStream containing concatenated salt, nonce and encrypted data
     * @throws RuntimeException if any error occurs during the encryption process
     */
    @Override
    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random salt and nonce
            byte[] salt = new byte[saltLength];
            secureRandom.nextBytes(salt);

            byte[] nonce = new byte[nonceLength];
            secureRandom.nextBytes(nonce);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(KEY_ALGORITHM, salt);

            // Initialize ChaCha20-Poly1305 cipher
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            val spec = new IvParameterSpec(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey.getLeft(), spec);

            // Concatenate salt and nonce
            byte[] saltAndNonce = new byte[saltLength + nonceLength];
            System.arraycopy(salt, 0, saltAndNonce, 0, saltLength);
            System.arraycopy(nonce, 0, saltAndNonce, saltLength, nonceLength);

            // Prepend salt and nonce to encrypted stream  
            return new SequenceInputStream(
                    new ByteArrayInputStream(saltAndNonce),
                    new CipherInputStream(inputStream, cipher)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption", e);
        }
    }

    /**
     * Decrypts a ChaCha20-Poly1305 encrypted stream.
     * <p>
     * The decryption process follows these steps:
     * 1. Reads salt and nonce from stream header
     * 2. Derives decryption key from salt
     * 3. Initializes cipher for decryption
     * 4. Returns decrypting stream for remaining data
     * <p>
     * Expected input format: [salt][nonce][encrypted data]
     * where:
     * - salt length = saltLength
     * - nonce length = nonceLength
     *
     * @param inputStream InputStream containing encrypted data with prepended salt and nonce
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

            // Read nonce from stream
            byte[] nonce = inputStream.readNBytes(nonceLength);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(KEY_ALGORITHM, salt, keyVersion);

            // Initialize ChaCha20-Poly1305 cipher for decryption
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            val spec = new IvParameterSpec(nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey.getLeft(), spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }

}