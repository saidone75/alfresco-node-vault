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

package org.saidone.service.content;

import org.alfresco.core.model.Node;
import org.saidone.model.NodeContent;

import java.io.InputStream;

/**
 * Abstraction over the persistence mechanism used to store node content.
 * Implementations handle saving, retrieving and deleting binary files as well
 * as computing cryptographic hashes.
 */
public interface ContentService {

    /**
     * Saves the content of the provided node into the underlying store.
     *
     * @param node        node whose content should be archived
     * @param inputStream stream providing the node content
     */
    void archiveNodeContent(Node node, InputStream inputStream);

    /**
     * Retrieves previously archived content for the given node id.
     *
     * @param nodeId identifier of the node
     * @return a {@link NodeContent} descriptor containing stream and metadata
     */
    NodeContent getNodeContent(String nodeId);

    /**
     * Deletes content associated with the given node id from the store.
     *
     * @param nodeId identifier of the node
     */
    void deleteFileById(String nodeId);

    /**
     * Computes the cryptographic hash of a stored node's content using the given algorithm.
     *
     * @param nodeId    identifier of the node
     * @param algorithm name of the hash algorithm, e.g. MD5 or SHA-256
     * @return the hexadecimal encoded hash string
     */
    String computeHash(String nodeId, String algorithm);

}
