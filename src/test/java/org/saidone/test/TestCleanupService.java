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

package org.saidone.test;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.saidone.config.S3Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.net.URI;
import java.util.stream.Collectors;

//@Component
@RequiredArgsConstructor
@Slf4j
public class TestCleanupService {

    private final NodesApi nodesApi;
    private final MongoClient mongoClient;
    private final S3Config s3Config;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${application.service.vault.storage.s3.bucket}")
    private String bucketName;

    @Setter
    private static String parentId;

    private S3Client s3Client() {
        val builder = S3Client.builder().region(Region.of(s3Config.getRegion())).forcePathStyle(true);
        builder.endpointOverride(URI.create(s3Config.getEndpoint()));
        if (s3Config.getEndpoint().contains("localhost")) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Config.getKey(), s3Config.getSecret())));
        }
        return builder.build();
    }

    private void emptyBucket(String bucketName) {
        try (val s3Client = s3Client()) {
            var continuationToken = (String) null;
            do {
                val listRes = s3Client.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .continuationToken(continuationToken)
                                .build());
                val contents = listRes.contents();
                if (contents.isEmpty()) break;
                val toDelete = contents.stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .collect(Collectors.toList());
                val delRes = s3Client.deleteObjects(
                        DeleteObjectsRequest.builder()
                                .bucket(bucketName)
                                .delete(Delete.builder().objects(toDelete).build())
                                .build());
                log.debug("Deleted {} objects", delRes.deleted().size());
                continuationToken = listRes.nextContinuationToken();
            } while (continuationToken != null);
            log.debug("Bucket emptied");
        } catch (Exception e) {
            log.warn("Error emptying bucket during cleanup: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanUp() {
        if (parentId != null) {
            try {
                nodesApi.deleteNode(parentId, true);
            } catch (Exception e) {
                log.warn("Error deleting Alfresco node: {}", e.getMessage());
            }
            try {
                mongoClient.getDatabase(database).drop();
            } catch (Exception e) {
                log.warn("Error dropping MongoDB database: {}", e.getMessage());
            }
            emptyBucket(bucketName);
        }
    }

}