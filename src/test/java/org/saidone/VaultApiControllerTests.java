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
import org.apache.logging.log4j.util.Strings;
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
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import utils.ResourceFileUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

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

    private <T> void performRequestAndAssert(HttpMethod method, String uri, Object[] uriVariables,
                                             String requestBody, int expectedStatus, Class<T> responseType,
                                             Consumer<T> bodyValidator) {
        WebTestClient.RequestHeadersSpec<?> requestSpec;
        if (HttpMethod.GET.equals(method)) {
            requestSpec = webTestClient.get()
                    .uri(uri, uriVariables);
        } else if (HttpMethod.POST.equals(method)) {
            requestSpec = webTestClient.post()
                    .uri(uri, uriVariables)
                    .bodyValue(requestBody);
        } else {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        requestSpec.header(HttpHeaders.AUTHORIZATION, basicAuth)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(responseType)
                .value(bodyValidator);
    }

    @Test
    @Order(10)
    @SneakyThrows
    void getNodeTest() {
        val nodeId = createNode().getId();
        performRequestAndAssert(HttpMethod.GET, "/api/vault/nodes/{nodeId}", new Object[]{nodeId},
                null, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        performRequestAndAssert(HttpMethod.GET, "/api/vault/nodes/{nodeId}", new Object[]{nodeId},
                null, 200, String.class, body -> assertTrue(body.contains(nodeId)));
    }

    @Test
    @Order(10)
    @SneakyThrows
    void getNodeContentTest() {
        val nodeId = createNode().getId();
        performRequestAndAssert(HttpMethod.GET, "/api/vault/nodes/{nodeId}/content", new Object[]{nodeId},
                null, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        performRequestAndAssert(HttpMethod.GET, "/api/vault/nodes/{nodeId}/content", new Object[]{nodeId},
                null, 200, byte[].class, body -> assertTrue(body.length > 0));
    }

    @Test
    @Order(30)
    @SneakyThrows
    void restoreNodeTest() {
        val nodeId = createNode().getId();
        performRequestAndAssert(HttpMethod.POST, "/api/vault/nodes/{nodeId}/restore", new Object[]{nodeId},
                Strings.EMPTY, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        performRequestAndAssert(HttpMethod.POST, "/api/vault/nodes/{nodeId}/restore", new Object[]{nodeId},
                Strings.EMPTY, 200, String.class, body -> assertTrue(body.contains(nodeId)));
        // TODO check if restored node exists on Alfresco
    }

    @Test
    @Order(100)
    @SneakyThrows
    public void cleanUp() {
        nodesApi.deleteNode(parentId, true);
        mongoClient.getDatabase(database).drop();
    }

}