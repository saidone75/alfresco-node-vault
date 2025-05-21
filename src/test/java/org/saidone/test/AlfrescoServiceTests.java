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

package org.saidone.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import feign.FeignException;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.PathElement;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.*;
import org.saidone.model.NodeWrapper;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.saidone.utils.ResourceFileUtils;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class AlfrescoServiceTests extends BaseTest {

    @MockitoBean
    VaultService vaultService;

    @Autowired
    AlfrescoService alfrescoService;

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
        @Cleanup val inputStream = assertDoesNotThrow(() -> alfrescoService.getNodeContent(nodeId));
        assertArrayEquals(Files.readAllBytes(file.toPath()), inputStream.readAllBytes());
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
    @Order(60)
    public void createPathIfNotExistsTest() {
        val pathElements = List.of("foo", "bar", "baz");
        var nodeId = alfrescoService.createPathIfNotExists(BaseTest.parentId, pathElements);
        var node = alfrescoService.getNode(nodeId);
        var path = node.getPath().getElements().stream().skip(1).map(PathElement::getName).collect(Collectors.joining("/"));
        assertTrue(alfrescoService.pathExists(String.format("%s/%s", path, node.getName())));
        nodeId = alfrescoService.createPathIfNotExists(BaseTest.parentId, pathElements);
        node = alfrescoService.getNode(nodeId);
        path = node.getPath().getElements().stream().skip(1).map(PathElement::getName).collect(Collectors.joining("/"));
        assertTrue(alfrescoService.pathExists(String.format("%s/%s", path, node.getName())));
    }

    @Test
    @Order(70)
    @SneakyThrows
    public void nodeWrapperTest() {
        assertThrows(IllegalArgumentException.class, () -> new NodeWrapper(null));
        val node = createNode();
        val nodeWrapper = assertDoesNotThrow(() -> new NodeWrapper(node));
        nodeWrapper.setNodeJson(UUID.randomUUID().toString());
        assertThrows(JsonProcessingException.class, nodeWrapper::getNode);
        nodeWrapper.setNodeJson(Strings.EMPTY);
        assertThrows(MismatchedInputException.class, nodeWrapper::getNode);
        nodeWrapper.setNodeJson(null);
        assertThrows(IllegalArgumentException.class, nodeWrapper::getNode);
    }

}