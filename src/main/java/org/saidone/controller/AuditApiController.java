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
     * @param to        end timestamp (inclusive)
     * @param maxItems  maximum number of items to return
     * @param skipCount number of items to skip (for pagination)
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
            @RequestParam(required = false, defaultValue = "20") int maxItems,
            @RequestParam(required = false, defaultValue = "0") int skipCount) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val list = auditService.findEntries(type, from, to, maxItems, skipCount);
        return ResponseEntity.ok(list);
    }

}
