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
import org.saidone.job.NodeArchivingJob;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import utils.ResourceFileUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class VaultApiControllerTests {

    @MockitoBean
    EventHandler eventHandler;
    @MockitoBean
    NodeArchivingJob nodeArchivingJob;

    @Autowired
    VaultService vaultService;

    @Autowired
    NodesApi nodesApi;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private WebTestClient webTestClient;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${content.service.security.basicAuth.username}")
    private String userName;
    @Value("${content.service.security.basicAuth.password}")
    private String password;

    private static String basicAuth;
    private static String parentId;

    @BeforeEach
    public void before(TestInfo testInfo) {
        if (parentId == null) {
            val nodeBodyCreate = new NodeBodyCreate();
            nodeBodyCreate.setName(UUID.randomUUID().toString());
            nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_FOLDER);
            parentId = Objects.requireNonNull(nodesApi.createNode(AlfrescoService.guestHome.getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry().getId();
        }
        basicAuth = String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", userName, password)).getBytes(StandardCharsets.UTF_8)));
        log.info("Running --> {}", testInfo.getDisplayName());
    }

    @SneakyThrows
    private Node createNode() {
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
    void getNodeTest() {
        val nodeId = createNode().getId();
        webTestClient.get()
                .uri("/api/vault/nodes/{nodeId}", nodeId)
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        webTestClient.get()
                .uri("/api/vault/nodes/{nodeId}", nodeId)
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains(nodeId)));
    }

    @Test
    @Order(10)
    @SneakyThrows
    void getNodeContentTest() {
    }

    @Test
    @Order(100)
    @SneakyThrows
    public void cleanUp() {
        nodesApi.deleteNode(parentId, true);
        mongoClient.getDatabase(database).drop();
    }

}