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

package org.saidone.service.crypto;

import lombok.Builder;
import lombok.Data;

/**
 * Container for a secret value retrieved from Vault.
 * <p>
 * Instances of this class are produced by {@link org.saidone.service.SecretService}
 * when fetching encryption material. The {@link #version} corresponds to the
 * version number reported by Vault while {@link #data} holds the secret bytes.
 * </p>
 */
@Builder
@Data
public class Secret {

    /**
     * Version of the secret as stored in Vault metadata.
     */
    public int version;

    /**
     * Raw secret bytes retrieved from Vault.
     */
    public byte[] data;

}
