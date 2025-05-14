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

    public void rotateSecret() {
        while (running) {
            try {
                getSecret();
                TimeUnit.MILLISECONDS.sleep(secureRandom.nextInt(5000));
            } catch (InterruptedException e) {
                log.warn("Error rotating secret", e);
            }
        }
    }

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
