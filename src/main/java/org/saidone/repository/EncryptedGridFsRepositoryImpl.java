package org.saidone.repository;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.SneakyThrows;
import lombok.val;
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
        InputStream streamToStore = inputStream;
        streamToStore = cryptoService.encrypt(inputStream);
        metadata.put("encrypted", "true");
        super.saveFile(streamToStore, fileName, contentType, metadata);
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
        if (metadata != null && metadata.containsKey("encrypted")) {
            return Boolean.parseBoolean(metadata.getString("encrypted"));
        }
        return false;
    }

    @Override
    public boolean isEncrypted(String uuid) {
        val file = findFileById(uuid);
        return isEncrypted(file);
    }

}