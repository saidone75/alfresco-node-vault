package org.saidone.repository;

import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Repository;

import java.io.InputStream;

@RequiredArgsConstructor
@Repository
public class GridFsRepositoryImpl implements GridFsRepository {

    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;
    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void init() {
        var indexOps = mongoTemplate.indexOps("fs.files");
        var index = new Index().on("metadata.uuid", Sort.Direction.ASC).named("metadata_uuid_index");
        indexOps.ensureIndex(index);
    }

    @Override
    public void saveFile(String uuid, InputStream fileStream, String fileName, String contentType) {
        var metadata = new Document();
        metadata.put("uuid", uuid);
        gridFsTemplate.store(
                fileStream,
                fileName,
                contentType,
                metadata
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

}