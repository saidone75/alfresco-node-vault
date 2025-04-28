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
import lombok.val;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Map;

@RequiredArgsConstructor
@Repository
public class GridFsRepositoryImpl implements GridFsRepository {

    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;
    private final MongoTemplate mongoTemplate;

    @Value("${application.service.vault.double-check-algorithm}")
    private String doubleCheckAlgorithm;

    @PostConstruct
    public void init() {
        val indexOps = mongoTemplate.indexOps("fs.files");
        val index = new Index().on("metadata.uuid", Sort.Direction.ASC).named("metadata_uuid_index");
        indexOps.ensureIndex(index);
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

    public String computeDigest(String uuid, String algorithm) {
        val command = new Document(String.format("file%s", algorithm.toLowerCase()), findFileById(uuid).getId()).append("root", "fs");
        val result = mongoTemplate.executeCommand(command);
        return result.getString(doubleCheckAlgorithm.toLowerCase());
    }

}