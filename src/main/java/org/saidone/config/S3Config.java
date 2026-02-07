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

package org.saidone.config;

import lombok.Data;
import lombok.val;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configuration for the Amazon S3 client used as storage backend.
 */
@Configuration
@ConfigurationProperties(prefix = "application.service.vault.storage.s3")
@Data
public class S3Config {

    private String key;
    private String secret;
    private String bucket;
    private String region;
    private String endpoint;

    /**
     * Builds the Amazon S3 client used as storage backend.
     *
     * <p>If a custom endpoint is provided, it is configured and basic
     * credentials are set when the endpoint targets localhost.</p>
     *
     * @return configured {@link S3Client}
     */
    @Bean
    public S3Client s3Client() {
        val builder = S3Client.builder().region(Region.of(region)).forcePathStyle(true);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            if (endpoint.contains("localhost")) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(key, secret)));
            }
        }
        return builder.build();
    }

}
