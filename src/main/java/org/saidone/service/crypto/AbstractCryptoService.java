package org.saidone.service.crypto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Setter;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.saidone.component.BaseComponent;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.service.SecretService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
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
@Setter
public abstract class AbstractCryptoService extends BaseComponent implements CryptoService {

    @Autowired
    private SecretService secretService;

    @Valid
    @NotNull
    protected Kdf kdf;

    // with version == null we retrieve the last version
    protected Pair<SecretKeySpec, Integer> deriveSecretKey(String algorithm, byte[] salt) {
        return deriveSecretKey(algorithm, salt, null);
    }

    /**
     * Derives a secret key based on the specified key derivation function (KDF) implementation.
     *
     * <p>This method selects the key derivation algorithm implementation configured in {@code kdf}
     * and derives a secret key accordingly. Supported KDF implementations include HKDF, Argon2,
     * and PBKDF2 (default).</p>
     *
     * @param algorithm the name of the cryptographic algorithm for which the secret key is derived
     * @param salt      the salt value used in the key derivation process
     * @param version   the version of the key derivation parameters to use
     * @return a {@code Pair} containing the derived {@link javax.crypto.spec.SecretKeySpec} and an {@link Integer} representing the key version
     */
    protected Pair<SecretKeySpec, Integer> deriveSecretKey(String algorithm, byte[] salt, Integer version) {
        return switch (kdf.getImpl()) {
            case "hkdf" -> deriveHkdfSecretKey(algorithm, salt, version);
            case "argon2" -> deriveArgon2SecretKey(algorithm, salt, version);
            default -> derivePbkdf2SecretKey(algorithm, salt, version);
        };
    }

    /**
     * Derives a secret key using PBKDF2 key derivation function
     */
    private Pair<SecretKeySpec, Integer> derivePbkdf2SecretKey(String algorithm, byte[] salt, Integer version) {
        try {
            val secret = secretService.getSecret(version);
            val spec = new PBEKeySpec(new String(secret.getData(), StandardCharsets.UTF_8).toCharArray(), salt, kdf.pbkdf2.getIterations(), 256);
            Arrays.fill(secret.getData(), (byte) 0);
            val skf = SecretKeyFactory.getInstance(Kdf.Pbkdf2.PBKDF2_KEY_FACTORY_ALGORITHM);
            return Pair.of(new SecretKeySpec(skf.generateSecret(spec).getEncoded(), algorithm), secret.getVersion());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        }
    }

    /**
     * Derives a secret key using HKDF key derivation function
     */
    private Pair<SecretKeySpec, Integer> deriveHkdfSecretKey(String algorithm, byte[] salt, Integer version) {
        val hkdf = new HKDFBytesGenerator(new SHA256Digest());
        val secret = secretService.getSecret(version);
        val hkdfParams = new HKDFParameters(secret.getData(), salt, kdf.hkdf.getInfo().getBytes(StandardCharsets.UTF_8));
        hkdf.init(hkdfParams);
        byte[] keyBytes = new byte[32];
        hkdf.generateBytes(keyBytes, 0, keyBytes.length);
        Arrays.fill(secret.getData(), (byte) 0);
        return Pair.of(new SecretKeySpec(keyBytes, algorithm), secret.getVersion());
    }

    /**
     * Derives a secret key using Argon2 key derivation function
     */
    private Pair<SecretKeySpec, Integer> deriveArgon2SecretKey(String algorithm, byte[] salt, int version) {
        val builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withParallelism(kdf.argon2.parallelism)
                .withMemoryAsKB(kdf.argon2.memory)
                .withIterations(kdf.argon2.iterations);
        val generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        byte[] keyBytes = new byte[32];
        val secret = secretService.getSecret(version);
        generator.generateBytes(secret.getData(), keyBytes);
        Arrays.fill(secret.getData(), (byte) 0);
        return Pair.of(new SecretKeySpec(keyBytes, algorithm), secret.getVersion());
    }

    /**
     * Configuration class for key derivation function settings
     * Contains nested configuration classes for PBKDF2, HKDF and Argon2
     */
    @Validated
    @Data
    public static class Kdf {
        @NotBlank(message = "The 'impl' field of Kdf is required")
        @Pattern(
                regexp = "pbkdf2|hkdf|argon2",
                message = "The 'impl' field of Kdf must be either 'pbkdf2', 'hkdf' or 'argon2'"
        )
        private String impl;
        @Valid
        private Pbkdf2 pbkdf2;
        @Valid
        private Hkdf hkdf;
        @Valid
        private Argon2 argon2;

        /**
         * Configuration for PBKDF2 key derivation function
         */
        @Data
        public static class Pbkdf2 {
            private static final String PBKDF2_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
            @Min(100000)
            private Integer iterations = 100000;
        }

        /**
         * Configuration for HKDF key derivation function
         */
        @Data
        public static class Hkdf {
            @NotBlank(message = "The 'info' field of HKDF is required")
            private String info;
        }

        /**
         * Configuration for Argon2 key derivation function
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

    /**
     * Encrypts a plain text string and returns Base64 encoded result
     *
     * @param text The text to encrypt
     * @return Base64 encoded encrypted text
     */
    public String encryptText(String text) {
        try (val is = encrypt(new AnvDigestInputStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))))) {
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