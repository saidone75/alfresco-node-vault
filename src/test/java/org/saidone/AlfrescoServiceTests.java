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

import feign.FeignException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.NodeBodyCreate;
import org.junit.jupiter.api.*;
import org.saidone.behaviour.EventHandler;
import org.saidone.job.NodeArchivingJob;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import utils.ResourceFileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class AlfrescoServiceTests {

    @MockitoBean
    EventHandler eventHandler;
    @MockitoBean
    NodeArchivingJob nodeArchivingJob;
    @MockitoBean
    VaultService vaultService;

    @Autowired
    AlfrescoService alfrescoService;

    @Autowired
    NodesApi nodesApi;

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

    @SneakyThrows
    public Node createNode(File file) {
        val nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(String.format("%s.pdf", UUID.randomUUID()));
        nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
        val node = Objects.requireNonNull(nodesApi.createNode(parentId, nodeBodyCreate, null, null, null, null, null).getBody()).getEntry();
        nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
        return node;
    }

    @SneakyThrows
    public Node createNode() {
        val file = ResourceFileUtils.getFileFromResource("sample.pdf");
        return createNode(file);
    }

    @Test
    @Order(10)
    @SneakyThrows
    public void getNodeTest() {
        val nodeId = createNode().getId();
        assertDoesNotThrow(() -> alfrescoService.getNode(nodeId));
    }

    @Test
    @Order(20)
    @SneakyThrows
    public void getNodeContentTest() {
        val file = ResourceFileUtils.getFileFromResource("sample.pdf");
        val nodeId = createNode(file).getId();
        val contentFile = assertDoesNotThrow(() -> alfrescoService.getNodeContent(nodeId));
        assertArrayEquals(Files.readAllBytes(file.toPath()), Files.readAllBytes(contentFile.toPath()));
        Files.deleteIfExists(contentFile.toPath());
    }

    @Test
    @Order(30)
    @SneakyThrows
    public void deleteNodeTest() {
        val nodeId = createNode().getId();
        assertDoesNotThrow(() -> alfrescoService.deleteNode(nodeId));
        assertThrows(FeignException.NotFound.class, () -> alfrescoService.getNode(nodeId));
    }

    @Test
    @Order(40)
    @SneakyThrows
    public void aspectsTest() {
        val nodeId = createNode().getId();
        assertDoesNotThrow(() -> alfrescoService.addAspects(nodeId, List.of(AlfrescoContentModel.ASP_EFFECTIVITY)));
        assertTrue(alfrescoService.getNode(nodeId).getAspectNames().contains(AlfrescoContentModel.ASP_EFFECTIVITY));
        assertDoesNotThrow(() -> alfrescoService.removeAspects(nodeId, List.of(AlfrescoContentModel.ASP_EFFECTIVITY)));
        assertFalse(alfrescoService.getNode(nodeId).getAspectNames().contains(AlfrescoContentModel.ASP_EFFECTIVITY));
    }

    @Test
    @Order(50)
    @SneakyThrows
    public void searchAndProcessTest() {
        val node = createNode();
        val result = new AtomicReference<String>();
        val consumer = (Consumer<String>) result::set;
        assertDoesNotThrow(() -> alfrescoService.searchAndProcess(String.format("=%s:'%s'", AlfrescoContentModel.PROP_NAME, node.getName()), consumer));
        assertEquals(node.getId(), result.get());
    }

    @Test
    @Order(100)
    @SneakyThrows
    public void cleanUp() {
        nodesApi.deleteNode(parentId, true);
    }

}