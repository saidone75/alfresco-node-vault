/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.saidone.config.EncryptionConfig;
import org.saidone.service.SecretService;
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
 * Cryptographic service based on the Bouncy Castle provider that performs
 * ChaCha20-Poly1305 encryption. The bean is activated when both
 * {@code application.service.vault.encryption.enabled} is set to {@code true}
 * and {@code application.service.vault.encryption.impl} is set to {@code bc}.
 * Random salt and nonce values are generated for every operation and the
 * encryption key is derived using the configured KDF implementation.
 */
@Service
@Setter
@ConfigurationProperties(prefix = "application.service.vault.encryption.bc")
@ConditionalOnExpression(
        "${application.service.vault.encryption.enabled}.equals(true) and '${application.service.vault.encryption.impl}'.equals('bc')"
)
public class BcCryptoServiceImpl extends AbstractCryptoService implements CryptoService {

    @Autowired
    private SecretService secretService;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Min(16)
    private int saltLength;
    @Min(12)
    private int nonceLength;
    // ChaCha20-Poly1305 is a stream cipher with AEAD; no padding is required or used.
    private static final String CIPHER_TRANSFORMATION = "ChaCha20-Poly1305";
    private static final String KEY_ALGORITHM = "ChaCha20";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Injects encryption configuration properties.
     *
     * @param properties resolved encryption configuration
     */
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
     * 4. Prepends key version+salt+nonce to encrypted stream
     * <p>
     * The output stream format is: [key version][salt][nonce][encrypted data]
     *
     * @param inputStream The plaintext input data to be encrypted
     * @param secret      secret material used to derive the encryption key
     * @return An InputStream containing concatenated salt, nonce and encrypted data
     * @throws RuntimeException if any error occurs during the encryption process
     */
    @Override
    public InputStream encrypt(InputStream inputStream, Secret secret) {
        try {
            // Generate random salt and nonce
            byte[] salt = new byte[saltLength];
            SECURE_RANDOM.nextBytes(salt);

            byte[] nonce = new byte[nonceLength];
            SECURE_RANDOM.nextBytes(nonce);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(secret, KEY_ALGORITHM, salt);

            // Initialize ChaCha20-Poly1305 cipher
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            val spec = new IvParameterSpec(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey.getLeft(), spec);

            // Concatenate key version, salt and nonce
            byte[] keyVersionSaltAndNonce = new byte[4 + saltLength + nonceLength];
            System.arraycopy(ByteBuffer.allocate(4).putInt(secretKey.getRight()).array(), 0, keyVersionSaltAndNonce, 0, 4);
            System.arraycopy(salt, 0, keyVersionSaltAndNonce, 4, saltLength);
            System.arraycopy(nonce, 0, keyVersionSaltAndNonce, 4 + saltLength, nonceLength);

            // Prepend key version, salt and nonce to encrypted stream
            return new SequenceInputStream(
                    new ByteArrayInputStream(keyVersionSaltAndNonce),
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
     * 1. Reads key version, salt and nonce from stream header
     * 2. Derives decryption key from salt
     * 3. Initializes cipher for decryption
     * 4. Returns decrypting stream for remaining data
     * <p>
     * Expected input format: [key version][salt][nonce][encrypted data]
     * where:
     * - key version length = 4 bytes
     * - salt length = saltLength bytes
     * - nonce length = nonceLength bytes
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

            val secret = secretService.getSecret(keyVersion);

            // Read salt from stream
            byte[] salt = inputStream.readNBytes(saltLength);

            // Read nonce from stream
            byte[] nonce = inputStream.readNBytes(nonceLength);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(secret, KEY_ALGORITHM, salt);

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