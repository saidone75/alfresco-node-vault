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

package org.saidone.service.content;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.config.S3Config;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeContent;
import org.saidone.repository.S3Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
@ConfigurationProperties(prefix = "application.service.vault.storage")
@ConditionalOnExpression("'${application.service.vault.storage.impl:}' == 's3'")
public class S3ContentService implements ContentService {

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final S3Repository s3Repository;

    private final S3Client s3Client;
    private final S3Config s3Config;

    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        val metadata = new HashMap<String, String>();
        metadata.put(MetadataKeys.UUID, node.getId());
        metadata.put(MetadataKeys.FILENAME, node.getName());
        metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
        try (val digestInputStream = new AnvDigestInputStream(inputStream, checksumAlgorithm)) {
            s3Repository.putObject(digestInputStream, s3Config.getBucket(), node.getId());
            val hash = digestInputStream.getHash();
            log.trace("{}: {}", checksumAlgorithm, hash);
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.CHECKSUM_VALUE, hash);
            val copyRequest = CopyObjectRequest.builder()
                    .copySource(s3Config.getBucket() + "/" + node.getId())
                    .bucket(s3Config.getBucket())
                    .key(node.getId())
                    .metadata(metadata)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(node.getContent().getMimeType())
                    .build();
            s3Client.copyObject(copyRequest);
        }
    }

    @Override
    public NodeContent getNodeContent(String nodeId) {
        try {
            val head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            val object = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            val nodeContent = new NodeContent();
            nodeContent.setFileName(head.metadata().getOrDefault("filename", nodeId));
            nodeContent.setContentType(head.contentType());
            nodeContent.setLength(head.contentLength());
            nodeContent.setContentStream(object);
            return nodeContent;
        } catch (S3Exception e) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    @Override
    public void deleteFileById(String nodeId) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Config.getBucket()).key(nodeId).build());
    }

    @Override
    @SneakyThrows
    public String computeHash(String nodeId, String algorithm) {
        try {
            try (val dis = new AnvDigestInputStream(s3Repository.getObject(s3Config.getBucket(), nodeId), algorithm)) {
                dis.transferTo(OutputStream.nullOutputStream());
                return dis.getHash();
            }
        } catch (S3Exception e) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

}