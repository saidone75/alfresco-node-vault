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

import org.alfresco.core.model.Node;
import org.saidone.model.MetadataKeys;
import org.saidone.service.crypto.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.InputStream;
import java.util.Map;

/**
 * {@link S3RepositoryImpl} variant that transparently encrypts data before
 * uploading to S3 and decrypts it when retrieved. Encryption is delegated to
 * the provided {@link CryptoService}.
 */
@Service
@ConditionalOnProperty(name = "application.service.vault.encryption.enabled", havingValue = "true")
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
     * Encrypts the provided stream before delegating to the parent implementation.
     *
     * @param bucketName  destination bucket
     * @param node        node whose id acts as the key
     * @param metadata    metadata key/value pairs to associate with the object
     * @param inputStream content stream to encrypt and upload
     */
    @Override
    public void putObject(String bucketName, Node node, Map<String, String> metadata, InputStream inputStream) {
        metadata.put(MetadataKeys.ENCRYPTED, Boolean.TRUE.toString());
        super.putObject(bucketName, node, metadata, cryptoService.encrypt(inputStream));
    }

    /**
     * Retrieves and decrypts the object content for the given node id.
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