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

package org.saidone.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Criteria of the advanced search on archived node metadata. Every criterion
 * is optional and all the supplied ones must match. The search is available
 * only when node metadata is not encrypted.
 */
@Data
@Schema(description = "Criteria of the advanced search. Every criterion is optional and all the supplied ones must match.")
public class NodeSearchRequestDto {

    /** How multiple values or conditions are combined. */
    @Schema(description = "How multiple values or conditions are combined: ANY (OR) or ALL (AND).")
    public enum Match {
        /** At least one must match (OR). */
        ANY,
        /** All must match (AND). */
        ALL
    }

    /** Node name, case-insensitive. Supports the {@code *} and {@code ?} wildcards. */
    @Schema(description = "Node name, case-insensitive. Supports the * (any sequence of characters) and ? (any single character) wildcards.", example = "contract_*.pdf")
    private String name;

    /** Node type, e.g. {@code cm:content}. */
    @Schema(description = "Node type, exact match.", example = "cm:content")
    private String nodeType;

    /** Lower bound of the creation date, inclusive. */
    @Schema(description = "Lower bound of the creation date, inclusive.", example = "2026-01-01T00:00:00Z")
    private Instant createdFrom;

    /** Upper bound of the creation date, inclusive. */
    @Schema(description = "Upper bound of the creation date, inclusive.", example = "2026-06-30T23:59:59Z")
    private Instant createdTo;

    /** Lower bound of the modification date, inclusive. */
    @Schema(description = "Lower bound of the modification date, inclusive.", example = "2026-01-01T00:00:00Z")
    private Instant modifiedFrom;

    /** Upper bound of the modification date, inclusive. */
    @Schema(description = "Upper bound of the modification date, inclusive.", example = "2026-10-16T23:59:59Z")
    private Instant modifiedTo;

    /** Content mimetype, e.g. {@code application/pdf}. */
    @Schema(description = "Content mimetype, exact match.", example = "application/pdf")
    private String mimeType;

    /** Minimum content size in bytes, inclusive. */
    @Schema(description = "Minimum content size in bytes, inclusive.", example = "1000")
    private Long minSize;

    /** Maximum content size in bytes, inclusive. */
    @Schema(description = "Maximum content size in bytes, inclusive.", example = "5000000")
    private Long maxSize;

    /** Aspects that must all be applied to the node. */
    @Schema(description = "Aspects that must all be applied to the node.", example = "[\"cm:titled\", \"cm:dublincore\"]")
    private List<String> aspects;

    /** Conditions on node properties. */
    @Schema(description = "Conditions on node properties, combined according to propertiesMatch.")
    private List<PropertyFilter> properties;

    /** How the property conditions are combined. Defaults to {@link Match#ALL}. */
    @Schema(description = "How the property conditions are combined: ALL (default) requires all of them to match, ANY at least one.", defaultValue = "ALL")
    private Match propertiesMatch = Match.ALL;

    /**
     * Condition on a single node property.
     */
    @Data
    @Schema(description = "Condition on a single node property.")
    public static class PropertyFilter {

        /** Property name, e.g. {@code cm:title}. */
        @Schema(description = "Property name.", example = "cm:publisher", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        /** Values to look for. */
        @Schema(description = "Values to look for, at least one.", example = "[\"ACME\", \"Initech\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Object> values;

        /**
         * How the values are combined. With {@link Match#ANY} the property must
         * equal one of the values; with {@link Match#ALL} a multi-valued property
         * must contain all of them. Defaults to {@link Match#ANY}.
         */
        @Schema(description = "How the values are combined: ANY (default) requires the property to equal one of them, ALL requires a multi-valued property to contain all of them.", defaultValue = "ANY")
        private Match match = Match.ANY;

    }

}
