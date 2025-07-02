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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.model.MetadataKeys;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.Map;

/**
 * Default {@link S3Repository} implementation relying on the AWS SDK
 * {@link S3Client}. The class exposes basic methods to upload and download
 * objects using a provided {@code S3Client} instance.
 */
@RequiredArgsConstructor
//@Service
@Slf4j
public class S3RepositoryImpl implements S3Repository {

    /**
     * AWS S3 client used to perform the requests. It is injected by Spring and
     * expected to be thread-safe.
     */
    protected final S3Client s3Client;

    /**
     * Uploads the provided stream as an object to S3. The node id is used as
     * the object key.
     *
     * @param bucketName  destination bucket
     * @param node        node whose id acts as the key
     * @param metadata    metadata key/value pairs to associate with the object
     * @param inputStream stream of the content to store
     */
    @Override
    public void putObject(String bucketName, Node node, Map<String, String> metadata, InputStream inputStream) {
        val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(node.getId())
                .metadata(metadata)
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromContentProvider(ContentStreamProvider.fromInputStream(inputStream), node.getContent().getMimeType()));
    }

    /**
     * Retrieves the object content for the given node id. This default
     * implementation returns {@code null} as it is expected to be overridden by
     * concrete subclasses.
     *
     * @param bucketName bucket containing the object
     * @param nodeId     the node id / object key
     * @return the object content stream or {@code null}
     */
    @Override
    public InputStream getObject(String bucketName, String nodeId) {
        
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName).key(nodeId).build());
    }

    private boolean isEncrypted(String bucketName, String nodeId) {
        val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(nodeId)
                .build();
        val metadata = s3Client.headObject(headObjectRequest).metadata();
        return Boolean.parseBoolean(metadata.getOrDefault(MetadataKeys.ENCRYPTED, Boolean.FALSE.toString()));
    }

}
