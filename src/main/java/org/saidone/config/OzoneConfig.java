/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.client.OzoneClient;
import org.apache.hadoop.ozone.client.OzoneClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties and bean factory for the native Ozone RPC client.
 * <p>
 * Properties are bound from {@code application.service.vault.storage.ozone.*}.
 * The configured volume and bucket must already exist.
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "application.service.vault.storage.ozone")
@ConditionalOnProperty(name = "application.service.vault.storage.impl", havingValue = "ozone")
public class OzoneConfig {

    /** Volume containing the vault content bucket. */
    private String volume = "anv";
    /** Bucket used to store node binaries. */
    private String bucket = "content";
    /** Ozone/Hadoop properties, including OM HA and security configuration. */
    private Map<String, String> properties = new HashMap<>();

    /**
     * Builds the RPC client using classpath configuration and property overrides.
     *
     * @return client closed automatically when the application shuts down
     * @throws IOException if the client cannot be initialized
     */
    @Bean(destroyMethod = "close")
    public OzoneClient ozoneClient() throws IOException {
        val configuration = new OzoneConfiguration();
        properties.forEach(configuration::set);
        return OzoneClientFactory.getRpcClient(configuration);
    }

    /**
     * Resolves the existing bucket and its replication configuration.
     *
     * @param client native Ozone client
     * @return bucket used for vault content
     * @throws IOException if the volume or bucket cannot be accessed
     */
    @Bean
    public OzoneBucket ozoneBucket(OzoneClient client) throws IOException {
        return client.getObjectStore().getVolume(volume).getBucket(bucket);
    }
}
