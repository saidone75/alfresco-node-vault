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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.config.EncryptionConfig;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing encryption secrets used in the application.
 * <p>
 * This service retrieves a secret either from a configured Vault path or from application properties,
 * and maintains it in encrypted form in memory. It provides functionality to periodically rotate the secret,
 * decrypting it when needed, and securely re-encrypting it with a newly generated AES key and IV.
 * <p>
 * The encryption uses AES with GCM mode with no padding. The secret is stored encrypted in memory,
 * with new keys and IVs generated whenever the secret is updated or rotated.
 * <p>
 * The secret rotation operation runs asynchronously in a background thread, which periodically decrypts and re-encrypts
 * the secret with a new key and IV, to maintain cryptographic freshness.
 * <p>
 * The service lifecycle is tied to the application lifecycle via the {@link BaseComponent} lifecycle methods.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class SecretService extends BaseComponent {

    private final VaultTemplate vaultTemplate;
    private final EncryptionConfig properties;

    private static final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;
    private byte[] iv;
    private byte[] secret;

    private CompletableFuture<Void> rotateSecret;
    private boolean running;

    @Override
    public void init() {
        super.init();
        if (properties.getVaultSecretPath() != null && properties.getVaultSecretKey() != null) {
            setSecret(getSecretFromVault());
        } else {
            setSecret(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        }
        running = true;
        rotateSecret = CompletableFuture.runAsync(this::rotateSecret);
    }

    /**
     * Continuously rotates the encryption secret while the service is running.
     * <p>
     * This method runs in a loop as long as the {@code running} flag is true.
     * It periodically calls {@link #getSecret()} to decrypt and re-encrypt the secret,
     * ensuring cryptographic freshness by regenerating encryption keys and initialization vectors.
     * Between rotations, it sleeps for a random duration up to 5 seconds to avoid predictable timing.
     * Any {@link InterruptedException} during sleep is caught and logged as a warning.
     */
    private void rotateSecret() {
        while (running) {
            try {
                getSecret();
                TimeUnit.MILLISECONDS.sleep(secureRandom.nextInt(5000));
            } catch (InterruptedException e) {
                log.warn("Error rotating secret", e);
            }
        }
    }

    /**
     * Returns the decrypted secret as a byte array.
     * <p>
     * This method synchronizes access to ensure thread safety when decrypting
     * the stored secret. It uses AES encryption with GCM mode (128-bit tag)
     * and no padding. The method decrypts the current encrypted secret using
     * the stored secret key and initialization vector (IV), then re-encrypts
     * it with a newly generated key and IV to maintain cryptographic freshness.
     *
     * @return the decrypted secret bytes
     * @throws RuntimeException if any error occurs during decryption
     */
    public synchronized byte[] getSecret() {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            val secret = cipher.doFinal(this.secret);
            setSecret(secret);
            return secret;
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting secret", e);
        }
    }

    /**
     * Encrypts and stores the provided secret byte array using AES encryption with GCM mode.
     * <p>
     * This method generates a new 256-bit AES secret key and a random 12-byte initialization vector (IV)
     * using a secure random number generator. It then encrypts the given secret with the generated key and IV,
     * storing the resulting ciphertext internally. After encryption, the input secret byte array is securely wiped
     * by filling it with zeroes to prevent sensitive data retention in memory.
     * <p>
     * If any error occurs during key generation or encryption, a RuntimeException is thrown.
     *
     * @param secret the plaintext secret bytes to be encrypted and stored; this array will be zeroed out after use
     * @throws RuntimeException if encryption or key generation fails
     */
    private void setSecret(byte[] secret) {
        try {
            val keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            secretKey = keyGen.generateKey();
            val cipher = Cipher.getInstance("AES/GCM/NoPadding");
            iv = new byte[12];
            secureRandom.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, new GCMParameterSpec(128, iv));
            this.secret = cipher.doFinal(secret);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting secret", e);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /**
     * Retrieves the secret from the Vault using the configured Vault path and key.
     * <p>
     * This method reads the secret at the specified Vault path via the VaultTemplate,
     * expecting the secret data to be stored under the "data" key in the response.
     * It extracts the secret value using the configured secret key and returns it as a UTF-8 encoded byte array.
     *
     * @return the secret byte array fetched from Vault
     * @throws IllegalStateException if the secret is not found at the configured Vault path
     */
    private byte[] getSecretFromVault() {
        val response = vaultTemplate.read(properties.getVaultSecretPath());
        if (response.getData() == null) {
            throw new IllegalStateException("Secret not found in Vault");
        }
        return (((Map<?, ?>) response.getData().get("data")).get(properties.getVaultSecretKey())).toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void stop() {
        running = false;
        try {
            rotateSecret.get();
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
        }
        super.stop();
    }

}
