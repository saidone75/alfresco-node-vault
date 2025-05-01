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

package org.saidone;

import com.mongodb.client.MongoClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.NodeBodyCreate;
import org.junit.jupiter.api.*;
import org.saidone.behaviour.EventHandler;
import org.saidone.exception.NodeNotOnVaultException;
import org.saidone.job.NodeArchivingJob;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.annotation.AfterTestClass;
import utils.ResourceFileUtils;

import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class VaultServiceTests {

    @MockitoBean
    EventHandler eventHandler;
    @MockitoBean
    NodeArchivingJob nodeArchivingJob;

    @Autowired
    AlfrescoService alfrescoService;

    @Autowired
    VaultService vaultService;

    @Autowired
    NodesApi nodesApi;

    @Autowired
    private MongoClient mongoClient;

    @Value("${spring.data.mongodb.database}")
    private String database;

    private static String parentId;

    @BeforeEach
    public void before(TestInfo testInfo) {
        if (parentId == null) {
            val nodeBodyCreate = new NodeBodyCreate();
            nodeBodyCreate.setName(UUID.randomUUID().toString());
            nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_FOLDER);
            parentId = Objects.requireNonNull(nodesApi.createNode(AlfrescoService.guestHome.getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry().getId();
        }
        log.info("Running --> {}", testInfo.getDisplayName());
    }

    @AfterAll
    public void cleanUp() {
        nodesApi.deleteNode(parentId, true);
        mongoClient.getDatabase(database).drop();
    }

    @SneakyThrows
    public Node createNode() {
        val file = ResourceFileUtils.getFileFromResource("sample.pdf");
        val nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(String.format("%s.pdf", UUID.randomUUID()));
        nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
        val node = Objects.requireNonNull(nodesApi.createNode(parentId, nodeBodyCreate, null, null, null, null, null).getBody()).getEntry();
        nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
        return node;
    }

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
        assertThrows(NodeNotOnVaultException.class, () -> vaultService.getNode(UUID.randomUUID().toString()));
        assertThrows(NodeNotOnVaultException.class, () -> vaultService.archiveNode(UUID.randomUUID().toString()));
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