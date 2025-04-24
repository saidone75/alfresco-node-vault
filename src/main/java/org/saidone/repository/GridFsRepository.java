package org.saidone.repository;

import com.mongodb.client.gridfs.model.GridFSFile;

import java.io.InputStream;
import java.util.Map;

public interface GridFsRepository {

    void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata);

    GridFSFile findFileById(String uuid);

    void deleteFileById(String uuid);

}