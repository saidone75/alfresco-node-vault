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

package org.saidone.model.dto;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Tracks nodes that failed integrity checks and may require remediation/retry.
 */
@Data
@Document(collection = "integrity_corrupted_node")
public class CorruptedNodeDto {

    @Id
    private String nodeId;

    @Field("ffs")
    private Instant firstFailedAt;

    @Field("lfs")
    @Indexed
    private Instant lastFailedAt;

    @Field("lat")
    private Instant lastAttemptAt;

    @Field("frs")
    private String failureReason;

    @Field("att")
    private int attempts;

    @Field("rid")
    private String lastRunId;
}
