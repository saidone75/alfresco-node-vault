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

import lombok.val;
import org.saidone.model.MetadataKeys;
import org.saidone.service.crypto.CryptoService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.HashMap;

@Service
public class EncryptedS3RepositoryImpl extends S3RepositoryImpl {

    private final CryptoService cryptoService;

    public EncryptedS3RepositoryImpl(S3Client s3Client, CryptoService cryptoService) {
        super(s3Client);
        this.cryptoService = cryptoService;
    }

    @Override
    public void putObject(InputStream inputStream, String bucketName, String nodeId) {
        super.putObject(cryptoService.encrypt(inputStream), bucketName, nodeId);
    }

    @Override
    public InputStream getObject(String bucketName, String nodeId) {
        return cryptoService.decrypt(s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName).key(nodeId).build()));
    }

}