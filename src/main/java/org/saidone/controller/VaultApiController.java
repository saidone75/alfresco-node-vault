

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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.model.dto.EntryDto;
import org.saidone.model.dto.CorruptedNodeDto;
import org.saidone.model.dto.IntegritySweepRunDto;
import org.saidone.service.AuthenticationService;
import org.saidone.service.NodeService;
import org.saidone.service.VaultService;
import org.saidone.service.content.ContentService;
import org.saidone.service.integrity.IntegritySweepService;
import org.saidone.service.crypto.KeyService;
import org.saidone.service.notarization.NotarizationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller that exposes operations for interacting with the vault.
 * <p>
 * All endpoints require basic authentication and delegate the heavy lifting to
 * {@link org.saidone.service.VaultService} and {@link ContentService}.
 * </p>
 */
@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vault API", description = "Vault operations")
public class VaultApiController extends BaseComponent {

    /**
     * Service used to verify Basic authentication credentials.
     */
    private final AuthenticationService authenticationService;
    /**
     * Provides high level vault operations.
     */
    private final VaultService vaultService;
    /**
     * Service responsible for interacting with nodes.
     */
    private final NodeService nodeService;
    /**
     * Service responsible for retrieving binary content.
     */
    private final ContentService contentService;
    /**
     * Handles notarization requests for archived nodes.
     */
    private final NotarizationService notarizationService;

    /**
     * Manages encryption key rotation for node content.
     */
    private final KeyService keyService;

    /**
     * Service responsible for integrity sweep executions.
     */
    private final IntegritySweepService integritySweepService;

    /**
     * Executor used for asynchronous operations.
     */
    private final TaskExecutor taskExecutor;

