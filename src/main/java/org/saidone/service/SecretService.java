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

package org.saidone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.config.EncryptionConfig;
import org.saidone.service.crypto.Secret;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Service responsible for reading encryption secrets from HashiCorp Vault.
 *
 * <p>The service uses Spring Vault {@code kv-v2} operations to read one configured key
 * from one configured secret path. Consumers can request either the latest secret version
 * or a specific version.</p>
 *
 * <p>When an error occurs while waiting for the asynchronous Vault call, methods in this class
 * log the exception, restore the interrupted status for {@link InterruptedException}, and return
 * {@code null} to indicate the secret could not be retrieved.</p>
 */
@RequiredArgsConstructor
@Service
@Slf4j
@ConditionalOnExpression("${application.service.vault.encryption.enabled}.equals(true)")
public class SecretService extends BaseComponent {

    private final VaultTemplate vaultTemplate;
    private final EncryptionConfig properties;

    private static VaultVersionedKeyValueOperations vaultVersionedKeyValueOperations;

    /**
     * Initializes Vault operations after dependency injection.
     *
     * <p>This method prepares the {@link VaultVersionedKeyValueOperations} instance bound to
     * the configured KV mount and checks the Vault system health endpoint. If Vault is reported
     * as not initialized, startup is aborted through {@link #shutDown(int)}.</p>
     */
    @Override
    public void init() {
        super.init();
        vaultVersionedKeyValueOperations = vaultTemplate.opsForVersionedKeyValue(properties.getVaultSecretKvMount());
        val health = vaultTemplate.opsForSys().health();
        if (!health.isInitialized()) {
            log.error("Unable to start {}", this.getClass().getSimpleName());
            super.shutDown(0);
        }
    }

    /**
     * Retrieves the latest configured secret value from Vault.
     *
     * <p>This is a convenience method equivalent to calling {@link #getSecret(Integer)}
     * with {@code null}.</p>
     *
     * @return the latest {@link Secret}, or {@code null} when retrieval fails
     */
    public Secret getSecret() {
        try {
            return getSecretAsync(null).get();
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            log.trace(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Retrieves a secret from Vault for a specific version or for the latest version.
     *
     * @param version the version number to retrieve; {@code null} loads the latest version
     * @return a {@link Secret} containing secret bytes and metadata version, or {@code null}
     *         if retrieval fails
     */
    public Secret getSecret(Integer version) {
        try {
            return getSecretAsync(version).get();
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage());
            log.trace(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Asynchronously fetches and maps a secret from Vault.
     *
     * @param version secret version to retrieve; {@code null} for the latest version
     * @return a future that resolves to a {@link Secret} built from Vault response data
     * @throws RuntimeException when Vault returns no payload/metadata for the requested version
     *                          or when the configured secret key is absent in the payload
     */
    private CompletableFuture<Secret> getSecretAsync(Integer version) {
        return CompletableFuture.supplyAsync(() -> {
            val response = version == null ?
                    vaultVersionedKeyValueOperations.get(properties.getVaultSecretPath()) :
                    vaultVersionedKeyValueOperations.get(properties.getVaultSecretPath(), Versioned.Version.from(version));
            if (response != null && response.getData() != null && response.getMetadata() != null) {
                return Secret.builder()
                        .version(response.getMetadata().getVersion().getVersion())
                        .data(((Map<?, ?>) response.getData()).get(properties.getVaultSecretKey()).toString().getBytes(StandardCharsets.UTF_8))
                        .build();
            } else {
                throw new RuntimeException("Unable to retrieve secret from vault");
            }
        });
    }

}
