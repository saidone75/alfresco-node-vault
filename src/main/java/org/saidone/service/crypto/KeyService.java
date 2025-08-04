/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

/**
 * Service responsible for managing encryption keys used to protect nodes in the vault.
 * Implementations typically handle key rotation and re-encryption of persisted data.
 */
public interface KeyService {

    /**
     * Re-encrypts the node identified by the given ID using the latest available key version.
     *
     * @param nodeId the Alfresco node identifier
     */
    void updateKey(String nodeId);

    /**
     * Re-encrypts all nodes currently protected with the specified key version to the latest version.
     *
     * @param sourceVersion the encryption key version currently used by the nodes to update
     */
    void updateKeys(int sourceVersion);

    /**
     * Re-encrypts all nodes from the given source key version to the desired target key version.
     *
     * @param sourceVersion the encryption key version from which nodes will be re-encrypted
     * @param targetVersion the target encryption key version to apply
     */
    void updateKeys(int sourceVersion, int targetVersion);

}