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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.saidone.exception.NodeNotFoundOnAlfrescoException;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class VaultServiceTests extends BaseTest {

    @Autowired
    AlfrescoService alfrescoService;
    @Autowired
    VaultService vaultService;

    @Test
    @Order(10)
    @SneakyThrows
    void archiveNodeTest() {
        val nodeId = createNode().getId();
        // save node on the vault
        assertDoesNotThrow(() -> vaultService.archiveNode(nodeId));
        // check if node is on the vault
        assertDoesNotThrow(() -> vaultService.getNode(nodeId));
    }

    @Test
    @Order(20)
    void nonExistentNodeTest() {
        assertThrows(NodeNotFoundOnVaultException.class, () -> vaultService.getNode(UUID.randomUUID().toString()));
        assertThrows(NodeNotFoundOnAlfrescoException.class, () -> vaultService.archiveNode(UUID.randomUUID().toString()));
    }

    @Test
    @Order(30)
    @SneakyThrows
    void restoreNodeTest() {
        val nodeId = createNode().getId();
        // save node on the vault
        assertDoesNotThrow(() -> vaultService.archiveNode(nodeId));
        // restore node
        val newNodeId = assertDoesNotThrow(() -> vaultService.restoreNode(nodeId, false));
        // check if node exists on Alfresco
        assertDoesNotThrow(() -> alfrescoService.getNode(newNodeId));
    }

    @Test
    @Order(40)
    @SneakyThrows
    void archiveNodesTest() {
        IntStream.range(0, 42).parallel().forEach(i -> {
            try {
                val nodeId = createNode().getId();
                // save node on the vault
                assertDoesNotThrow(() -> vaultService.archiveNode(nodeId));
                // check if node is on the vault
                assertDoesNotThrow(() -> vaultService.getNode(nodeId));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}