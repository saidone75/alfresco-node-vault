/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.repository;

import org.alfresco.core.model.Node;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.file.Path;
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
     * @param metadata    optional object metadata to store alongside the binary
     *                    content
     * @param content     file holding the bytes to store; it is streamed from
     *                    disk with its exact length, so the content is never
     *                    buffered in memory
     */
    void putObject(String bucketName, Node node, HashMap<String, String> metadata, Path content);

    /**
     * Retrieves the stored bytes of an object previously stored for the given
     * node id.
     *
     * @param bucketName the bucket where the object resides
     * @param nodeId     identifier of the node / object key
     * @return the object's stored bytes, with its metadata available through
     * {@link ResponseInputStream#response()}
     */
    ResponseInputStream<GetObjectResponse> getObject(String bucketName, String nodeId);

}
