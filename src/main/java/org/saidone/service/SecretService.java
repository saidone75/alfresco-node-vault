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
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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
     * as not initialized, startup is aborted through {@link #shutDown(int)}.
     * The latest encryption secret must also be readable and nonempty; otherwise bean
     * initialization fails. Bytes read for this check are cleared before returning.</p>
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
        Secret secret = null;
        try {
            if (properties.isInitializeSecretOnStartup()) {
                initializeSecretIfAbsent();
            }
            secret = getSecret();
            if (secret.getData() == null || secret.getData().length == 0) {
                throw new VaultException("The configured encryption secret is empty");
            }
        } catch (RuntimeException e) {
            throw new VaultException("""
                    Cannot start with encryption enabled: the configured Vault encryption secret \
                    is missing, empty or unreadable. Check Vault configuration and permissions; \
                    provision the secret explicitly for a new installation or restore the required versions \
                    for an existing archive.""", e);
        } finally {
            if (secret != null && secret.getData() != null) {
                Arrays.fill(secret.getData(), (byte) 0);
            }
        }
    }

    private void initializeSecretIfAbsent() {
        val path = properties.getVaultSecretPath();
        if (vaultVersionedKeyValueOperations.get(path) != null) {
            return;
        }
        if (vaultVersionedKeyValueOperations.opsForKeyValueMetadata().get(path) != null) {
            // Another instance may have provisioned between the data and metadata reads.
            if (vaultVersionedKeyValueOperations.get(path) != null) {
                return;
            }
            throw new VaultException("Encryption secret has existing Vault metadata; restore its required versions");
        }
        val random = new byte[32];
        byte[] encoded = null;
        val data = new HashMap<String, Object>();
        try {
            new SecureRandom().nextBytes(random);
            encoded = Base64.getEncoder().encode(random);
            data.put(properties.getVaultSecretKey(), new String(encoded, StandardCharsets.US_ASCII));
            try {
                vaultVersionedKeyValueOperations.put(path, Versioned.create(data, Versioned.Version.from(0)));
            } catch (org.springframework.vault.VaultException e) {
                // Only a CAS conflict permits rereading the concurrent creator's secret.
                if (!(e.getCause() instanceof org.springframework.web.client.HttpStatusCodeException http)
                        || http.getStatusCode().value() != 400
                        || !http.getResponseBodyAsString().contains("check-and-set parameter did not match")) {
                    throw e;
                }
            }
        } finally {
            Arrays.fill(random, (byte) 0);
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
            data.clear();
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
