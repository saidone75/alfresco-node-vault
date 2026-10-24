

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
import org.saidone.exception.InvalidRequestException;
import org.saidone.model.dto.EntryDto;
import org.saidone.model.dto.CorruptedNodeDto;
import org.saidone.model.dto.IntegritySweepRunDto;
import org.saidone.model.dto.NodeSearchRequestDto;
import org.saidone.service.AuthenticationService;
import org.saidone.service.NodeService;
import org.saidone.service.VaultService;
import org.saidone.service.content.ContentService;
import org.saidone.service.integrity.IntegritySweepService;
import org.saidone.service.crypto.KeyService;
import org.saidone.service.notarization.NotarizationService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.saidone.utils.PageUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller that exposes operations for interacting with the vault.
 * <p>
 * All endpoints require basic authentication and delegate the heavy lifting to
 * {@link org.saidone.service.VaultService} and {@link ContentService}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/vault")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Tag(name = "Vault API", description = "Vault operations")
public class VaultApiController extends BaseComponent {

    private final AuthenticationService authenticationService;
    private final VaultService vaultService;
    private final NodeService nodeService;
    private final ContentService contentService;
    private final Optional<NotarizationService> notarizationService;
    private final Optional<KeyService> keyService;
    private final IntegritySweepService integritySweepService;

    /**
     * Executor used for asynchronous operations.
     */
    private final TaskExecutor taskExecutor;

    private static final String NOTARIZATION_DISABLED = "Notarization is disabled.";
    private static final String ENCRYPTION_DISABLED = "Encryption is disabled: neither content nor metadata encryption is enabled.";
    private static final int CONTENT_BUFFER_SIZE = 8192;
    @Value("${application.service.vault.delete-node.api-enabled:false}")
    private boolean deleteNodeApiEnabled;
    @Value("${application.service.vault.encryption.metadata.enabled:false}")
    private boolean metadataEncryptionEnabled;

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
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
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

