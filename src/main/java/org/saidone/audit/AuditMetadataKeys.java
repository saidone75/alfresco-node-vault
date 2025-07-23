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

package org.saidone.audit;

/**
 * Abbreviated MongoDB field names and audit metadata keys used throughout the
 * auditing subsystem. These constants keep the stored documents small while
 * providing a single point of reference for the mapping between Java fields and
 * their persisted counterparts.
 */
public interface AuditMetadataKeys {

    /** Field name for the audit entry timestamp. */
    String TIMESTAMP = "ts";
    /** Field name for the metadata document. */
    String METADATA = "md";
    /** Field name for the entry type. */
    String TYPE = "typ";

    String ID = "id";
    /** Client IP address. */
    String IP = "ip";
    /** HTTP {@code User-Agent} header value. */
    String USER_AGENT = "ua";
    /** Requested path. */
    String PATH = "path";
    /** HTTP method. */
    String METHOD = "mth";

    /** Constant identifying request audit entries. */
    String REQUEST = "req";
    /** HTTP response status code. */
    String STATUS = "st";
    /** Constant identifying response audit entries. */
    String RESPONSE = "res";

}
