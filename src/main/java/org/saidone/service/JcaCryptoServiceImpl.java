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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Setter;
import lombok.val;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.saidone.component.BaseComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

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
@Setter
@ConfigurationProperties(prefix = "application.service.vault.encryption.jca")
@ConditionalOnExpression("${application.service.vault.encryption.enabled:true} == true && '${application.service.vault.encryption.impl:}' == 'jca'")
public class JcaCryptoServiceImpl extends BaseComponent implements CryptoService {

    @Min(16)
    private int saltLength;
    @Min(12)
    private int ivLength;
    private static final int TAG_LENGTH = 128;
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    @NotBlank
    private String key;

    @Valid
    @NotNull
    private Kdf kdf;

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Derives a {@link SecretKeySpec} using the specified salt.
     * <br>
     * The key derivation function (KDF) used depends on the value of {@code kdfImpl}.
     * If {@code kdfImpl} is set to "argon2", the Argon2 KDF is used; otherwise, PBKDF2 is applied.
     *
     * @param salt the salt to use for key derivation
     * @return the derived {@link SecretKeySpec}
     */
    private SecretKeySpec deriveSecretKey(byte[] salt) {
        if (kdf.equals("argon2")) return deriveArgon2SecretKey(salt);
        else return derivePbkdf2SecretKey(salt);
    }

    /**
     * Derives a secret key using the PBKDF2 (Password-Based Key Derivation Function 2) algorithm with the given salt.
     * <br>
     * This method transforms a password and salt into a {@link SecretKeySpec} suitable for cryptographic operations
     * by running the PBKDF2 function for a specified number of iterations and key length.
     *
     * @param salt the salt used in the key derivation process
     * @return the derived {@link SecretKeySpec}
     * @throws IllegalStateException if the key derivation process cannot be completed due to algorithm or key spec errors
     */
    private SecretKeySpec derivePbkdf2SecretKey(byte[] salt) {
        try {
            val spec = new PBEKeySpec(key.toCharArray(), salt, kdf.pbkdf2.getIterations(), 256);
            val skf = SecretKeyFactory.getInstance(Kdf.Pbkdf2.PBKDF2_KEY_FACTORY_ALGORITHM);
            return new SecretKeySpec(skf.generateSecret(spec).getEncoded(), KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        }
    }

    /**
     * Derives a secret key using the Argon2 key derivation function.
     * <p>
     * This method initializes an Argon2BytesGenerator with parameters such as salt, parallelism, memory cost, and iterations.
     * The supplied salt and an instance variable {@code key} are used to generate a 32-byte key, which is then returned
     * as a {@link SecretKeySpec} suitable for AES encryption.
     *
     * @param salt the salt to use for the Argon2 key derivation function
     * @return a SecretKeySpec containing the derived AES key
     */
    private SecretKeySpec deriveArgon2SecretKey(byte[] salt) {
        val builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withParallelism(kdf.argon2.parallelism)
                .withMemoryAsKB(kdf.argon2.memory)
                .withIterations(kdf.argon2.iterations);
        val generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        byte[] generatedKeyBytes = new byte[32];
        generator.generateBytes(key.getBytes(StandardCharsets.UTF_8), generatedKeyBytes);
        return new SecretKeySpec(generatedKeyBytes, KEY_ALGORITHM);
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
            byte[] salt = new byte[saltLength];
            secureRandom.nextBytes(salt);

            // Generate random IV for GCM mode
            byte[] iv = new byte[ivLength];
            secureRandom.nextBytes(iv);

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            val spec = new GCMParameterSpec(TAG_LENGTH, iv);
            val secretKey = deriveSecretKey(salt);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Concatenate salt and IV
            byte[] saltAndIv = new byte[saltLength + ivLength];
            System.arraycopy(salt, 0, saltAndIv, 0, saltLength);
            System.arraycopy(iv, 0, saltAndIv, saltLength, ivLength);

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
            byte[] salt = inputStream.readNBytes(saltLength);

            // Read IV from the the stream
            byte[] iv = inputStream.readNBytes(ivLength);

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

    /**
     * Encapsulates the configuration for the Key Derivation Function (KDF) used in cryptographic processes.
     * This class allows choosing between "pbkdf2" and "argon2" implementations through the "impl" property.
     * Each implementation can be further configured using nested classes for their specific parameters.
     * Validation annotations ensure correct configuration by enforcing the presence of mandatory fields and constraints on parameter values.
     * <p>
     * The Pbkdf2 nested class defines the algorithm and required iteration count for PBKDF2-based key derivation.
     * The Argon2 nested class specifies settings for Argon2-based key derivation, such as parallelism, memory usage, and iterations.
     */
    @Validated
    @Data
    public static class Kdf {
        @NotBlank(message = "The 'impl' field of Kdf is required")
        @Pattern(
                regexp = "pbkdf2|argon2",
                message = "The 'impl' field of Kdf must be either 'pbkdf2' or 'argon2'"
        )
        private String impl;
        @Valid
        private Pbkdf2 pbkdf2;
        @Valid
        private Argon2 argon2;

        /**
         * Contains configuration settings for the PBKDF2 implementation of the key derivation function.
         * Defines the algorithm used for key factory and the minimum number of iterations to be applied during key derivation.
         * The iterations parameter is essential to determine the computational difficulty of the operation.
         */
        @Data
        public static class Pbkdf2 {
            private static final String PBKDF2_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
            @Min(100000)
            private Integer iterations = 100000;
        }

        /**
         * Contains configuration settings for the Argon2 implementation of the key derivation function.
         * Specifies minimum values for parallelism, memory consumption (in bytes), and the number of iterations required.
         * These parameters influence both the security and the computational requirements of the derivation process.
         */
        @Data
        public static class Argon2 {
            @Min(1)
            private Integer parallelism = 1;
            @Min(65536)
            private Integer memory;
            @Min(3)
            private Integer iterations;
        }

    }

}