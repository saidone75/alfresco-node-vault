/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

package org.saidone.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.saidone.service.AuthenticationService;
import org.saidone.service.audit.AuditEntry;
import org.saidone.service.audit.AuditServiceImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST controller exposing read-only operations for the audit log.
 * <p>
 * All endpoints require basic authentication and delegate the retrieval of
 * entries to {@link AuditServiceImpl}.
 * </p>
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit API", description = "Audit log operations")
public class AuditApiController {

    private final AuditServiceImpl auditService;
    private final AuthenticationService authenticationService;

    /**
     * Retrieves audit entries optionally filtered by type and timestamp range.
     *
     * @param auth optional Basic authentication header
     * @param type filter by entry type
     * @param from start timestamp (inclusive)
     * @param to   end timestamp (inclusive)
     * @param page page number
     * @param size page size
     * @return the list of matching audit entries
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping
    @Operation(
            summary = "Get audit entries",
            description = "Retrieves audit log entries with optional filtering.",
            parameters = {
                    @Parameter(name = "type", description = "Entry type", in = ParameterIn.QUERY),
                    @Parameter(name = "from", description = "Start timestamp (ISO-8601)", in = ParameterIn.QUERY),
                    @Parameter(name = "to", description = "End timestamp (ISO-8601)", in = ParameterIn.QUERY),
                    @Parameter(name = "page", description = "Page number", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "Page size", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Entries retrieved", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuditEntry.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    @SneakyThrows
    public ResponseEntity<List<AuditEntry>> getEntries(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "archiveDate"));
        val list = auditService.findEntries(type, from, to, pageable);
        return ResponseEntity.ok(list);
    }

}
