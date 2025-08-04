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

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.saidone.service.NodeService;
import org.springframework.stereotype.Service;

/**
 * Default {@link KeyService} implementation that delegates node retrieval to a {@link NodeService}.
 * The actual re-encryption logic is currently left unimplemented and will be provided in future iterations.
 */
@RequiredArgsConstructor
@Service
public class KeyServiceImpl implements KeyService {

    private final NodeService nodeService;

    /** {@inheritDoc} */
    @Override
    public void updateKey(String nodeId) {

    }

    /** {@inheritDoc} */
    @Override
    public void updateKeys(int sourceVersion) {
        val nodes = nodeService.findByKv(sourceVersion);
    }

    /** {@inheritDoc} */
    @Override
    public void updateKeys(int sourceVersion, int targetVersion) {

    }

}
