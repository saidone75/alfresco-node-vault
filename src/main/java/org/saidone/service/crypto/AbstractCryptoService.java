/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.saidone.component.BaseComponent;
import org.saidone.misc.AnvDigestInputStream;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Base implementation for {@link CryptoService} that provides common
 * functionality for the concrete encryption services. It offers helper methods
 * to derive secret keys using PBKDF2, HKDF or Argon2 and defines configuration
 * beans for the supported key derivation algorithms.
 */
public abstract class AbstractCryptoService extends BaseComponent implements CryptoService {

    /**
     * Derives a secret key based on the specified key derivation function (KDF) implementation.
     *
     * <p>This method selects the key derivation algorithm implementation configured in {@code kdf}
     * and derives a secret key accordingly. Supported KDF implementations include HKDF, Argon2,
     * and PBKDF2 (default).</p>
     *
     * @param secret    the secret fetched from Vault
     * @param algorithm the name of the cryptographic algorithm for which the secret key is derived
     * @param salt      the salt value used in the key derivation process
     * @return a {@code Pair} containing the derived {@link javax.crypto.spec.SecretKeySpec} and an {@link Integer} representing the key version
     */
    protected Pair<SecretKeySpec, Integer> deriveSecretKey(Secret secret, String algorithm, byte[] salt, Kdf kdf) {
        try (secret) {
            return switch (kdf.getImpl()) {
                case "hkdf" -> deriveHkdfSecretKey(secret, algorithm, salt, kdf);
                case "argon2" -> deriveArgon2SecretKey(secret, algorithm, salt, kdf);
                case "pbkdf2" -> derivePbkdf2SecretKey(secret, algorithm, salt, kdf);
                default -> throw new IllegalArgumentException("Unknown KDF");
            };
        }
    }

    /**
     * Derives a secret key using the PBKDF2 algorithm.
     *
     * @param secret    the secret fetched from Vault
     * @param algorithm the target algorithm of the resulting key
     * @param salt      the salt to use for key derivation
     * @return a pair containing the derived key and the version number
     */
    private Pair<SecretKeySpec, Integer> derivePbkdf2SecretKey(Secret secret, String algorithm, byte[] salt, Kdf kdf) {
        val decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(secret.getData()));
        val characters = decoded.array();
        val password = decoded.remaining() == characters.length
                ? characters
                : Arrays.copyOfRange(characters, decoded.position(), decoded.limit());
        try {
            val spec = new PBEKeySpec(password, salt, kdf.pbkdf2.getIterations(), 256);
            try {
                val skf = SecretKeyFactory.getInstance(Kdf.Pbkdf2.PBKDF2_KEY_FACTORY_ALGORITHM);
                val keyBytes = skf.generateSecret(spec).getEncoded();
                try {
                    return Pair.of(new SecretKeySpec(keyBytes, algorithm), secret.getVersion());
                } finally {
                    Arrays.fill(keyBytes, (byte) 0);
                }
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        } finally {
            Arrays.fill(password, '\0');
            if (password != characters) {
                Arrays.fill(characters, '\0');
            }
        }
    }

    /**
     * Derives a secret key using the HKDF key derivation function.
     *
     * @param secret    the secret fetched from Vault
     * @param algorithm the target algorithm of the resulting key
     * @param salt      the salt value used for the HKDF extraction phase
     * @return a pair containing the derived key and the version number
     */
    private Pair<SecretKeySpec, Integer> deriveHkdfSecretKey(Secret secret, String algorithm, byte[] salt, Kdf kdf) {
        val keyBytes = new byte[32];
        try {
            val hkdf = new HKDFBytesGenerator(new SHA256Digest());
            val hkdfParams = new HKDFParameters(secret.getData(), salt, kdf.hkdf.getInfo().getBytes(StandardCharsets.UTF_8));
            hkdf.init(hkdfParams);
            hkdf.generateBytes(keyBytes, 0, keyBytes.length);
            return Pair.of(new SecretKeySpec(keyBytes, algorithm), secret.getVersion());
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * Derives a secret key using the Argon2 key derivation function.
     *
     * @param secret    the secret fetched from Vault
     * @param algorithm the target algorithm of the resulting key
     * @param salt      the salt to use for key derivation
     * @return a pair containing the derived key and the version number
     */
    private Pair<SecretKeySpec, Integer> deriveArgon2SecretKey(Secret secret, String algorithm, byte[] salt, Kdf kdf) {
        val keyBytes = new byte[32];
        try {
            val builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withSalt(salt)
                    .withParallelism(kdf.argon2.parallelism)
                    .withMemoryAsKB(kdf.argon2.memory)
                    .withIterations(kdf.argon2.iterations);
            val generator = new Argon2BytesGenerator();
            generator.init(builder.build());
            generator.generateBytes(secret.getData(), keyBytes);
            return Pair.of(new SecretKeySpec(keyBytes, algorithm), secret.getVersion());
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * Configuration holder for key derivation settings.
     * <p>
     * The {@code impl} property selects the KDF implementation to use while the
     * nested classes expose specific configuration options for PBKDF2, HKDF and
     * Argon2.
     * </p>
     */
    @Validated
    @Data
    public static class Kdf {
        /**
         * Selected key derivation implementation: {@code pbkdf2}, {@code hkdf}
         * or {@code argon2}.
         */
        @NotBlank(message = "The 'impl' field of Kdf is required")
        @Pattern(
                regexp = "pbkdf2|hkdf|argon2",
                message = "The 'impl' field of Kdf must be either 'pbkdf2', 'hkdf' or 'argon2'"
        )
        private String impl;

        /** PBKDF2 specific options. */
        @Valid
        private Pbkdf2 pbkdf2;

        /** HKDF specific options. */
        @Valid
        private Hkdf hkdf;

        /** Argon2 specific options. */
        @Valid
        private Argon2 argon2;

        /**
         * Configuration for PBKDF2 key derivation function
         */
        @Data
        public static class Pbkdf2 {
            /** JCA algorithm name for key derivation. */
            private static final String PBKDF2_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";

            /** Number of hashing iterations to perform. */
            @Min(100000)
            private Integer iterations = 100000;
        }

        /**
         * Configuration for HKDF key derivation function
         */
        @Data
        public static class Hkdf {
            /** Context string used as HKDF info parameter. */
            @NotBlank(message = "The 'info' field of HKDF is required")
            private String info;
        }

        /**
         * Configuration for Argon2 key derivation function
         */
        @Data
        public static class Argon2 {
            /** Number of parallel threads. */
            @Min(1)
            private Integer parallelism = 1;

            /** Memory cost in kilobytes. */
            @Min(65536)
            private Integer memory;

            /** Iteration count. */
            @Min(3)
            private Integer iterations;
        }

    }

    /**
     * Encrypts a plain text string and returns a Base64 encoded result.
     *
     * @param text   The text to encrypt
     * @param secret secret material used to derive the encryption key
     * @return Base64 encoded encrypted text
     */
    public String encryptText(String text, Secret secret) {
        try (secret;
             val is = encrypt(new AnvDigestInputStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))), secret)) {
            return Base64.getEncoder().encodeToString(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error during text encryption", e);
        }
    }

    /**
     * Decrypts a Base64 encoded encrypted text string
     *
     * @param encryptedText The Base64 encoded encrypted text
     * @return Decrypted plain text
     */
    public String decryptText(String encryptedText) {
        try (val is = decrypt(new ByteArrayInputStream(Base64.getDecoder().decode(encryptedText)))) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error during text decryption", e);
        }
    }

}
