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

package org.saidone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.config.EncryptionConfig;
import org.saidone.exception.VaultException;
import org.saidone.service.crypto.Secret;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service responsible for reading encryption secrets from HashiCorp Vault.
 *
 * <p>The service uses Spring Vault {@code kv-v2} operations to read one configured key
 * from one configured secret path. Consumers can request either the latest secret version
 * or a specific version.</p>
 *
 * <p>When the secret cannot be retrieved, methods in this class throw a {@link VaultException}
 * carrying the cause. The service does not clear the calling thread's interrupted status.</p>
 *
 * <p>The bean is active when at least one of
 * {@code application.service.vault.encryption.content.enabled} or
 * {@code application.service.vault.encryption.metadata.enabled} is set to
 * {@code true}.</p>
 */
@RequiredArgsConstructor
@Service
@Slf4j
@ConditionalOnExpression("('${application.service.vault.encryption.content.enabled:false}' == 'true' or '${application.service.vault.encryption.metadata.enabled:false}' == 'true')")
public class SecretService extends BaseComponent {

    private final VaultTemplate vaultTemplate;
    private final EncryptionConfig properties;

    private VaultVersionedKeyValueOperations vaultVersionedKeyValueOperations;

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
            super.shutDown(1);
        }
    }

    /**
     * Retrieves the latest configured secret value from Vault.
     *
     * <p>This is a convenience method equivalent to calling {@link #getSecret(Integer)}
     * with {@code null}.</p>
     *
     * @return the latest {@link Secret}
     * @throws VaultException if the secret cannot be retrieved
     */
    public Secret getSecret() {
        return getSecret(null);
    }

    /**
     * Retrieves a secret from Vault for a specific version or for the latest version.
     *
     * @param version the version number to retrieve; {@code null} loads the latest version
     * @return a {@link Secret} containing secret bytes and metadata version
     * @throws VaultException if the secret cannot be retrieved
     */
    public Secret getSecret(Integer version) {
        val description = version == null ? "latest secret" : String.format("secret v.%d", version);
        if (Thread.currentThread().isInterrupted()) {
            throw new VaultException(String.format("Interrupted while retrieving %s from Vault", description),
                    new InterruptedException("Calling thread is interrupted"));
        }
        try {
            val response = version == null ?
                    vaultVersionedKeyValueOperations.get(properties.getVaultSecretPath()) :
                    vaultVersionedKeyValueOperations.get(properties.getVaultSecretPath(), Versioned.Version.from(version));
            if (response != null && response.getData() != null && response.getMetadata() != null) {
                val value = ((Map<?, ?>) response.getData()).get(properties.getVaultSecretKey());
                if (value == null) {
                    throw new IllegalStateException(String.format("Key %s not found in secret %s",
                            properties.getVaultSecretKey(), properties.getVaultSecretPath()));
                }
                return Secret.builder()
                        .version(response.getMetadata().getVersion().getVersion())
                        .data(value.toString().getBytes(StandardCharsets.UTF_8))
                        .build();
            } else {
                throw new IllegalStateException(String.format("No data found in secret %s", properties.getVaultSecretPath()));
            }
        } catch (RuntimeException e) {
            log.trace(e.getMessage(), e);
            throw new VaultException(String.format("Unable to retrieve %s from Vault: %s", description, e.getMessage()), e);
        }
    }

}
