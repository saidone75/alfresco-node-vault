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

package org.saidone.exception;

/**
 * Raised to signal a failure while persisting or retrieving notarization data.
 * <p>
 * It wraps any lower level exception coming from the underlying blockchain or
 * storage implementation so that callers can react uniformly to notarization
 * errors.
 * </p>
 */
public class NotarizationException extends VaultException {

    /**
     * Creates a new instance with the provided error message.
     *
     * @param message description of the failure
     */
    public NotarizationException(String message) {
        super(message);
    }
}