    /**
     * Searches nodes archived within the specified date range with pagination support.
     * <p>
     * If no range is supplied the search defaults to the last 24 hours ending at
     * {@link Instant#now()}. This mirrors the behaviour of the underlying service
     * which looks for nodes archived during the previous day when no parameters
     * are provided.
     * </p>
     *
     * @param auth optional Basic authentication header
     * @param from start of the archive date range (inclusive)
     * @param to   end of the archive date range (inclusive)
     * @param page page number
     * @param size page size
     * @param dir  sort direction for archive date
     * @return paginated list of node entries
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/search")
    @Operation(
            summary = "Search archived nodes",
            description = "Search nodes by archive date range.",
            parameters = {
                    @Parameter(name = "from", description = "Start archive date (ISO-8601)", in = ParameterIn.QUERY),
                    @Parameter(name = "to", description = "End archive date (ISO-8601)", in = ParameterIn.QUERY),
                    @Parameter(name = "page", description = "Page number", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "Page size", in = ParameterIn.QUERY),
                    @Parameter(name = "dir", description = "Sort direction (ASC or DESC)", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nodes retrieved",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = EntryDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<Page<EntryDto>> searchNodes(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction dir) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (to == null) {
            to = Instant.now();
        }

        if (from == null) {
            from = to.minus(1, ChronoUnit.DAYS);
        }

        val pageable = PageRequest.of(page, size, Sort.by(dir, "adt"));
        val result = nodeService.findByArchiveDateRange(from, to, pageable)
                .map(nodeWrapper -> new EntryDto(nodeWrapper.getNode()));
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the metadata of a node stored in the vault.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to retrieve
     * @return the node metadata wrapped in an {@link EntryDto} object
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/nodes/{nodeId}")
    @Operation(
            summary = "Get node metadata",
            description = "Retrieves metadata of a specified node.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Node metadata retrieved successfully",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = EntryDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<EntryDto> getNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val node = vaultService.getNode(nodeId);
        return ResponseEntity.ok(new EntryDto(node));
    }

    /**
     * Streams the binary content of a node.
     *
     * @param auth       optional Basic authentication header
     * @param nodeId     identifier of the node
     * @param attachment {@code true} if the content should be sent as a download attachment
     * @return the streamed content wrapped as an {@link InputStreamResource}
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/nodes/{nodeId}/content")
    @Operation(
            summary = "Get node content",
            description = "Streams the content of the specified node. Set 'attachment' parameter to true for download as attachment.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node", required = true, in = ParameterIn.PATH),
                    @Parameter(name = "attachment", description = "Whether to send content as attachment", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Node content streamed successfully",
                            content = @Content(mediaType = "application/octet-stream",
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<InputStreamResource> getNodeContent(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "true") boolean attachment) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Streaming node content for node: {}", nodeId);

        val nodeContent = contentService.getNodeContent(nodeId);

        val headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(nodeContent.getContentType()));
        if (attachment) {
            headers.setContentDispositionFormData("attachment", nodeContent.getFileName());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(nodeContent.getContentStream()));
    }

    /**
     * Restores a node previously archived in the vault.
     *
     * @param auth               optional Basic authentication header
     * @param nodeId             identifier of the node to restore
     * @param restorePermissions {@code true} to also restore permissions
     * @return a textual confirmation message
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/{nodeId}/restore")
    @Operation(
            summary = "Restore a node",
            description = "Restores the specified node from the vault. Optionally restore permissions.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to restore", required = true, in = ParameterIn.PATH),
                    @Parameter(name = "restorePermissions", description = "Whether to restore permissions for the node", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Node successfully restored",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> restoreNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "false") boolean restorePermissions) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val newNodeId = vaultService.restoreNode(nodeId, restorePermissions);
        return ResponseEntity.ok().body(String.format("Node %s successfully restored as %s", nodeId, newNodeId));
    }

    /**
     * Archives an Alfresco node and removes it from the repository.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to archive
     * @return a confirmation message
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/{nodeId}/archive")
    @Operation(
            summary = "Archive a node",
            description = "Archives the specified node in the vault.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to archive", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Node successfully archived",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> archiveNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        vaultService.archiveNode(nodeId);
        return ResponseEntity.ok().body(String.format("Node %s successfully archived.", nodeId));
    }

    /**
     * Require notarization of a node. The notarization process runs asynchronously
     * and the call returns immediately.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to notarize
     * @return a confirmation message, or {@code 409 Conflict} if the node was already notarized
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/{nodeId}/notarize")
    @Operation(
            summary = "Notarize a node",
            description = "Require notarization of the specified node. Returns conflict if already notarized.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to notarize", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Notarization required",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "409", description = "Node already notarized",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> notarizeNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val nodeWrapper = nodeService.findById(nodeId);
        if (Strings.isNotBlank(nodeWrapper.getNotarizationTxId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(String.format("Node %s is already notarized.", nodeId));
        }
        // Run notarization asynchronously to avoid blocking the caller
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting notarization for node {}", nodeId);
                notarizationService.notarizeNode(nodeId);
                log.info("Completed notarization for node {}", nodeId);
            } catch (Exception e) {
                log.error("Error while notarizing node {}", nodeId, e);
            }
        }, taskExecutor);
        return ResponseEntity.ok().body(String.format("Notarization for node %s required.", nodeId));
    }

    /**
     * Checks the notarization status of a node and returns a human readable
     * message describing the result.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to be checked
     * @return textual description of the current notarization status
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/nodes/{nodeId}/notarize")
    @Operation(
            summary = "Check notarization",
            description = "Check notarization of the specified node.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to check", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Notarization check succeeded",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> checkNotarization(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        notarizationService.checkNotarization(nodeId);
        return ResponseEntity.ok(String.format("Node %s is notarized and hashes match.", nodeId));
    }

    /**
     * Updates the encryption key for a node.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node whose key should be updated
     * @return a confirmation message
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/{nodeId}/update-key")
    @Operation(
            summary = "Update encryption key",
            description = "Update the encryption key of the specified node.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to update", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Encryption key updated",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> updateKey(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        keyService.updateKey(nodeId);
        return ResponseEntity.ok(String.format("Encryption key updated for node %s.", nodeId));
    }

    /**
     * Re-encrypts all nodes currently protected with the specified key version.
     * The operation runs asynchronously and returns immediately.
     *
     * @param auth       optional Basic authentication header
     * @param keyVersion version of the outdated encryption key
     * @return a confirmation message
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/update-keys")
    @Operation(
            summary = "Re-encrypts nodes",
            description = "Re-encrypts all nodes with the specified key version. The update runs asynchronously.",
            parameters = {
                    @Parameter(name = "keyVersion", description = "Version of the encryption key to update", required = true, in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Update required",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> updateKey(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam(name = "keyVersion") @Parameter(name = "keyVersion", description = "Version of the encryption key to update", required = true) int keyVersion) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Re-key nodes asynchronously to avoid long blocking requests
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting key update for version {}", keyVersion);
                keyService.updateKeys(keyVersion);
                log.info("Completed key update for version {}", keyVersion);
            } catch (Exception e) {
                log.error("Error while updating keys for version {}", keyVersion, e);
            }
        }, taskExecutor);
        return ResponseEntity.ok("Update required.");
    }

    /**
     * Lists integrity sweep runs with pagination.
     *
     * @param auth optional Basic authentication header
     * @param page page number
     * @param size page size
     * @return page of integrity sweep runs
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/integrity-sweeps")
    @Operation(
            summary = "List integrity sweep runs",
            description = "Returns integrity sweep runs ordered by start time (most recent first).",
            parameters = {
                    @Parameter(name = "page", description = "Page number", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "Page size", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Integrity sweep runs retrieved",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = IntegritySweepRunDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<Page<IntegritySweepRunDto>> getIntegritySweepRuns(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sat"));
        return ResponseEntity.ok(integritySweepService.findRuns(pageable));
    }

    /**
     * Lists nodes currently marked as potentially corrupted by integrity sweeps.
     *
     * @param auth optional Basic authentication header
     * @param page page number
     * @param size page size
     * @return page of potentially corrupted nodes
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/integrity-sweeps/corrupted-nodes")
    @Operation(
            summary = "List potentially corrupted nodes",
            description = "Returns nodes that failed integrity checks and should be reprocessed.",
            parameters = {
                    @Parameter(name = "page", description = "Page number", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "Page size", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Potentially corrupted nodes retrieved",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = CorruptedNodeDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<Page<CorruptedNodeDto>> getCorruptedNodes(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lfs"));
        return ResponseEntity.ok(integritySweepService.findCorruptedNodes(pageable));
    }

    /**
     * Triggers an integrity sweep asynchronously.
     *
     * @param auth optional Basic authentication header
     * @return textual confirmation message
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/integrity-sweeps/run")
    @Operation(
            summary = "Run integrity sweep",
            description = "Starts an integrity sweep asynchronously and returns immediately.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Integrity sweep required",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<String> runIntegritySweep(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CompletableFuture.runAsync(() -> integritySweepService.runSweep("MANUAL"), taskExecutor);
        return ResponseEntity.ok("Integrity sweep required.");
    }

}
