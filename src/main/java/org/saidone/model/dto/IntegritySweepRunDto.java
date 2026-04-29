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
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the summary of a single integrity sweep execution.
 */
@Data
@Document(collection = "integrity_sweep_run")
public class IntegritySweepRunDto {

    @Id
    private String id;

    @Field("sat")
    @Indexed
    private Instant startedAt;

    @Field("eat")
    private Instant endedAt;

    @Field("dur")
    private long durationMs;

    @Field("trg")
    private String trigger;

    @Field("st")
    private String status;

    @Field("scn")
    private int scanned;

    @Field("pss")
    private int passed;

    @Field("fal")
    private int failed;

    @Field("err")
    private int errors;

    @Field("fnd")
    private List<String> failedNodeIds = new ArrayList<>();

}
