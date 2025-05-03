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

import java.io.InputStream;
import java.util.Map;

public interface GridFsRepository {

    void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata);

    void updateFileMetadata(String uuid, Map<String, String> metadata);

    GridFSFile findFileById(String uuid);

    void deleteFileById(String uuid);

}