        if (!authenticationService.canRead(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (to == null) {
            to = Instant.now();
        }

        if (from == null) {
            from = to.minus(1, ChronoUnit.DAYS);
        }

        val pageable = PageUtils.pageRequest(page, size, Sort.by(dir, "adt"));
        val result = nodeService.findByArchiveDateRange(from, to, pageable)
                .map(nodeWrapper -> new EntryDto(nodeWrapper.getNode()));
        return ResponseEntity.ok(result);
    }

    /**
     * Searches archived nodes by their metadata with pagination support.
     * <p>
     * All the supplied criteria must match. The search is available only when
     * node metadata is not encrypted.
     * </p>
     *
     * @param auth    optional Basic authentication header
     * @param request search criteria
     * @param page    page number
     * @param size    page size
     * @param dir     sort direction for archive date
     * @return paginated list of node entries
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/advanced-search")
    @Operation(
            summary = "Advanced search of archived nodes",
            description = "Search nodes by name, node type, creation and modification dates, mimetype, size, aspects and properties. Available only when metadata encryption is disabled.",
            parameters = {
                    @Parameter(name = "page", description = "Page number", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "Page size", in = ParameterIn.QUERY),
                    @Parameter(name = "dir", description = "Sort direction for archive date (ASC or DESC)", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nodes retrieved",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = EntryDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Metadata encryption is enabled", content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<Object> advancedSearchNodes(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestBody NodeSearchRequestDto request,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction dir) {

        if (!authenticationService.canRead(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (metadataEncryptionEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("Advanced search is disabled: metadata encryption is enabled.");
        }

        if (request.getProperties() != null && request.getProperties().stream()
                .anyMatch(p -> Strings.isBlank(p.getName()) || p.getValues() == null || p.getValues().isEmpty())) {
            throw new InvalidRequestException("Property filters require a name and at least one value");
        }

        val pageable = PageUtils.pageRequest(page, size, Sort.by(dir, "adt"));
        val result = nodeService.search(request, pageable)
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

        if (!authenticationService.canRead(auth)) {
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
     * @return the streamed content
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
    public ResponseEntity<Flux<DataBuffer>> getNodeContent(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "true") boolean attachment) {

        if (!authenticationService.canRead(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Streaming node content for node: {}", nodeId);

        val nodeContentInfo = contentService.getNodeContentInfo(nodeId);
        val headers = new HttpHeaders();
        headers.setContentType(Strings.isNotBlank(nodeContentInfo.getContentType())
                ? MediaType.parseMediaType(nodeContentInfo.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM);
        if (attachment) {
            headers.setContentDispositionFormData("attachment", nodeContentInfo.getFileName());
        }
        val body = DataBufferUtils.readInputStream(
                        () -> contentService.getNodeContent(nodeId).getContentStream(),
                        DefaultDataBufferFactory.sharedInstance, CONTENT_BUFFER_SIZE)
                .subscribeOn(Schedulers.boundedElastic());
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
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
                    @ApiResponse(responseCode = "200", description = "Node successfully restored, or already restored",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "409", description = "Another operation on this node is already in progress",
                            content = @Content(mediaType = "text/plain")),
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
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.format("Node %s successfully restored as %s",
                        HtmlUtils.htmlEscape(nodeId), HtmlUtils.htmlEscape(String.valueOf(newNodeId))));
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
                    @ApiResponse(responseCode = "409", description = "Another operation on this node is already in progress",
                            content = @Content(mediaType = "text/plain")),
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
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.format("Node %s successfully archived.", HtmlUtils.htmlEscape(nodeId)));
    }

    @SecurityRequirement(name = "basicAuth")
    @DeleteMapping("/nodes/{nodeId}")
    @Operation(
            summary = "Delete a node from vault",
            description = "Deletes the specified node from vault and optionally performs Alfresco restore/delete steps.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to delete", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Node successfully deleted", content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "API disabled", content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "404", description = "Node not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Another operation on this node is already in progress",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<String> deleteNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {
        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!deleteNodeApiEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("Delete node API is disabled.");
        }
        vaultService.deleteNode(nodeId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.format("Node %s successfully deleted.", HtmlUtils.htmlEscape(nodeId)));
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
                    @ApiResponse(responseCode = "403", description = "Notarization is disabled",
                            content = @Content(mediaType = "text/plain")),
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

        if (notarizationService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(NOTARIZATION_DISABLED);
        }

        val nodeWrapper = nodeService.findById(nodeId);
        if (Strings.isNotBlank(nodeWrapper.getNotarizationTxId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(String.format("Node %s is already notarized.", HtmlUtils.htmlEscape(nodeId)));
        }
        // Run notarization asynchronously to avoid blocking the caller
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting notarization for node {}", nodeId);
                notarizationService.get().notarizeNode(nodeId);
                log.info("Completed notarization for node {}", nodeId);
            } catch (Exception e) {
                log.error("Error while notarizing node {}", nodeId, e);
            }
        }, taskExecutor);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.format("Notarization for node %s required.", HtmlUtils.htmlEscape(nodeId)));
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
                    @ApiResponse(responseCode = "403", description = "Notarization is disabled",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "409", description = "Another operation on this node is in progress, node not notarized or hashes do not match",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> checkNotarization(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (notarizationService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(NOTARIZATION_DISABLED);
        }

        notarizationService.get().checkNotarization(nodeId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.format("Node %s is notarized and hashes match.", HtmlUtils.htmlEscape(nodeId)));
    }

    /**
     * Updates the encryption key for a node.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node whose key should be updated
     * @return a confirmation message, or 403 when encryption is disabled
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
                    @ApiResponse(responseCode = "403", description = "Encryption is disabled",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "409", description = "Another operation on this node is already in progress",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> updateKey(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (keyService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(ENCRYPTION_DISABLED);
        }
        keyService.get().updateKey(nodeId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.format("Encryption key updated for node %s.", HtmlUtils.htmlEscape(nodeId)));
    }

    /**
     * Re-encrypts all nodes currently protected with the specified key version.
     * The operation runs asynchronously and returns immediately.
     *
     * @param auth       optional Basic authentication header
     * @param keyVersion positive source key version identifying the nodes to re-encrypt
     * @return a confirmation message, or 403 when encryption is disabled
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/update-keys")
    @Operation(
            summary = "Re-encrypts nodes",
            description = "Re-encrypts nodes using the specified source key version with the latest available key. The update runs asynchronously.",
            parameters = {
                    @Parameter(name = "keyVersion", description = "Source key version of the nodes to re-encrypt", required = true, in = ParameterIn.QUERY,
                            schema = @Schema(type = "integer", minimum = "1"))
            },
            responses = {
                    @ApiResponse(responseCode = "202", description = "Update accepted",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content),
                    @ApiResponse(responseCode = "403", description = "Encryption is disabled",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<String> updateKey(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam(name = "keyVersion") @Parameter(name = "keyVersion", description = "Source key version of the nodes to re-encrypt", required = true,
                    schema = @Schema(type = "integer", minimum = "1")) int keyVersion) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (keyService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(ENCRYPTION_DISABLED);
        }

        if (keyVersion < 1) {
            throw new InvalidRequestException("keyVersion must be a positive source key version");
        }

        // Re-key nodes asynchronously to avoid long blocking requests
        CompletableFuture.runAsync(() -> {
            try {
                keyService.get().updateKeys(keyVersion);
            } catch (Exception e) {
                log.error("Error while updating keys for version {}", keyVersion, e);
            }
        }, taskExecutor);
        return ResponseEntity.accepted()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Update required.");
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
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
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

        val pageable = PageUtils.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "sat"));
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
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
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

        val pageable = PageUtils.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "lfs"));
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
                    @ApiResponse(responseCode = "403", description = "Notarization is disabled",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            })
    public ResponseEntity<String> runIntegritySweep(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // integrity sweeps verify content against the notarized hashes
        if (notarizationService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body(NOTARIZATION_DISABLED);
        }

        CompletableFuture.runAsync(() -> integritySweepService.runSweep("MANUAL"), taskExecutor);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Integrity sweep required.");
    }

}
