package org.saidone.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.val;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.saidone.component.BaseComponent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@ConfigurationProperties(prefix = "application.service.vault.encryption")
public abstract class AbstractCryptoService extends BaseComponent implements CryptoService {

    @NotBlank
    protected String secret;

    @Valid
    @NotNull
    protected Kdf kdf;

    protected SecretKeySpec deriveSecretKey(String algorithm, byte[] salt) {
        return switch (kdf.getImpl()) {
            case "hkdf" -> deriveHkdfSecretKey(algorithm, salt);
            case "argon2" -> deriveArgon2SecretKey(algorithm, salt);
            default -> derivePbkdf2SecretKey(algorithm, salt);
        };
    }

    private SecretKeySpec derivePbkdf2SecretKey(String algorithm, byte[] salt) {
        try {
            val spec = new PBEKeySpec(secret.toCharArray(), salt, kdf.pbkdf2.getIterations(), 256);
            val skf = SecretKeyFactory.getInstance(Kdf.Pbkdf2.PBKDF2_KEY_FACTORY_ALGORITHM);
            return new SecretKeySpec(skf.generateSecret(spec).getEncoded(), algorithm);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive secret key", e);
        }
    }

    private SecretKeySpec deriveHkdfSecretKey(String algorithm, byte[] salt) {
        // Use HKDF with SHA-256 as digest
        val hkdf = new HKDFBytesGenerator(new SHA256Digest());

        // Create HKDF parameters
        val secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        val hkdfParams = new HKDFParameters(secretBytes, salt, kdf.hkdf.getInfo().getBytes(StandardCharsets.UTF_8));

        hkdf.init(hkdfParams);

        // Generate a 32-byte (256-bit) key for ChaCha20
        byte[] keyBytes = new byte[32];
        hkdf.generateBytes(keyBytes, 0, keyBytes.length);

        return new SecretKeySpec(keyBytes, algorithm);
    }

    private SecretKeySpec deriveArgon2SecretKey(String algorithm, byte[] salt) {
        val builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withParallelism(kdf.argon2.parallelism)
                .withMemoryAsKB(kdf.argon2.memory)
                .withIterations(kdf.argon2.iterations);
        val generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        byte[] generatedKeyBytes = new byte[32];
        generator.generateBytes(secret.getBytes(StandardCharsets.UTF_8), generatedKeyBytes);
        return new SecretKeySpec(generatedKeyBytes, algorithm);
    }

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

        @Data
        public static class Pbkdf2 {
            private static final String PBKDF2_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
            @Min(100000)
            private Integer iterations = 100000;
        }

        @Data
        public static class Hkdf {
            @NotBlank(message = "The 'info' field of HKDF is required")
            private String info;
        }

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

    public String encryptText(String text) {
        try (val is = encrypt(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))) {
            return Base64.getEncoder().encodeToString(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error during text encryption", e);
        }
    }

    public String decryptText(String encryptedText) {
        try (val is = decrypt(new ByteArrayInputStream(Base64.getDecoder().decode(encryptedText)))) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error during text decryption", e);
        }
    }

}