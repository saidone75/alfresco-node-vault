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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.model.MetadataKeys;
import org.saidone.service.SecretService;
import org.saidone.service.crypto.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.util.HashMap;

/**
 * {@link S3RepositoryImpl} variant that transparently encrypts data before
 * uploading to S3 and decrypts it when retrieved, if marked as encrypted. Encryption is delegated to
 * the provided {@link CryptoService}.
 * <p>
 * The bean becomes active only when
 * {@code application.service.vault.encryption.content.enabled} is {@code true} and
 * {@code application.service.vault.storage.impl} equals {@code "s3"}.
 */
@Service
@ConditionalOnExpression(
        "${application.service.vault.encryption.content.enabled}.equals(true) and '${application.service.vault.storage.impl}'.equals('s3')"
)
@Slf4j
public class EncryptedS3RepositoryImpl extends S3RepositoryImpl {

    private final SecretService secretService;
    private final CryptoService cryptoService;

    /**
     * Creates a new repository instance using the given AWS client and
     * cryptographic service.
     *
     * @param s3Client      AWS S3 client
     * @param secretService service providing encryption material
     * @param cryptoService service responsible for encryption and decryption
     */
    public EncryptedS3RepositoryImpl(S3Client s3Client, SecretService secretService, CryptoService cryptoService) {
        super(s3Client);
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
    public InputStream prepareContent(InputStream content, HashMap<String, String> metadata) {
        val secret = secretService.getSecret();
        metadata.put(MetadataKeys.ENCRYPTED, Boolean.TRUE.toString());
        metadata.put(MetadataKeys.KEY_VERSION, String.valueOf(secret.getVersion()));
        return cryptoService.encrypt(content, secret);
    }

    /**
     * Retrieves the object content from S3.
     * If the object is encrypted, decrypts the content before returning.
     *
     * @param bucketName bucket containing the object
     * @param nodeId     the node id / object key
     * @return the object content stream, decrypted if necessary
     */
    @Override
    public InputStream getObject(String bucketName, String nodeId) {
        val objectStream = getObjectStream(bucketName, nodeId);
        if (isEncrypted(objectStream.response())) {
            return cryptoService.decrypt(objectStream);
        }
        return objectStream;
    }

    /**
     * Checks if a given S3 object is marked as encrypted.
     *
     * @param response the response of the get object request
     * @return true if the object is encrypted, false otherwise
     */
    private boolean isEncrypted(GetObjectResponse response) {
        return Boolean.parseBoolean(response.metadata().get(MetadataKeys.ENCRYPTED));
    }

}