/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
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

package org.saidone.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Criteria of the advanced search on archived node metadata. Every criterion
 * is optional and all the supplied ones must match. The search is available
 * only when node metadata is not encrypted.
 */
@Data
public class NodeSearchRequestDto {

    /** How multiple values or conditions are combined. */
    public enum Match {
        /** At least one must match (OR). */
        ANY,
        /** All must match (AND). */
        ALL
    }

    /** Node name, case-insensitive. Supports the {@code *} and {@code ?} wildcards. */
    private String name;

    /** Node type, e.g. {@code cm:content}. */
    private String nodeType;

    /** Lower bound of the creation date, inclusive. */
    private Instant createdFrom;

    /** Upper bound of the creation date, inclusive. */
    private Instant createdTo;

    /** Lower bound of the modification date, inclusive. */
    private Instant modifiedFrom;

    /** Upper bound of the modification date, inclusive. */
    private Instant modifiedTo;

    /** Content mimetype, e.g. {@code application/pdf}. */
    private String mimeType;

    /** Minimum content size in bytes, inclusive. */
    private Long minSize;

    /** Maximum content size in bytes, inclusive. */
    private Long maxSize;

    /** Aspects that must all be applied to the node. */
    private List<String> aspects;

    /** Conditions on node properties. */
    private List<PropertyFilter> properties;

    /** How the property conditions are combined. Defaults to {@link Match#ALL}. */
    private Match propertiesMatch = Match.ALL;

    /**
     * Condition on a single node property.
     */
    @Data
    public static class PropertyFilter {

        /** Property name, e.g. {@code cm:title}. */
        private String name;

        /** Values to look for. */
        private List<Object> values;

        /**
         * How the values are combined. With {@link Match#ANY} the property must
         * equal one of the values; with {@link Match#ALL} a multi-valued property
         * must contain all of them. Defaults to {@link Match#ANY}.
         */
        private Match match = Match.ANY;

    }

}
