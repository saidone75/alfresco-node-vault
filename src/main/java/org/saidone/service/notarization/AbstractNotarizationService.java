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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.service.VaultService;
import org.saidone.model.NodeWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Base implementation providing shared functionality for notarization services.
 * <p>
 * This class exposes the {@link VaultService} used to retrieve {@link NodeWrapper}
 * instances and offers a default implementation of {@link #notarizeDocument(String)}
 * that computes the document hash and delegates to {@link #storeHash(String, String)}.
 * Subclasses only need to implement the storage logic specific to the chosen
 * backend (e.g. blockchain, database...).
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractNotarizationService extends BaseComponent implements NotarizationService {

    /** Service used to access nodes archived in the vault. */
    private final VaultService vaultService;

    /**
     * Persists the given hash using the concrete notarization backend.
     *
     * @param nodeId the Alfresco node identifier
     * @param hash   the hash value to persist
     * @return a reference to the stored proof
     */
    public abstract String storeHash(String nodeId, String hash);

    /**
     * Computes the document hash and stores it via {@link #storeHash(String, String)}.
     *
     * @param nodeId the Alfresco node identifier
     */
    @SneakyThrows
    public void notarizeDocument(String nodeId) {
        storeHash(nodeId, "hash");
        log.debug("notarizing document {}", nodeId);
    }

}
