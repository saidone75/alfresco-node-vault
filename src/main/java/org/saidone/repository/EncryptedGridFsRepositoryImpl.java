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

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.SneakyThrows;
import lombok.val;
import org.saidone.model.MetadataKeys;
import org.saidone.service.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "application.service.vault.encryption.enabled", havingValue = "true")
public class EncryptedGridFsRepositoryImpl extends GridFsRepositoryImpl {

    private final CryptoService cryptoService;

    public EncryptedGridFsRepositoryImpl(
            GridFsTemplate gridFsTemplate,
            GridFsOperations gridFsOperations,
            MongoTemplate mongoTemplate,
            CryptoService cryptoService
    ) {
        super(gridFsTemplate, gridFsOperations, mongoTemplate);
        this.cryptoService = cryptoService;
    }

    @Override
    public void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata) {
        val encryptedInputStream = cryptoService.encrypt(inputStream);
        metadata.put(MetadataKeys.ENCRYPTED, String.valueOf(true));
        super.saveFile(encryptedInputStream, fileName, contentType, metadata);
    }

    @Override
    @SneakyThrows
    public InputStream getFileContent(GridFSFile file) {
        if (isEncrypted(file)) {
            return cryptoService.decrypt(super.getFileContent(file));
        }
        return super.getFileContent(file);
    }

    private boolean isEncrypted(GridFSFile file) {
        val metadata = file.getMetadata();
        if (metadata != null && metadata.containsKey(MetadataKeys.ENCRYPTED)) {
            return Boolean.parseBoolean(metadata.getString(MetadataKeys.ENCRYPTED));
        }
        return false;
    }

    @Override
    public boolean isEncrypted(String uuid) {
        val file = findFileById(uuid);
        return isEncrypted(file);
    }

}