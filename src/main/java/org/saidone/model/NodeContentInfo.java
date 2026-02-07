/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
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

package org.saidone.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Metadata describing the archived content of a node. Extends
 * {@link NodeContent} with additional information such as checksum and
 * encryption status.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NodeContentInfo extends NodeContent {

    private String contentId;
    private String contentHashAlgorithm;
    private String contentHash;
    private boolean encrypted;

}
