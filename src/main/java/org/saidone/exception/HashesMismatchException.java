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

package org.saidone.exception;

/**
 * Exception thrown when a node's checksum stored in Alfresco does not match the
 * value stored in the vault.
 */
public class HashesMismatchException extends VaultException {
    public HashesMismatchException(String alfrescoHash, String vaultHash) {
        super("""
                Hashes does not match:
                Alfresco hash is %s
                Vault    hash is %s
                """.formatted(alfrescoHash, vaultHash));
    }
}