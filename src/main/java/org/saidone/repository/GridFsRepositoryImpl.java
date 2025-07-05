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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
 * Implements the {@link GridFsRepository} interface, enabling file storage,
 * retrieval, metadata updates and file deletion in the GridFS storage system.
 * Utilizes Spring Data's {@code GridFsTemplate}, {@code GridFsOperations}, and
 * {@code MongoTemplate} for interactions with MongoDB and GridFS collections.
 * <p>
 * On initialization, an ascending index is automatically created on the
 * {@code metadata.uuid} field in the {@code fs.files} collection to improve
 * query performance for operations that search files by UUID.
 * <p>
 * Supports the following main operations:
 * <ul>
 *   <li>Store files with a name, content type and associated metadata.</li>
 *   <li>Update the metadata of an existing file identified by a unique
 *   {@code uuid}.</li>
 *   <li>Retrieve a single file from GridFS by its {@code uuid}, returning a
 *   {@link GridFSFile}.</li>
 *   <li>Delete a file from GridFS using its {@code uuid} as key.</li>
 *   <li>Provide file content streams via {@link InputStream} for efficient
 *   reading of file contents.</li>
 *   <li>Compute cryptographic hash values of files using MongoDB commands, with
 *   the hash algorithm selectable at runtime.</li>
 * </ul>
 * <p>
 * This implementation is registered in the Spring context only if the property
 * {@code application.service.vault.encryption.enabled} is set to {@code false}
 * or is not defined.
 * <p>
 * Extends {@link org.saidone.component.BaseComponent} to inherit standardized
 * component lifecycle logging.
 * <p>
 * Thread safety is guaranteed by relying on the thread-safe beans of the
 * Spring container for all MongoDB operations.
 */
@Repository
@ConditionalOnExpression(
        "${application.service.vault.encryption.enabled}.equals(false) and '${application.service.vault.storage.impl}'.equals('gridfs')"
)
@RequiredArgsConstructor
@Slf4j
public class GridFsRepositoryImpl extends BaseComponent implements GridFsRepository {

    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;
    private final MongoTemplate mongoTemplate;

    /**
     * Initializes the component by creating an index on the
     * {@code metadata.uuid} field of the {@code fs.files} collection. This
     * ensures fast lookups when retrieving files by UUID.
     * <p>
     * If the index creation fails the component is shut down.
     */
    @PostConstruct
    public void init() {
        super.init();
        try {
            val indexOps = mongoTemplate.indexOps("fs.files");
            val index = new Index().on("metadata.uuid", Sort.Direction.ASC).named("metadata_uuid_index");
            indexOps.createIndex(index);
        } catch (Exception e) {
            log.error("Unable to start {}", this.getClass().getSimpleName());
            super.shutDown(0);
        }
    }

    /**
     * Stores a new file in GridFS.
     *
     * @param inputStream the stream containing the file content
     * @param fileName    name of the file to store
     * @param contentType MIME type of the file
     * @param metadata    additional metadata to associate with the file
     */
    @Override
    public void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata) {
        gridFsTemplate.store(
                inputStream,
                fileName,
                contentType,
                new Document(metadata)
        );
    }

    /**
     * Updates the metadata document of a stored file.
     *
     * @param uuid     unique identifier of the file
     * @param metadata key/value pairs to merge into the existing metadata
     */
    @Override
    public void updateFileMetadata(String uuid, Map<String, String> metadata) {
        val query = new Query(Criteria.where("metadata.uuid").is(uuid));
        val update = new Update();
        metadata.forEach((key, value) -> update.set(String.format("metadata.%s", key), value));
        mongoTemplate.updateFirst(query, update, "fs.files");
    }

    /**
     * Retrieves a file descriptor by UUID.
     *
     * @param uuid unique identifier of the file
     * @return the matching {@link GridFSFile} or {@code null} if not found
     */
    @Override
    public GridFSFile findFileById(String uuid) {
        val query = new Query(Criteria.where("metadata.uuid").is(uuid));
        return gridFsTemplate.findOne(query);
    }

    /**
     * Deletes a file and its metadata from GridFS by UUID.
     *
     * @param uuid unique identifier of the file
     */
    @Override
    public void deleteFileById(String uuid) {
        val query = new Query(Criteria.where("metadata.uuid").is(uuid));
        gridFsTemplate.delete(query);
    }

    /**
     * Returns an input stream for the given GridFS file.
     *
     * @param file the file retrieved from GridFS
     * @return input stream positioned at the beginning of the file or
     * {@code null} if {@code file} is {@code null}
     */
    @SneakyThrows
    public InputStream getFileContent(GridFSFile file) {
        if (file != null) {
            return gridFsOperations.getResource(file).getInputStream();
        }
        return null;
    }

    /**
     * Computes the cryptographic hash of the provided GridFS file using the
     * specified algorithm.
     *
     * @param file      the file whose hash should be calculated
     * @param algorithm name of the hash algorithm supported by MongoDB
     * @return the hexadecimal encoded hash value
     */
    protected String computeHash(GridFSFile file, String algorithm) {
        val command = new Document(String.format("file%s", algorithm.toLowerCase()), file.getId()).append("root", "fs");
        val result = mongoTemplate.executeCommand(command);
        return result.getString(algorithm.toLowerCase());
    }

    /**
     * Computes the hash of a file identified by its UUID.
     *
     * @param uuid      unique identifier of the file
     * @param algorithm name of the hash algorithm supported by MongoDB
     * @return the hexadecimal encoded hash value
     */
    @Override
    public String computeHash(String uuid, String algorithm) {
        return computeHash(findFileById(uuid), algorithm);
    }

}