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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties used to access the Alfresco repository.
 * <p>
 * These values configure how nodes are fetched and deleted when
 * interacting with Alfresco.
 */
@Configuration
@ConfigurationProperties(prefix = "application.service.alfresco")
@Data
public class AlfrescoServiceConfig {

    /** Number of nodes retrieved per REST request. */
    private int searchBatchSize;
    /** Whether nodes should be removed from Alfresco instead of archived. */
    private boolean permanentlyDeleteNodes;
    /** List of additional properties to include in the Alfresco query. */
    private List<String> include;

}
