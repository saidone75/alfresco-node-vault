package org.saidone.repository;

import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
        var indexOps = mongoTemplate.indexOps("fs.files");
        var index = new Index().on("metadata.uuid", Sort.Direction.ASC).named("metadata_uuid_index");
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
        var query = new Query(Criteria.where("metadata.uuid").is(uuid));
        return gridFsTemplate.findOne(query);
    }

    @Override
    public void deleteFileById(String uuid) {
        var query = new Query(Criteria.where("metadata.uuid").is(uuid));
        gridFsTemplate.delete(query);
    }

    @SneakyThrows
    public InputStream getFileContent(GridFSFile file) {
        if (file != null) {
            return gridFsOperations.getResource(file).getInputStream();
        }
        return null;
    }

    public String calculateMd5(String uuid) {
        var command = new Document("filemd5", findFileById(uuid).getId()).append("root", "fs");
        var result = mongoTemplate.executeCommand(command);
        return result.getString(doubleCheckAlgorithm.toLowerCase());
    }

}