/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.saidone.service.audit.AuditEntry;
import org.saidone.service.audit.AuditServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link org.saidone.controller.AuditApiController}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
class AuditApiControllerTests extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private AuditServiceImpl auditService;

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
        val auditEntry = new AuditEntry();
        auditEntry.setType("test");
        auditService.saveEntry(auditEntry);
        auditService.saveEntry(auditEntry);

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
