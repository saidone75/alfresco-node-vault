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
import org.saidone.component.BaseComponent;
import org.saidone.config.S3Config;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeContent;
import org.saidone.service.crypto.CryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConfigurationProperties(prefix = "application.service.vault.storage")
@ConditionalOnExpression("'${application.service.vault.storage.impl:}' == 's3'")
public class S3ContentService extends BaseComponent implements ContentService {

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final S3Client s3Client;
    private final S3Config s3Config;

    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        val metadata = new HashMap<String, String>();
        metadata.put(MetadataKeys.UUID, node.getId());
        metadata.put(MetadataKeys.FILENAME, node.getName());
        metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());

        var tempFile = (File) null;

        try {
            // 1. Crea un file temporaneo
            tempFile = File.createTempFile("upload-", ".tmp");

            // 2. Scrive lo stream sul file e calcola l'hash in una passata
            String hash;
            try (OutputStream out = new FileOutputStream(tempFile);
                 AnvDigestInputStream digestStream = new AnvDigestInputStream(inputStream, checksumAlgorithm)) {

                long copied = digestStream.transferTo(out);
                hash = digestStream.getHash();
                log.trace("{}: {}", checksumAlgorithm, hash);

                metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
                metadata.put(MetadataKeys.CHECKSUM_VALUE, hash);
            }

            // 3. Prepara la richiesta S3 con lunghezza
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(node.getId())
                    .contentType(node.getContent().getMimeType())
                    .metadata(metadata)
                    .build();

            try (InputStream fileInputStream = new FileInputStream(tempFile)) {
                s3Client.putObject(putRequest, RequestBody.fromInputStream(fileInputStream, tempFile.length()));
            }

            // 4. Copia con metadati aggiornati

            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .copySource(s3Config.getBucket() + "/" + node.getId())
                    .bucket(s3Config.getBucket())
                    .key(node.getId())
                    .metadata(metadata)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(node.getContent().getMimeType())
                    .build();



            s3Client.copyObject(copyRequest);



        } catch (IOException e) {
            throw new UncheckedIOException("Error archiving node content", e);
        } finally {
            // 5. Pulisce il file temporaneo
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Could not delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
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
