package org.saidone.test;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.saidone.audit.AuditEntry;
import org.saidone.audit.AuditService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
class AuditApiControllerTests extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private AuditService auditService;

    @Value("${content.service.security.basicAuth.username}")
    private String userName;
    @Value("${content.service.security.basicAuth.password}")
    private String password;
    private static String basicAuth;

    @BeforeEach
    public void beforeEach(org.junit.jupiter.api.TestInfo testInfo) {
        super.before(testInfo);
        if (basicAuth == null) {
            basicAuth = String.format("Basic %s", Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8)));
        }
        log.info("Running --> {}", testInfo.getDisplayName());
    }

    @Test
    @SneakyThrows
    void getAuditEntries() {
        auditService.saveEntry(new HashMap<>(), "test");
        auditService.saveEntry(new HashMap<>(), "test");

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/audit")
                        .queryParam("type", "test")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(AuditEntry.class)
                .value(list -> assertTrue(list.size() >= 2));
    }
}
