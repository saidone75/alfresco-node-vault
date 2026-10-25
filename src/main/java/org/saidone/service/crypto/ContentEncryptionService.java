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
import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import org.saidone.component.BaseComponent;
import org.saidone.config.ContentCompressionConfig;
import org.saidone.misc.ExactLengthInputStream;
import org.saidone.misc.ZstdCompressingInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.service.SecretService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Applies optional Zstandard compression before content encryption for every storage backend, so that repositories
 * only store and retrieve bytes.
 * <p>
 * Content is encrypted on storage when
 * {@code application.service.vault.encryption.content.enabled} is {@code true};
 * stored content is decrypted whenever its metadata marks it as encrypted.
 * </p>
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ContentEncryptionService extends BaseComponent {

    private final Optional<SecretService> secretService;
    private final Optional<CryptoService> cryptoService;
    private final ContentCompressionConfig compression;

    private static final String ZSTD_V1 = "zstd-v1";

    @Value("${application.service.vault.encryption.content.enabled:false}")
    private boolean encryptionEnabled;

    /**
     * Returns the stream of the bytes to store for the given content, encrypted
     * when content encryption is enabled, and records in {@code metadata}
     * whether they are encrypted and with which key version.
     *
     * @param content  the original content; closing the returned stream closes it
     * @param metadata object metadata, updated with the encryption details
     * @param originalLength expected original content size, before either transformation
     * @return the stream of the bytes to store
     */
    public InputStream toStoredContent(InputStream content, Map<String, String> metadata, long originalLength) {
        InputStream transformed = content;
        try {
            if (originalLength < 0) throw new IllegalArgumentException("Negative original content length");
            metadata.put(MetadataKeys.ORIGINAL_LENGTH, Long.toString(originalLength));
            // Also validates input length before a new GridFS object can replace an old one.
            transformed = new ExactLengthInputStream(content, originalLength);
            if (compression.isEnabled()) {
                transformed = new ZstdCompressingInputStream(transformed, compression.getLevel());
                metadata.put(MetadataKeys.COMPRESSION, ZSTD_V1);
            } else {
                metadata.remove(MetadataKeys.COMPRESSION);
            }
            if (!encryptionEnabled) {
                metadata.put(MetadataKeys.ENCRYPTED, Boolean.FALSE.toString());
                return transformed;
            }
            try (val secret = secretService.orElseThrow().getSecret()) {
                metadata.put(MetadataKeys.ENCRYPTED, Boolean.TRUE.toString());
                metadata.put(MetadataKeys.KEY_VERSION, String.valueOf(secret.getVersion()));
                return cryptoService.orElseThrow().encrypt(transformed, secret);
            }
        } catch (IOException | RuntimeException e) {
            closeAfterFailure(transformed, e);
            throw new IllegalStateException("Unable to prepare stored content", e);
        }
    }

    /** Reads according to stored metadata, independently of current compression write settings. */
    public InputStream fromStoredContent(InputStream stored, Map<String, ?> metadata) {
        InputStream decoded = stored;
        try {
            val encoding = metadata.get(MetadataKeys.COMPRESSION);
            if (encoding != null && !ZSTD_V1.equals(encoding)) {
                throw new IllegalArgumentException("Unsupported content compression: " + encoding);
            }
            decoded = fromStoredContent(stored, Boolean.parseBoolean(String.valueOf(metadata.get(MetadataKeys.ENCRYPTED))));
            if (encoding != null) {
                val length = Long.parseLong(String.valueOf(metadata.get(MetadataKeys.ORIGINAL_LENGTH)));
                if (length < 0) throw new IllegalArgumentException("Negative original content length");
                val decompressor = new ZstdInputStreamNoFinalizer(decoded);
                decoded = decompressor;
                decompressor.setLongMax(23);
                decoded = new ExactLengthInputStream(decoded, length);
            }
            return decoded;
        } catch (IOException | RuntimeException e) {
            closeAfterFailure(decoded, e);
            throw new IllegalStateException("Unable to decode stored content", e);
        }
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
            closeAfterFailure(stored, e);
            throw e;
        }
    }

    private static void closeAfterFailure(InputStream stream, Exception failure) {
        try {
            stream.close();
        } catch (IOException closing) {
            failure.addSuppressed(closing);
        }
    }

}
