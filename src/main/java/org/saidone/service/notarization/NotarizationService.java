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

/**
 * Contract for services capable of notarizing archived documents.
 * <p>
 * Implementations typically store a cryptographic hash of a node's content
 * into an immutable data structure such as a blockchain. The returned value of
 * {@link #storeHash(String, String)} can be used as a reference to the proof
 * (for example, a transaction ID).
 */
public interface NotarizationService {

    /**
     * Persists the provided hash in the underlying notarization backend.
     *
     * @param nodeId the Alfresco node identifier, used for logging purposes
     * @param hash   the hexadecimal or base64 encoded hash value to persist
     * @return a reference to the stored proof (e.g. a transaction hash)
     */
    String storeHash(String nodeId, String hash);

    /**
     * Generates and stores the hash for the given node.
     *
     * @param nodeId the Alfresco node identifier
     */
    void notarizeDocument(String nodeId);

}
