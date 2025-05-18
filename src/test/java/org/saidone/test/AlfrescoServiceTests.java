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

import feign.FeignException;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.testcontainers.AlfrescoContainer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepositoryImpl;
import org.saidone.service.AlfrescoService;
import org.saidone.service.SecretService;
import org.saidone.service.VaultService;
import org.saidone.utils.ResourceFileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class AlfrescoServiceTests extends BaseTest {

    @Container
    static AlfrescoContainer<?> alfrescoContainer;

    static {
        try {
            alfrescoContainer = new AlfrescoContainer<>("23.2.1")
                    .withExposedPorts(8080)
                    .withEnv("JAVA_OPTS", "-Xms1g -Xmx2g")
                    .waitingFor(Wait.forHttp("/alfresco/api/-default-/public/alfresco/versions/1/probes/-ready-")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(20)));
            alfrescoContainer.start();
            log.info("Alfresco container started on port: {}", alfrescoContainer.getMappedPort(8080));
        } catch (Exception e) {
            log.error("Failed to start Alfresco container: {}", e.getMessage());
            throw new RuntimeException("Could not start Alfresco container", e);
        }
    }

    @DynamicPropertySource
    static void alfrescoProperties(DynamicPropertyRegistry registry) {
        String alfrescoUrl = String.format("http://localhost:%d", alfrescoContainer.getMappedPort(8080));
        registry.add("content.service.url", () -> alfrescoUrl);
        log.info("Configured content.service.url: {}", alfrescoUrl);
    }

    @MockitoBean
    VaultService vaultService;

    @MockitoBean
    SecretService secretService;

    @MockitoBean
    MongoNodeRepositoryImpl mongoNodeRepository;

    @MockitoBean
    GridFsRepositoryImpl gridFsRepository;

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

}