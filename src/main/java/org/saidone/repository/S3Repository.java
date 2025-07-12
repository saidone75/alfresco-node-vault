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

import org.alfresco.core.model.Node;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Abstraction over the minimal set of S3 operations required by the vault.
 * Implementations store and retrieve node content using the AWS SDK.
 */
public interface S3Repository {

    /**
     * Uploads a node's content to the specified S3 bucket using the node id as
     * object key.
     *
     * @param bucketName  name of the target S3 bucket
     * @param node        node whose identifier will be used as the object key
     * @param metadata
     * @param inputStream the stream providing the content to store
     */
    void putObject(String bucketName, Node node, HashMap<String, String> metadata, InputStream inputStream);

    /**
     * Retrieves an object from S3 previously stored for the given node id.
     *
     * @param bucketName the bucket where the object resides
     * @param nodeId     identifier of the node / object key
     * @return an {@link InputStream} for the object's content
     */
    InputStream getObject(String bucketName, String nodeId);

}
