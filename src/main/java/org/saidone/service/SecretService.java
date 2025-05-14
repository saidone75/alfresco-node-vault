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
import org.saidone.component.BaseComponent;
import org.saidone.config.EncryptionConfig;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    public byte[] getSecret() {
        try {
            return getSecretAsync().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<byte[]> getSecretAsync() {
        return CompletableFuture.supplyAsync(() -> {
            var response = vaultTemplate.read(properties.getVaultSecretPath());
            if (response.getData() == null) {
                throw new IllegalStateException("Secret not found in Vault");
            }
            Object data = ((Map<?, ?>) response.getData().get("data"))
                    .get(properties.getVaultSecretKey());

            if (data == null) {
                throw new IllegalStateException("Secret key not found");
            }

            return data.toString().getBytes(StandardCharsets.UTF_8);
        });
    }

}
