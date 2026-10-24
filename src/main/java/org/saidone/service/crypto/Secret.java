/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Arrays;

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
public class Secret implements AutoCloseable {

    /**
     * Version of the secret as stored in Vault metadata.
     */
    private int version;

    /**
     * Raw secret bytes retrieved from Vault.
     */
    @ToString.Exclude
    private byte[] data;

    @SuppressWarnings("unused")
    public static class SecretBuilder {
        @Override
        public String toString() {
            return "Secret.SecretBuilder()";
        }
    }

    /** Clears the owned buffer; safe to call repeatedly. The version remains available. */
    @Override
    public void close() {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

}
