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

package org.saidone.service.notarization;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.service.NodeService;
import org.saidone.service.content.ContentService;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractNotarizationService extends BaseComponent implements NotarizationService {

    private final NodeService nodeService;
    private final ContentService contentService;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    public abstract String putHash(String nodeId, String hash);

    public abstract String getHash(String txId);

    @SneakyThrows
    public void notarizeDocument(String nodeId) {
        log.debug("Notarizing document {}", nodeId);
        val hash = contentService.computeHash(nodeId, checksumAlgorithm);
        val txHash = putHash(nodeId, hash);
        val nodeWrapper = nodeService.findById(nodeId);
        nodeWrapper.setNotarizationTxId(txHash);
        nodeService.save(nodeWrapper);
    }

}
