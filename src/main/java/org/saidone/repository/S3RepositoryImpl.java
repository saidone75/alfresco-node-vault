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

import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.model.MetadataKeys;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.InputStream;
import java.util.HashMap;

@RequiredArgsConstructor
//@Service
@Slf4j
public class S3RepositoryImpl implements S3Repository {

    protected final S3Client s3Client;

    @Override
    public void putObject(InputStream inputStream, String bucketName, String nodeId) {
        @Cleanup val transferManager = S3TransferManager.builder()
                .s3Client(S3AsyncClient.builder()
                        .region(Region.EU_CENTRAL_1)
                        .build())
                .build();
        try {
           val metadata = new HashMap<String, String>();
            metadata.put(MetadataKeys.ENCRYPTED, Boolean.TRUE.toString());
            val putReq = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(nodeId)
                    .metadata(metadata)
                    .build();
            val body = AsyncRequestBody.forBlockingInputStream(null);
            val uploadRequest = UploadRequest.builder()
                    .putObjectRequest(putReq)
                    .requestBody(body)
                    .build();
            val upload = transferManager.upload(uploadRequest);
            body.writeInputStream(inputStream);
            val response = upload.completionFuture().join();
            log.debug("Upload succeeded. ETag: {}", response.response().eTag());
        } catch (Exception e) {
           log.error("Error during upload: {}", e.getMessage());
        }

    }

    @Override
    public InputStream getObject(String bucketName, String nodeId) {
        // TODO implementation
        return null;
    }

}
