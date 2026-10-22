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

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.model.MetadataKeys;
import org.saidone.service.SecretService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Applies content encryption for every storage backend, so that repositories
 * only store and retrieve bytes.
 * <p>
 * Content is encrypted on storage when
 * {@code application.service.vault.encryption.content.enabled} is {@code true};
 * stored content is decrypted whenever its metadata marks it as encrypted.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ContentEncryptionService extends BaseComponent {

    private final Optional<SecretService> secretService;
    private final Optional<CryptoService> cryptoService;

    @Value("${application.service.vault.encryption.content.enabled:false}")
    private boolean encryptionEnabled;

    /**
     * Returns the stream of the bytes to store for the given content, encrypted
     * when content encryption is enabled, and records in {@code metadata}
     * whether they are encrypted and with which key version.
     *
     * @param content  the original content; closing the returned stream closes it
     * @param metadata object metadata, updated with the encryption details
     * @return the stream of the bytes to store
     */
    public InputStream toStoredContent(InputStream content, Map<String, String> metadata) {
        if (!encryptionEnabled) {
            metadata.put(MetadataKeys.ENCRYPTED, Boolean.FALSE.toString());
            return content;
        }
        val secret = secretService.orElseThrow().getSecret();
        metadata.put(MetadataKeys.ENCRYPTED, Boolean.TRUE.toString());
        metadata.put(MetadataKeys.KEY_VERSION, String.valueOf(secret.getVersion()));
        return cryptoService.orElseThrow().encrypt(content, secret);
    }

    /**
     * Returns the original content of the given stored bytes, decrypting them
     * when they are encrypted. The stored stream is closed if decryption cannot
     * be set up.
     *
     * @param stored    the stored bytes
     * @param encrypted whether the stored bytes are encrypted, as recorded in their metadata
     * @return the original content stream
     * @throws IllegalStateException if the content is encrypted but no encryption is configured
     */
    public InputStream fromStoredContent(InputStream stored, boolean encrypted) {
        if (!encrypted) return stored;
        try {
            return cryptoService
                    .orElseThrow(() -> new IllegalStateException("Content is encrypted but encryption is not configured"))
                    .decrypt(stored);
        } catch (RuntimeException e) {
            try {
                stored.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

}
