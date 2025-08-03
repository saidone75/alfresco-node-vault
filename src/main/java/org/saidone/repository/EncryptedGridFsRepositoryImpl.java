/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.SneakyThrows;
import lombok.val;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.service.SecretService;
import org.saidone.service.crypto.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * GridFS repository that transparently encrypts content before it is persisted
 * and decrypts it when retrieved. The bean is loaded only when
 * {@code application.service.vault.encryption.enabled} is {@code true} and
 * {@code application.service.vault.storage.impl} equals {@code "gridfs"}.
 */
@Repository
@ConditionalOnExpression(
        "${application.service.vault.encryption.enabled}.equals(true) and '${application.service.vault.storage.impl}'.equals('gridfs')"
)
public class EncryptedGridFsRepositoryImpl extends GridFsRepositoryImpl {

    /** Service providing encryption secrets for content. */
    private final SecretService secretService;
    /** Component performing the actual encryption/decryption. */
    private final CryptoService cryptoService;

    /**
     * Constructs an EncryptedGridFsRepositoryImpl with the required dependencies.
     *
     * @param gridFsTemplate   the GridFsTemplate for GridFS operations
     * @param gridFsOperations the GridFsOperations for GridFS operations
     * @param mongoTemplate    the MongoTemplate for MongoDB operations
     * @param secretService    service providing encryption material
     * @param cryptoService    the CryptoService used for encryption and decryption
     */
    public EncryptedGridFsRepositoryImpl(
            GridFsTemplate gridFsTemplate,
            GridFsOperations gridFsOperations,
            MongoTemplate mongoTemplate,
            SecretService secretService,
            CryptoService cryptoService
    ) {
        super(gridFsTemplate, gridFsOperations, mongoTemplate);
        this.secretService = secretService;
        this.cryptoService = cryptoService;
    }

    /**
     * Saves a file to GridFS after encrypting its content.
     * Marks the file metadata as encrypted.
     *
     * @param inputStream the input stream of the file content
     * @param fileName    the name of the file
     * @param contentType the MIME type of the file
     * @param metadata    additional metadata to associate with the file
     */
    @Override
    @SneakyThrows
    public void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata) {
        val secret = secretService.getSecret();
        val encryptedInputStream = cryptoService.encrypt(new AnvDigestInputStream(inputStream), secret);
        metadata.put(MetadataKeys.ENCRYPTED, String.valueOf(true));
        metadata.put(MetadataKeys.KEY_VERSION, String.valueOf(secret.getVersion()));
        super.saveFile(encryptedInputStream, fileName, contentType, metadata);
    }

    /**
     * Retrieves the content of a file from GridFS.
     * If the file is encrypted, decrypts the content before returning.
     *
     * @param file the GridFSFile to retrieve content from
     * @return an InputStream of the file content, decrypted if necessary
     */
    @Override
    @SneakyThrows
    public InputStream getFileContent(GridFSFile file) {
        if (isEncrypted(file)) {
            return cryptoService.decrypt(super.getFileContent(file));
        }
        return super.getFileContent(file);
    }

    /**
     * Computes the hash of a file's content using the specified algorithm.
     * If the file is encrypted, computes the hash on the decrypted content.
     *
     * @param uuid      the unique identifier of the file
     * @param algorithm the hash algorithm to use (e.g., SHA-256)
     * @return the computed hash as a hexadecimal string
     */
    @Override
    @SneakyThrows
    public String computeHash(String uuid, String algorithm) {
        val file = findFileById(uuid);
        if (isEncrypted(file)) {
            try (val mongoDigestInputStream = new AnvDigestInputStream(getFileContent(file), algorithm)) {
                mongoDigestInputStream.transferTo(OutputStream.nullOutputStream());
                return mongoDigestInputStream.getHash();
            }
        } else return super.computeHash(file, algorithm);
    }

    /**
     * Checks if a given GridFS file is marked as encrypted.
     *
     * @param file the GridFSFile to check
     * @return true if the file is encrypted, false otherwise
     */
    private boolean isEncrypted(GridFSFile file) {
        val metadata = file.getMetadata();
        if (metadata != null && metadata.containsKey(MetadataKeys.ENCRYPTED)) {
            return Boolean.parseBoolean(metadata.getString(MetadataKeys.ENCRYPTED));
        }
        return false;
    }

}