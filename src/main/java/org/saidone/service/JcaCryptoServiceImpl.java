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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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


/**
 * Implementation of the CryptoService interface using the Java Cryptography Architecture (JCA).
 * <p>
 * This service provides encryption and decryption capabilities using AES-GCM with PBKDF2 key derivation.
 * It supports both stream-based and text-based encryption operations with the following security features:
 * AES encryption in GCM (Galois/Counter Mode) for authenticated encryption,
 * PBKDF2 with HMAC-SHA256 for secure key derivation from passwords,
 * Secure random generation of cryptographic salt and initialization vectors,
 * Base64 encoding for text-based encryption results.
 * <p>
 * This implementation is conditionally enabled based on the following application properties:
 * application.service.vault.encryption.enabled = true
 * application.service.vault.encryption.impl = 'jca'
 *
 * @see CryptoService
 * @see BaseComponent
 */
@Service
@ConditionalOnExpression("${application.service.vault.encryption.enabled:true} == true && '${application.service.vault.encryption.impl:}' == 'jca'")
public class JcaCryptoServiceImpl extends BaseComponent implements CryptoService {

    @Value("${application.service.vault.encryption.jca.key}")
    private String key;

    @Value("${application.service.vault.encryption.jca.salt-length:16}")
    private int SALT_LENGTH;
    @Value("${application.service.vault.encryption.jca.iv-length:12}")
    private int IV_LENGTH;
    @Value("${application.service.vault.encryption.jca.iterations:100000}")
    private int ITERATIONS;
    private int TAG_LENGTH = 128;

    private static final String KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Derives a secret AES key from the configured password and the given salt using PBKDF2 with HmacSHA256.
     * <p>
     * This method uses 100,000 iterations and generates a 256-bit key suitable for AES encryption.
     *
     * @param salt the salt bytes used in key derivation to ensure uniqueness and strengthen security
     * @return a SecretKeySpec representing the derived AES secret key
     * @throws IllegalStateException if the key derivation algorithm is not available or the key specification is invalid
     */
    private SecretKeySpec deriveSecretKey(byte[] salt) {
        try {
            val spec = new PBEKeySpec(key.toCharArray(), salt, ITERATIONS, 256);
            val skf = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return new SecretKeySpec(skf.generateSecret(spec).getEncoded(), ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        }
    }

    /**
     * Encrypts the provided input stream using AES encryption in GCM mode with no padding.
     * <p>
     * This method generates a random salt and initialization vector (IV) for each encryption operation.
     * The salt is used to derive a secret AES key from a configured password using PBKDF2 with HmacSHA256,
     * ensuring cryptographic strength and uniqueness of the key.
     * <p>
     * The IV is used to initialize the cipher for AES-GCM encryption.
     * The method returns an InputStream that prepends the concatenated salt and IV bytes before the encrypted data stream,
     * allowing the decryption process to retrieve these parameters.
     *
     * @param inputStream the input stream containing plaintext data to encrypt
     * @return an InputStream emitting the salt, IV, and encrypted data in sequence
     * @throws RuntimeException if any error occurs during the encryption process
     */
    @Override
    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random salt for PBKDF2
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);

            // Generate random IV for GCM mode
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = deriveSecretKey(salt);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Concatenate salt and IV
            byte[] saltAndIv = new byte[SALT_LENGTH + IV_LENGTH];
            System.arraycopy(salt, 0, saltAndIv, 0, SALT_LENGTH);
            System.arraycopy(iv, 0, saltAndIv, SALT_LENGTH, IV_LENGTH);

            // Prepend salt and IV to the input stream
            return new SequenceInputStream(
                    new ByteArrayInputStream(saltAndIv),
                    new CipherInputStream(inputStream, cipher)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption", e);
        }
    }

    /**
     * Decrypts the provided input stream using AES-GCM with a key derived from a salt.
     * <p>
     * The method expects the input stream to begin with a salt followed by an initialization vector (IV).
     * It reads these values, derives the secret key from the salt, initializes the cipher with the IV and key,
     * and then returns a stream that decrypts the remaining input on-the-fly.
     *
     * @param inputStream the input stream containing the encrypted data preceded by salt and IV
     * @return an InputStream that yields the decrypted data when read
     * @throws RuntimeException if any error occurs during the decryption process
     */
    @Override
    public InputStream decrypt(InputStream inputStream) {
        try {
            // Read salt from the the stream
            byte[] salt = inputStream.readNBytes(SALT_LENGTH);

            // Read IV from the the stream
            byte[] iv = inputStream.readNBytes(IV_LENGTH);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = deriveSecretKey(salt);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }

    /**
     * Encrypts the given text by converting it to UTF-8 bytes, encrypting those bytes,
     * and then encoding the result into a Base64 string.
     *
     * @param text the plaintext string to encrypt
     * @return a Base64-encoded string representing the encrypted text
     * @throws RuntimeException if any error occurs during the encryption process
     */
    @Override
    public String encryptText(String text) {
        try (val is = encrypt(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))) {
            return Base64.getEncoder().encodeToString(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error during text encryption", e);
        }
    }

    /**
     * Decrypts a Base64-encoded, AES-encrypted text string.
     * <p>
     * This method performs the following steps:
     * - Decodes the input Base64 string into bytes.
     * - Decrypts the bytes using the configured AES decryption process.
     * - Converts the decrypted bytes into a UTF-8 encoded string.
     * <p>
     * If any error occurs during decoding or decryption, a RuntimeException is thrown.
     *
     * @param encryptedText the Base64-encoded encrypted string to decrypt
     * @return the original decrypted plaintext string
     * @throws RuntimeException if an error occurs during decryption
     */
    @Override
    public String decryptText(String encryptedText) {
        try (val is = decrypt(new ByteArrayInputStream(Base64.getDecoder().decode(encryptedText)))) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error during text decryption", e);
        }
    }

}