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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.saidone.service.NodeService;
import org.saidone.service.VaultService;
import org.saidone.service.content.ContentService;
import org.saidone.service.notarization.NotarizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
public class NotarizationServiceTests extends BaseTest {

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    @Autowired
    VaultService vaultService;
    @Autowired
    NodeService nodeService;
    @Autowired
    ContentService contentService;
    @Autowired
    NotarizationService notarizationService;

    @Test
    @Order(10)
    @SneakyThrows
    void notarizeNodeTest() {
        val nodeId = createNode().getId();
        // save node on the vault
        vaultService.archiveNode(nodeId);
        // notarize node
        assertDoesNotThrow(() -> notarizationService.notarizeNode(nodeId));
        // check if node has been notarized
        assertNotNull(nodeService.findById(nodeId).getNotarizationTxId());
    }

    @Test
    @Order(20)
    @SneakyThrows
    void checkNotarizationTest() {
        val nodeId = createNode().getId();
        // save node on the vault
        vaultService.archiveNode(nodeId);
        // notarize node
        notarizationService.notarizeNode(nodeId);
        // check if hashes match
        val notarizationTransactionId = nodeService.findById(nodeId).getNotarizationTxId();
        val txHash = notarizationService.getHash(notarizationTransactionId);
        val hash = contentService.computeHash(nodeId, checksumAlgorithm);
        assertEquals(txHash, hash);
    }

}
