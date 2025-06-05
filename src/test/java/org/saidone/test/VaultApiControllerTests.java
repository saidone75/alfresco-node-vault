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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.*;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class VaultApiControllerTests extends BaseTest {

    @Autowired
    VaultService vaultService;
    @Autowired
    private WebTestClient webTestClient;

    @Value("${content.service.security.basicAuth.username}")
    private String userName;
    @Value("${content.service.security.basicAuth.password}")
    private String password;
    private static String basicAuth;

    @Autowired
    private AlfrescoService alfrescoService;

    @BeforeEach
    public void before(TestInfo testInfo) {
        super.before(testInfo);
        if (basicAuth == null) basicAuth = String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", userName, password)).getBytes(StandardCharsets.UTF_8)));
        log.info("Running --> {}", testInfo.getDisplayName());
    }

    private <T> void performRequestAndProcess(HttpMethod method, String uri, Object[] uriVariables,
                                              String requestBody, int expectedStatus, Class<T> responseType,
                                              Consumer<T> bodyProcessor) {
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
                .value(bodyProcessor);
    }

    @Test
    @Order(10)
    @SneakyThrows
    void getNodeTest() {
        val nodeId = createNode().getId();
        performRequestAndProcess(HttpMethod.GET, "/api/vault/nodes/{nodeId}", new Object[]{nodeId},
                null, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        performRequestAndProcess(HttpMethod.GET, "/api/vault/nodes/{nodeId}", new Object[]{nodeId},
                null, 200, String.class, body -> assertTrue(body.contains(nodeId)));
    }

    @Test
    @Order(20)
    @SneakyThrows
    void getNodeContentTest() {
        val nodeId = createNode().getId();
        performRequestAndProcess(HttpMethod.GET, "/api/vault/nodes/{nodeId}/content", new Object[]{nodeId},
                null, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        performRequestAndProcess(HttpMethod.GET, "/api/vault/nodes/{nodeId}/content", new Object[]{nodeId},
                null, 200, byte[].class, body -> assertTrue(body.length > 0));
    }

    @Test
    @Order(30)
    @SneakyThrows
    void restoreNodeTest() {
        val nodeId = createNode().getId();
        performRequestAndProcess(HttpMethod.POST, "/api/vault/nodes/{nodeId}/restore", new Object[]{nodeId},
                Strings.EMPTY, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        vaultService.archiveNode(nodeId);
        val result = new AtomicReference<String>();
        val consumer = (Consumer<String>) body -> {
            var pattern = Pattern.compile("^.*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}).*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");
            var matcher = pattern.matcher(body);
            assertTrue(matcher.matches());
            result.set(matcher.group(2));
        };
        performRequestAndProcess(HttpMethod.POST, "/api/vault/nodes/{nodeId}/restore", new Object[]{nodeId},
                Strings.EMPTY, 200, String.class, consumer);
        assertDoesNotThrow(() -> alfrescoService.getNode(result.get()));
    }

    @Test
    @Order(40)
    @SneakyThrows
    void archiveNodeTest() {
        val nodeId = createNode().getId();
        performRequestAndProcess(HttpMethod.POST, "/api/vault/nodes/{nodeId}/archive", new Object[]{nodeId},
                Strings.EMPTY, 200, String.class, body -> assertTrue(body.contains(nodeId)));
        performRequestAndProcess(HttpMethod.POST, "/api/vault/nodes/{nodeId}/archive", new Object[]{nodeId},
                Strings.EMPTY, 404, String.class, body -> assertTrue(body.contains(nodeId)));
        assertThrows(FeignException.NotFound.class, () -> alfrescoService.getNode(nodeId));
    }

}