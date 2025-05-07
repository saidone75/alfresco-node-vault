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

/**
 * Implementation of the {@link CryptoService} using Java Cryptography Architecture (JCA) with AES encryption.
 *
 * This service performs encryption and decryption of data streams and text strings using AES in GCM mode
 * with no padding. Encryption keys are derived via PBKDF2 with HmacSHA256 from a configured password and a
 * randomly generated salt.
 *
 * Encryption process:
 * - Generates a random salt and initialization vector (IV) for each encryption operation.
 * - Derives a secret AES key from the password and salt using PBKDF2 with 100,000 iterations.
 * - Encrypts the input stream using AES/GCM/NoPadding cipher initialized with the derived key and IV.
 * - Prepends the salt and IV bytes to the encrypted data stream for use during decryption.
 *
 * Decryption process:
 * - Reads the salt and IV bytes prepended to the encrypted input stream.
 * - Derives the AES key using the same PBKDF2 parameters and the extracted salt.
 * - Decrypts the rest of the input stream using AES/GCM/NoPadding cipher initialized with the derived key and IV.
 *
 * The service supports both streaming encryption/decryption of arbitrary input streams and
 * convenience methods for encrypting and decrypting UTF-8 encoded strings with Base64 encoding.
 *
 * Activation of this service is conditional on the Spring property
 * {@code application.service.vault.encryption.enabled=true}.
 */
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

    /**
     * Derives a secret AES key from the configured password and the given salt using PBKDF2 with HmacSHA256.
     *
     * This method uses 100,000 iterations and generates a 256-bit key suitable for AES encryption.
     *
     * @param salt the salt bytes used in key derivation to ensure uniqueness and strengthen security
     * @return a SecretKeySpec representing the derived AES secret key
     * @throws IllegalStateException if the key derivation algorithm is not available or the key specification is invalid
     */
    private SecretKeySpec getSecretKey(byte[] salt) {
        try {
            val spec = new PBEKeySpec(key.toCharArray(), salt, 100000, 256);
            val skf = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return new SecretKeySpec(skf.generateSecret(spec).getEncoded(), ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        }
    }

    /**
     * Encrypts the provided input stream using AES encryption in GCM mode with no padding.
     *
     * This method generates a random salt and initialization vector (IV) for each encryption operation.
     * The salt is used to derive a secret AES key from a configured password using PBKDF2 with HmacSHA256,
     * ensuring cryptographic strength and uniqueness of the key.
     *
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

    /**
     * Encrypts the provided input stream using AES encryption in GCM mode with no padding.
     *
     * This method generates a random salt and initialization vector (IV) for each encryption operation.
     * The salt is used to derive a secret AES key from a configured password using PBKDF2 with HmacSHA256,
     * ensuring cryptographic strength and uniqueness of the key.
     *
     * The IV is used to initialize the cipher for AES-GCM encryption.
     * The method returns an InputStream that prepends the concatenated salt and IV bytes before the encrypted data stream,
     * allowing the decryption process to retrieve these parameters.
     *
     * @param inputStream the input stream containing plaintext data to encrypt
     * @return an InputStream emitting the salt, IV, and encrypted data in sequence
     * @throws RuntimeException if any error occurs during the encryption process
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
            val secretKey = getSecretKey(salt);
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
        try {
            byte[] encryptedBytes = encrypt(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).readAllBytes();
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error during text encryption", e);
        }
    }

    /**
     * Decrypts a Base64-encoded, AES-encrypted text string.
     *
     * This method performs the following steps:
     * - Decodes the input Base64 string into bytes.
     * - Decrypts the bytes using the configured AES decryption process.
     * - Converts the decrypted bytes into a UTF-8 encoded string.
     *
     * If any error occurs during decoding or decryption, a RuntimeException is thrown.
     *
     * @param encryptedText the Base64-encoded encrypted string to decrypt
     * @return the original decrypted plaintext string
     * @throws RuntimeException if an error occurs during decryption
     */
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