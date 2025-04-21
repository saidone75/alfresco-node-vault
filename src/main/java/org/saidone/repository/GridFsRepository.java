package org.saidone.repository;

import com.mongodb.client.gridfs.model.GridFSFile;

import java.io.InputStream;

public interface GridFsRepository {

    void saveFile(String uuid, InputStream fileStream, String fileName, String contentType);

    GridFSFile findFileById(String uuid);

    void deleteFileById(String uuid);

}