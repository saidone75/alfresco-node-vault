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

package org.saidone.model;

import lombok.Data;

import java.io.InputStream;

/**
 * Represents the content of a node including its metadata and the actual content stream.
 * <p>
 * This class holds information about the file name, content type, length of the content,
 * and an InputStream to read the content data.
 * </p>
 */
@Data
public class NodeContent {

    /**
     * The name of the file associated with this node content.
     */
    private String fileName;

    /**
     * The MIME type of the content.
     */
    private String contentType;

    /**
     * The length of the content in bytes.
     */
    private long length;

    /**
     * The input stream to read the content data.
     */
    private InputStream contentStream;

}
