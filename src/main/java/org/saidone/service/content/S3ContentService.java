/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

package org.saidone.service.content;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.component.BaseComponent;
import org.saidone.config.S3Config;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.exception.VaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeContentInfo;
import org.saidone.model.NodeContentStream;
import org.saidone.repository.S3Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * {@link ContentService} implementation that stores node binaries in Amazon S3.
 * <p>
 * During archival the content stream is uploaded while a checksum is computed on the
 * fly. The resulting hash and other metadata are stored as object metadata. Retrieval
 * operations return a {@link NodeContentStream} descriptor using the AWS SDK.
 * </p>
 * <p>
 * This service is enabled when {@code application.service.vault.storage.impl} is set to
 * {@code s3}.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConfigurationProperties(prefix = "application.service.vault.storage")
@ConditionalOnExpression("'${application.service.vault.storage.impl:}' == 's3'")
public class S3ContentService extends BaseComponent implements ContentService {

    @Value("${application.service.vault.encryption.enabled}")
    private boolean encryptionEnabled;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final S3Repository s3Repository;

    private final S3Client s3Client;
    private final S3Config s3Config;

    /**
     * Saves the content stream of the given node to S3. The method computes the
     * checksum on the fly and stores it as object metadata.
     *
     * @param node        node whose content is being archived
     * @param inputStream input stream providing the node content
     */
    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        val metadata = new HashMap<String, String>();
        metadata.put(MetadataKeys.UUID, node.getId());
        metadata.put(MetadataKeys.FILENAME, node.getName());
        metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
        try (val digestInputStream = new AnvDigestInputStream(inputStream, checksumAlgorithm)) {
            s3Repository.putObject(s3Config.getBucket(), node, metadata, digestInputStream);
            val hash = digestInputStream.getHash();
            log.trace("{}: {}", checksumAlgorithm, hash);
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.CHECKSUM_VALUE, hash);
            val copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(s3Config.getBucket())
                    .sourceKey(node.getId())
                    .destinationBucket(s3Config.getBucket())
                    .destinationKey(node.getId())
                    .metadata(metadata)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(node.getContent().getMimeType())
                    .build();
            s3Client.copyObject(copyRequest);
        }
    }

    /**
     * Retrieves the content of a node from S3.
     *
     * @param nodeId identifier of the node
     * @return descriptor containing file name, content type and the data stream
     * @throws NodeNotFoundOnVaultException if the object is not found
     */
    @Override
    public NodeContentStream getNodeContent(String nodeId) {
        try {
            val head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            val nodeContent = new NodeContentStream();
            nodeContent.setFileName(head.metadata().getOrDefault(MetadataKeys.FILENAME, nodeId));
            nodeContent.setContentType(head.contentType());
            nodeContent.setLength(head.contentLength());
            nodeContent.setContentStream(s3Repository.getObject(s3Config.getBucket(), nodeId));
            return nodeContent;
        } catch (S3Exception e) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    /**
     * Retrieves only the metadata of a node's content stored in S3 without
     * streaming the actual binary.
     *
     * @param nodeId identifier of the node
     * @return a populated {@link NodeContentInfo}
     * @throws NodeNotFoundOnVaultException if the object does not exist in S3
     */
    @Override
    public NodeContentInfo getNodeContentInfo(String nodeId) {
        try {
            val head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            val nodeContentInfo = new NodeContentInfo();
            nodeContentInfo.setFileName(head.metadata().getOrDefault(MetadataKeys.FILENAME, nodeId));
            nodeContentInfo.setContentType(head.contentType());
            nodeContentInfo.setContentId(nodeId);
            nodeContentInfo.setContentHashAlgorithm(head.metadata().get(MetadataKeys.CHECKSUM_ALGORITHM));
            nodeContentInfo.setContentHash(head.metadata().get(MetadataKeys.CHECKSUM_VALUE));
            return nodeContentInfo;
        } catch (S3Exception e) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    /**
     * Removes the stored object associated with the given node id.
     */
    @Override
    public void deleteNodeContent(String nodeId) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Config.getBucket()).key(nodeId).build());
    }

    /**
     * Computes the checksum of a stored object using the supplied algorithm.
     *
     * @param nodeId    identifier of the node
     * @param algorithm name of the hash algorithm
     * @return hexadecimal encoded hash string
     * @throws NodeNotFoundOnVaultException if the node cannot be found
     */
    @Override
    @SneakyThrows
    public String computeHash(String nodeId, String algorithm) {
        try {
            try (val dis = new AnvDigestInputStream(s3Repository.getObject(s3Config.getBucket(), nodeId), algorithm)) {
                dis.transferTo(OutputStream.nullOutputStream());
                return dis.getHash();
            }
        } catch (S3Exception e) {
            throw new VaultException(nodeId);
        }
    }

    @Override
    @PreDestroy
    public void stop() {
        s3Client.close();
        super.stop();
    }

}