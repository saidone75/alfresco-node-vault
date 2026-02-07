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

import java.io.InputStream;

/**
 * Descriptor representing a node's binary data as an {@link InputStream} along
 * with its length. Extends {@link NodeContent} to also expose the file name and
 * content type.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NodeContentStream extends NodeContent {

    /** Size of the content in bytes. */
    private long length;
    /** Stream providing access to the binary content. */
    private InputStream contentStream;

}
