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
import org.saidone.service.crypto.Key;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Service class for interacting with Vault to retrieve secrets.
 * <p>
 * This service uses Spring Vault's versioned key-value operations to fetch secrets
 * from a configured Vault path and key. It supports retrieving secrets by specific version
 * or the latest version if none is specified.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class SecretService extends BaseComponent {

    private final VaultTemplate vaultTemplate;
    private final EncryptionConfig properties;

    private static VaultVersionedKeyValueOperations vaultVersionedKeyValueOperations;

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
     * Retrieves the secret from Vault for the specified version.
     *
     * @param version the version of the secret to retrieve; if null, retrieves the latest version
     * @return a Pair containing the secret bytes and the version number
     * @throws RuntimeException if unable to retrieve the secret or if an error occurs during retrieval
     */
    public Key getSecret(Integer version) {
        try {
            return getSecretAsync(version).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Key> getSecretAsync(Integer version) {
        return CompletableFuture.supplyAsync(() -> {
            Versioned<Map<String, Object>> response;
            if (version == null) {
                response = vaultVersionedKeyValueOperations.get(properties.getVaultSecretPath());
            } else {
                response = vaultVersionedKeyValueOperations.get(properties.getVaultSecretPath(), Versioned.Version.from(version));
            }
            if (response != null && response.getData() != null && response.getMetadata() != null) {
                return Key.builder()
                        .version(response.getMetadata().getVersion().getVersion())
                        .data(((Map<?, ?>) response.getData()).get(properties.getVaultSecretKey()).toString().getBytes(StandardCharsets.UTF_8))
                        .build();
            } else throw new RuntimeException("Unable to retrieve secret from vault");
        });
    }

}
