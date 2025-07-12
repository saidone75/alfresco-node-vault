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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.exception.NodeNotFoundException;
import org.saidone.exception.VaultException;
import org.saidone.model.Entry;
import org.saidone.service.AuthenticationService;
import org.saidone.service.NodeService;
import org.saidone.service.VaultService;
import org.saidone.service.content.ContentService;
import org.saidone.service.notarization.NotarizationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
public class VaultApiController {

    /** Service used to verify Basic authentication credentials. */
    private final AuthenticationService authenticationService;
    /** Provides high level vault operations. */
    private final VaultService vaultService;
    /** Service responsible for interacting with nodes. */
    private final NodeService nodeService;
    /** Service responsible for retrieving binary content. */
    private final ContentService contentService;
    /** Handles notarization requests for archived nodes. */
    private final NotarizationService notarizationService;

    /**
     * Handles any unexpected exception thrown during request processing.
     *
     * @param e the exception that was raised
     * @return a generic {@code 500 Internal Server Error} response
     */
    @ExceptionHandler(Exception.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleException(Exception e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error");
    }

    /**
     * Handles vault specific errors that may occur while processing a request.
     *
     * @param e the thrown {@link VaultException}
     * @return a generic error response with HTTP status {@code 500}
     */
    @ExceptionHandler(VaultException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleVaultException(Exception e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error");
    }

    /**
     * Handles cases where the requested node cannot be found in the vault.
     *
     * @param e the thrown exception
     * @return a response with HTTP status {@code 404 Not Found}
     */
    @ExceptionHandler(NodeNotFoundException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNodeNotFoundException(NodeNotFoundException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Node not found");
    }

    /**
     * Handles out-of-memory situations that may occur when streaming large files.
     *
     * @param e the {@link OutOfMemoryError} encountered
     * @return a response indicating that the server ran out of memory
     */
    @ExceptionHandler(OutOfMemoryError.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Server memory limit exceeded. Please try with a smaller file or contact administrator.");
    }

    /**
     * Returns the metadata of a node stored in the vault.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to retrieve
     * @return the node metadata wrapped in an {@link Entry} object
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
                                    schema = @Schema(implementation = Entry.class))),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    @SneakyThrows
    public ResponseEntity<?> getNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        val node = vaultService.getNode(nodeId);
        return ResponseEntity.ok(new Entry(node));
    }

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
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    /**
     * Streams the binary content of a node.
     *
     * @param auth       optional Basic authentication header
     * @param nodeId     identifier of the node
     * @param attachment {@code true} if the content should be sent as a download attachment
     * @return the streamed content wrapped as an {@link InputStreamResource}
     */
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
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    @SneakyThrows
    public ResponseEntity<?> restoreNode(
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
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<?> archiveNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        vaultService.archiveNode(nodeId);
        return ResponseEntity.ok().body(String.format("Node %s successfully archived.", nodeId));
    }

    /**
     * Require notarization of a node.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to notarize
     * @return a confirmation message
     */
    @SecurityRequirement(name = "basicAuth")
    @PostMapping("/nodes/{nodeId}/notarize")
    @Operation(
            summary = "Notarize a node",
            description = "Request notarization of the specified node.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to notarize", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Notarization required",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<?> notarizeNode(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CompletableFuture.runAsync(() -> notarizationService.notarizeDocument(nodeId));
        return ResponseEntity.ok().body(String.format("Notarization for node %s required.", nodeId));
    }

    /**
     * Check notarization of a node.
     *
     * @param auth   optional Basic authentication header
     * @param nodeId identifier of the node to be checked
     */
    @SecurityRequirement(name = "basicAuth")
    @GetMapping("/nodes/{nodeId}/notarize")
    @Operation(
            summary = "Notarize a node",
            description = "Request notarization of the specified node.",
            parameters = {
                    @Parameter(name = "nodeId", description = "Identifier of the node to notarize", required = true, in = ParameterIn.PATH)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Notarization required",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "404", description = "Node not found",
                            content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content)
            })
    public ResponseEntity<?> checkNotarization(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String nodeId) {

        if (!authenticationService.isAuthorized(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CompletableFuture.runAsync(() -> notarizationService.notarizeDocument(nodeId));

        val nodeWrapper = nodeService.findById(nodeId);
        if (Strings.isNotBlank(nodeWrapper.getNotarizationTxId())) {
            val hash = notarizationService.getHash(nodeWrapper.getNotarizationTxId());
            val nodeContentInfo = contentService.getNodeContentInfo(nodeId);
            val algorithm = contentService.getNodeContentInfo(nodeId).getContentHashAlgorithm();
            log.debug(hash);
        } else {
            return ResponseEntity.ok("");
        }



        return ResponseEntity.ok().body(String.format("Notarization for node %s required.", nodeId));
    }

}