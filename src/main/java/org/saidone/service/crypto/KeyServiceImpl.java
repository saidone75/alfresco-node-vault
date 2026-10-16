/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
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

package org.saidone.service.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.service.NodeService;
import org.saidone.service.SecretService;
import org.saidone.service.content.ContentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Default {@link KeyService} implementation that re-encrypts node content and
 * metadata by delegating node retrieval to {@link NodeService} and content
 * handling to {@link ContentService}.
 * <p>
 * The bean is active when at least one of
 * {@code application.service.vault.encryption.content.enabled} or
 * {@code application.service.vault.encryption.metadata.enabled} is set to
 * {@code true}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("('${application.service.vault.encryption.content.enabled:false}' == 'true' or '${application.service.vault.encryption.metadata.enabled:false}' == 'true')")
public class KeyServiceImpl extends BaseComponent implements KeyService {

    /**
     * Provides access to the encryption secrets used for re-encryption.
     */
    private final SecretService secretService;
    /**
     * Retrieves and persists node metadata.
     */
    private final NodeService nodeService;
    /**
     * Handles reading and writing of encrypted node content.
     */
    private final ContentService contentService;
    /**
     * Whether binary content is encrypted and must be re-encrypted on key rotation.
     */
    @Value("${application.service.vault.encryption.content.enabled:false}")
    private boolean contentEncryptionEnabled;

    /**
     * Re-encrypts the node identified by the given ID using the latest key
     * version.
     *
     * <p>The node metadata is loaded from the {@link NodeService}, its content
     * is archived again using the {@link ContentService} when content
     * encryption is enabled, and the updated node is persisted (re-encrypting
     * metadata when metadata encryption is enabled).</p>
     *
     * @param nodeId the Alfresco node identifier
     */
    @Override
    public void updateKey(String nodeId) {
        val nodeWrapper = nodeService.findById(nodeId);
        val actualKeyVersion = nodeWrapper.getKeyVersion();
        val lastKeyVersion = secretService.getSecret().getVersion();
        if (actualKeyVersion < lastKeyVersion) {
            log.debug("Updating encryption key from v.{} to v.{} for node {}", actualKeyVersion, lastKeyVersion, nodeId);
            nodeWrapper.setKeyVersion(lastKeyVersion);
            if (contentEncryptionEnabled) {
                contentService.archiveNodeContent(nodeWrapper.getNode(),
                        contentService.getNodeContent(nodeId).getContentStream());
                nodeWrapper.setKeyVersion(contentService.getNodeContentInfo(nodeId).getKeyVersion());
            }
            nodeService.save(nodeWrapper);
        } else {
            log.debug("Encryption key for node {} is already up-to-date (v.{})", nodeId, actualKeyVersion);
        }
    }

    /**
     * Re-encrypts all nodes currently protected with the specified key version
     * by delegating each node to {@link #updateKey(String)}.
     *
     * @param sourceVersion the key version currently used by nodes that should
     *                      be updated
     */
    @Override
    public void updateKeys(int sourceVersion) {
        val nodes = nodeService.findByKv(sourceVersion);
        nodes.forEach(node -> updateKey(node.getId()));
    }

}
