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

/**
 * Standard metadata keys used when storing node content in various backends.
 * These constants provide a common naming convention across implementations.
 */
public interface MetadataKeys {

    /** Name of the checksum algorithm (e.g. SHA-256). */
    String CHECKSUM_ALGORITHM = "chkalg";
    /** Hex encoded checksum value. */
    String CHECKSUM_VALUE = "chkval";
    /** MIME type associated with the stored content. */
    String CONTENT_TYPE = "_contentType";
    /** Flag marking content as encrypted. */
    String ENCRYPTED = "enc";
    /** Original file name. */
    String FILENAME = "filename";
    /** Unique identifier of the stored object. */
    String UUID = "uuid";

}
