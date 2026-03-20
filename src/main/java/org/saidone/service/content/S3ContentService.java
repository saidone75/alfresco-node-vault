/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
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
import lombok.Cleanup;
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
import java.nio.file.Files;
import java.util.HashMap;

/**
 * {@link ContentService} implementation that stores node binaries in Amazon S3.
 * <p>
 * During archival the content stream is written to a temporary file so the checksum can
 * be computed before uploading. The resulting hash and other metadata are persisted as
 * S3 user metadata. Retrieval operations return {@link NodeContentStream} and
 * {@link NodeContentInfo} descriptors built from {@code HeadObject} and {@code GetObject}
 * responses.
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

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final S3Repository s3Repository;

    private final S3Client s3Client;
    private final S3Config s3Config;

    /**
     * Saves the content stream of the given node to S3.
     * <p>
     * The method computes the checksum while copying the payload to a temporary file,
     * then uploads that file with Alfresco metadata (UUID, file name, MIME type,
     * checksum algorithm and checksum value).
     * </p>
     *
     * @param node        node whose content is being archived
     * @param inputStream input stream providing the node content
     */
    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        @Cleanup("delete") val tempFile = Files.createTempFile("s3-upload-", ".bin").toFile();
        try (val dis = new AnvDigestInputStream(inputStream, checksumAlgorithm);
             val fos = Files.newOutputStream(tempFile.toPath());
             val fis = Files.newInputStream(tempFile.toPath())) {
            dis.transferTo(fos);
            fos.flush();
            log.trace("{}: {}", checksumAlgorithm, dis.getHash());
            val metadata = new HashMap<String, String>();
            metadata.put(MetadataKeys.UUID, node.getId());
            metadata.put(MetadataKeys.FILENAME, node.getName());
            metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.CHECKSUM_VALUE, dis.getHash());
            s3Repository.putObject(s3Config.getBucket(), node, metadata, fis);
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
            val nodeContentStream = new NodeContentStream();
            nodeContentStream.setFileName(head.metadata().getOrDefault(MetadataKeys.FILENAME, nodeId));
            nodeContentStream.setContentType(head.contentType());
            nodeContentStream.setLength(head.contentLength());
            nodeContentStream.setContentStream(s3Repository.getObject(s3Config.getBucket(), nodeId));
            return nodeContentStream;
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
     *
     * @param nodeId identifier of the node
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
     * @throws VaultException if the object cannot be read from the vault
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
