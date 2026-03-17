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
import lombok.EqualsAndHashCode;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configuration properties and bean factory for the Amazon S3 client used by the vault storage backend.
 *
 * <p>Properties are bound from the {@code application.service.vault.storage.s3.*} namespace.</p>
 */
@EqualsAndHashCode(callSuper = true)
@Configuration
@ConfigurationProperties(prefix = "application.service.vault.storage.s3")
@Data
public class S3Config extends BaseComponent {

    /** Access key used when static credentials are configured. */
    private String key;

    /** Secret key used when static credentials are configured. */
    private String secret;

    /** Target bucket name for vault object storage. */
    private String bucket;

    /** AWS region identifier (for example {@code eu-west-1}). */
    private String region;

    /** Optional custom S3-compatible endpoint URI. */
    private String endpoint;

    /**
     * Builds the Amazon S3 client used as storage backend.
     *
     * <p>If a custom endpoint is provided, it is applied through
     * {@link software.amazon.awssdk.services.s3.S3ClientBuilder#endpointOverride(URI)} and static
     * credentials built from {@link #key} and {@link #secret} are configured.</p>
     *
     * @return configured {@link S3Client}
     */
    @Bean
    public S3Client s3Client() {
        val builder = S3Client.builder().region(Region.of(region)).forcePathStyle(true);
        if (Strings.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(key, secret)));
        }
        return builder.build();
    }

}
