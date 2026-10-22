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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.component.BaseComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * Default {@link S3Repository} implementation relying on the AWS SDK
 * {@link S3Client}. The class exposes basic methods to upload and download
 * objects using a provided {@code S3Client} instance.
 * <p>
 * The bean is created only when {@code application.service.vault.encryption.content.enabled}
 * is set to {@code false} and {@code application.service.vault.storage.impl}
 * equals {@code "s3"}.
 */
@Service
@ConditionalOnExpression(
        "${application.service.vault.encryption.content.enabled}.equals(false) and '${application.service.vault.storage.impl}'.equals('s3')"
)
@RequiredArgsConstructor
@Slf4j
public class S3RepositoryImpl extends BaseComponent implements S3Repository {

    /**
     * AWS S3 client used to perform the requests. It is injected by Spring and
     * expected to be thread-safe.
     */
    protected final S3Client s3Client;

    /**
     * Returns the provided content unchanged: plain content is stored as is.
     *
     * @param content  the original content
     * @param metadata object metadata, left unchanged
     * @return the provided content stream
     */
    @Override
    public InputStream prepareContent(InputStream content, HashMap<String, String> metadata) {
        return content;
    }

    /**
     * Uploads the provided file as an object to S3. The node id is used as
     * the object key.
     *
     * @param bucketName destination bucket
     * @param node       node whose id acts as the key
     * @param metadata   optional object metadata to associate with the stored
     *                   content
     * @param content    file holding the content to store
     */
    @Override
    public void putObject(String bucketName, Node node, HashMap<String, String> metadata, Path content) {
        val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(node.getId())
                .contentType(node.getContent().getMimeType())
                .metadata(metadata)
                .build();
        // A file body has a known length and can be re-read on retries, so the SDK never buffers it in memory.
        s3Client.putObject(putObjectRequest, RequestBody.fromFile(content));
    }

    /**
     * Retrieves the object content for the given node id using the underlying
     * {@link S3Client}.
     *
     * @param bucketName bucket containing the object
     * @param nodeId     the node id / object key
     * @return the object content stream
     */
    @Override
    public InputStream getObject(String bucketName, String nodeId) {
        return getObjectStream(bucketName, nodeId);
    }

    /**
     * Retrieves the raw object content for the given node id, exposing the
     * object metadata through {@link ResponseInputStream#response()}.
     *
     * @param bucketName bucket containing the object
     * @param nodeId     the node id / object key
     * @return the object content stream along with its response metadata
     */
    protected ResponseInputStream<GetObjectResponse> getObjectStream(String bucketName, String nodeId) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName).key(nodeId).build());
    }

}
