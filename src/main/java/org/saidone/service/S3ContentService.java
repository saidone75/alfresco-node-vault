package org.saidone.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class S3ContentService implements ContentService {

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final S3Client s3Client;
    private final S3Config s3Config;

    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(MetadataKeys.UUID, node.getId());
        metadata.put("filename", node.getName());
        metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
        try (val digestInputStream = new AnvDigestInputStream(inputStream, checksumAlgorithm)) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(node.getId())
                    .contentType(node.getContent().getMimeType())
                    .metadata(metadata)
                    .build();
            s3Client.putObject(putRequest,
                    RequestBody.fromInputStream(digestInputStream, node.getContent().getSizeInBytes()));
            String hash = digestInputStream.getHash();
            log.trace("{}: {}", checksumAlgorithm, hash);
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.CHECKSUM_VALUE, hash);
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
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
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            NodeContent nodeContent = new NodeContent();
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
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build());
            Map<String, String> meta = head.metadata();
            if (meta != null) {
                String storedAlg = meta.get(MetadataKeys.CHECKSUM_ALGORITHM);
                String storedVal = meta.get(MetadataKeys.CHECKSUM_VALUE);
                if (storedAlg != null && storedAlg.equalsIgnoreCase(algorithm) && storedVal != null) {
                    return storedVal;
                }
            }
            try (val dis = new AnvDigestInputStream(s3Client.getObject(GetObjectRequest.builder()
                    .bucket(s3Config.getBucket()).key(nodeId).build()), algorithm)) {
                dis.transferTo(OutputStream.nullOutputStream());
                return dis.getHash();
            }
        } catch (S3Exception e) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }
}
