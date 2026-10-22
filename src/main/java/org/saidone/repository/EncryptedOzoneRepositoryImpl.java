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

package org.saidone.repository;

import lombok.val;

import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.client.OzoneKeyDetails;
import org.saidone.model.MetadataKeys;
import org.saidone.service.SecretService;
import org.saidone.service.crypto.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * {@link OzoneRepositoryImpl} variant that transparently encrypts data before
 * uploading to Ozone and decrypts it when retrieved, if marked as encrypted.
 * Encryption is delegated to the provided {@link CryptoService}.
 * <p>
 * The bean becomes active only when
 * {@code application.service.vault.encryption.content.enabled} is {@code true} and
 * {@code application.service.vault.storage.impl} equals {@code "ozone"}.
 */
@Service
@ConditionalOnExpression("'${application.service.vault.storage.impl}' == 'ozone' and '${application.service.vault.encryption.content.enabled:false}' == 'true'")
public class EncryptedOzoneRepositoryImpl extends OzoneRepositoryImpl {

    private final SecretService secretService;
    private final CryptoService cryptoService;

    /**
     * Creates a new repository instance using the given Ozone bucket and
     * cryptographic service.
     *
     * @param bucket        Ozone bucket used for content storage
     * @param secretService service providing encryption material
     * @param cryptoService service responsible for encryption and decryption
     */
    public EncryptedOzoneRepositoryImpl(OzoneBucket bucket, SecretService secretService, CryptoService cryptoService) {
        super(bucket);
        this.secretService = secretService;
        this.cryptoService = cryptoService;
    }

    /**
     * Returns the encrypted stream of the provided content and marks the
     * object's metadata as encrypted with the current key version.
     *
     * @param content  the original content
     * @param metadata object metadata, updated with the encryption details
     * @return the encrypted content stream
     */
    @Override
    public InputStream prepareContent(InputStream content, Map<String, String> metadata) {
        val secret = secretService.getSecret();
        metadata.put(MetadataKeys.ENCRYPTED, Boolean.TRUE.toString());
        metadata.put(MetadataKeys.KEY_VERSION, Integer.toString(secret.getVersion()));
        return cryptoService.encrypt(content, secret);
    }

    /**
     * Retrieves the object content from Ozone.
     * If the object is encrypted, decrypts the content before returning.
     *
     * @param nodeId  identifier of the node / object key
     * @param details stored object metadata used to determine whether to decrypt
     * @return the object content stream, decrypted if necessary
     * @throws IOException if the content cannot be retrieved
     */
    @Override
    public InputStream getObject(String nodeId, OzoneKeyDetails details) throws IOException {
        val stream = super.getObject(nodeId, details);
        try {
            return Boolean.parseBoolean(details.getMetadata().get(MetadataKeys.ENCRYPTED))
                    ? cryptoService.decrypt(stream)
                    : stream;
        } catch (RuntimeException e) {
            try {
                stream.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

}
