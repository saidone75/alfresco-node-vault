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

package org.saidone.test;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.saidone.repository.MongoNodeRepositoryImpl;
import org.saidone.service.content.ContentService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cleans up integration-test data using the configured content storage backend.
 * Content IDs are recorded when test nodes are created, including nodes whose
 * archival fails or whose metadata is deleted before cleanup.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
@Slf4j
public class TestCleanupService {

    private final NodesApi nodesApi;
    private final MongoNodeRepositoryImpl mongoNodeRepository;
    private final ContentService contentService;
    private final Set<String> nodeIds = ConcurrentHashMap.newKeySet();

    @Setter
    private String parentId;

    /** Records a test node before uploading or archiving its content. */
    public void registerNode(String nodeId) {
        nodeIds.add(nodeId);
    }

    /**
     * Removes the test folder and only the recorded nodes at context shutdown.
     * Content is deleted before its metadata; unrelated records and collections are retained.
     */
    @PreDestroy
    public void cleanUp() {
        if (parentId == null) {
            return;
        }
        try {
            nodesApi.deleteNode(parentId, true);
        } catch (Exception e) {
            log.warn("Error deleting Alfresco node: {}", e.getMessage());
        }
        for (val nodeId : nodeIds) {
            try {
                contentService.deleteNodeContent(nodeId);
                mongoNodeRepository.deleteById(nodeId);
            } catch (Exception e) {
                log.warn("Error deleting test node {}: {}", nodeId, e.getMessage());
            }
        }
    }

}
