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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.Document;
import org.saidone.component.BaseComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Map;

/**
 * Repository implementation for the management of files in MongoDB's GridFS
 * without encryption support.
 * <p>
 * Implements the {@link GridFsRepository} interface, enabling file storage, retrieval,
 * metadata updates and file deletion in the GridFS storage system.
 * Utilizes Spring Data's {@code GridFsTemplate}, {@code GridFsOperations}, and {@code MongoTemplate}
 * for interactions with MongoDB and GridFS collections.
 * <p>
 * On initialization, an ascending index is automatically created on the
 * {@code metadata.uuid} field in the {@code fs.files} collection to improve query
 * performance for operations that search files by UUID.
 * <p>
 * Supports the following main operations:
 * - Stores files with a name, content type, and associated metadata.
 * - Updates the metadata of an existing file identified by a unique {@code uuid}.
 * - Retrieves a single file from GridFS by its {@code uuid}, returning a {@link GridFSFile}.
 * - Deletes a file from GridFS using its {@code uuid} as key.
 * - Provides file content streams via {@link InputStream} for efficient reading of file contents.
 * - Computes cryptographic hash values of files using MongoDB commands, with the hash algorithm selectable at runtime.
 * <p>
 * This implementation is registered in the Spring context only if the property
 * {@code application.service.vault.encryption.enabled} is set to {@code false} or is not defined.
 * <p>
 * Extends {@link org.saidone.component.BaseComponent} to inherit standardized component lifecycle logging.
 * <p>
 * Thread safety is guaranteed by relying on the thread-safe beans of the Spring container for all MongoDB operations.
 */
@Repository
@ConditionalOnProperty(name = "application.service.vault.encryption.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GridFsRepositoryImpl extends BaseComponent implements GridFsRepository {

    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;
    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void init() {
        super.init();
        try {
            val indexOps = mongoTemplate.indexOps("fs.files");
            val index = new Index().on("metadata.uuid", Sort.Direction.ASC).named("metadata_uuid_index");
            indexOps.ensureIndex(index);
        } catch (Exception e) {
            log.error("Unable to start {}", this.getClass().getSimpleName());
            super.shutDown(0);
        }
    }

    @Override
    public void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata) {
        gridFsTemplate.store(
                inputStream,
                fileName,
                contentType,
                new Document(metadata)
        );
    }

    @Override
    public void updateFileMetadata(String uuid, Map<String, String> metadata) {
        val query = new Query(Criteria.where("metadata.uuid").is(uuid));
        val update = new Update();
        metadata.forEach((key, value) -> update.set(String.format("metadata.%s", key), value));
        mongoTemplate.updateFirst(query, update, "fs.files");
    }

    @Override
    public GridFSFile findFileById(String uuid) {
        val query = new Query(Criteria.where("metadata.uuid").is(uuid));
        return gridFsTemplate.findOne(query);
    }

    @Override
    public void deleteFileById(String uuid) {
        val query = new Query(Criteria.where("metadata.uuid").is(uuid));
        gridFsTemplate.delete(query);
    }

    @SneakyThrows
    public InputStream getFileContent(GridFSFile file) {
        if (file != null) {
            return gridFsOperations.getResource(file).getInputStream();
        }
        return null;
    }

    protected String computeHash(GridFSFile file, String algorithm) {
        val command = new Document(String.format("file%s", algorithm.toLowerCase()), file.getId()).append("root", "fs");
        val result = mongoTemplate.executeCommand(command);
        return result.getString(algorithm.toLowerCase());
    }

    @Override
    public String computeHash(String uuid, String algorithm) {
        return computeHash(findFileById(uuid), algorithm);
    }

}