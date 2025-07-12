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

package org.saidone.repository;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.model.Node;
import org.saidone.service.crypto.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.InputStream;
import java.util.HashMap;

/**
 * {@link S3RepositoryImpl} variant that transparently encrypts data before
 * uploading to S3 and decrypts it when retrieved. Encryption is delegated to
 * the provided {@link CryptoService}.
 * <p>
 * The bean becomes active only when
 * {@code application.service.vault.encryption.enabled} is {@code true} and
 * {@code application.service.vault.storage.impl} equals {@code "s3"}.
 */
@Service
@ConditionalOnExpression(
        "${application.service.vault.encryption.enabled}.equals(true) and '${application.service.vault.storage.impl}'.equals('s3')"
)
@Slf4j
public class EncryptedS3RepositoryImpl extends S3RepositoryImpl {

    /**
     * Service used to perform stream encryption and decryption.
     */
    private final CryptoService cryptoService;

    /**
     * Creates a new repository instance using the given AWS client and
     * cryptographic service.
     *
     * @param s3Client      AWS S3 client
     * @param cryptoService service responsible for encryption and decryption
     */
    public EncryptedS3RepositoryImpl(S3Client s3Client, CryptoService cryptoService) {
        super(s3Client);
        this.cryptoService = cryptoService;
    }

    /**
     * Encrypts the provided content stream and stores it in S3. The object's
     * metadata is updated to mark it as encrypted before delegating to the
     * parent implementation.
     *
     * @param bucketName  destination bucket
     * @param node        node whose id acts as the key
     * @param metadata
     * @param inputStream content stream to encrypt and upload
     */
    @Override
    public void putObject(String bucketName, Node node, HashMap<String, String> metadata, InputStream inputStream) {
        super.putObject(bucketName, node, metadata, cryptoService.encrypt(inputStream));
    }

    /**
     * Retrieves the encrypted object content from S3 and returns a decrypted
     * stream using the configured {@link CryptoService}.
     *
     * @param bucketName bucket containing the object
     * @param nodeId     the node id / object key
     * @return decrypted content stream
     */
    @Override
    public InputStream getObject(String bucketName, String nodeId) {
        return cryptoService.decrypt(super.getObject(bucketName, nodeId));
    }

}