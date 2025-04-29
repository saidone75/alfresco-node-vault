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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.exception.NodeNotOnVaultException;
import org.saidone.exception.VaultException;
import org.saidone.model.Entry;
import org.saidone.service.VaultService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vault API", description = "Vault operations")
public class ApiController {

    private final VaultService vaultService;

    @ExceptionHandler(Exception.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleException(Exception e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.getMessage());
    }

    @ExceptionHandler(VaultException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleVaultException(Exception e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.getMessage());
    }

    @ExceptionHandler(NodeNotOnVaultException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNodeNotFoundOnVaultException(NodeNotOnVaultException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(e.getMessage());
    }

    @ExceptionHandler(OutOfMemoryError.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError e) {
        log.error("Out of memory error during streaming: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Server memory limit exceeded. Please try with a smaller file or contact administrator.");
    }

    @GetMapping("/nodes/{nodeId}/content")
    @Operation(summary = "Get node content",
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
    public ResponseEntity<InputStreamResource> getNodeContent(
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "true") boolean attachment) {

        log.debug("Streaming node content for node: {}", nodeId);

        val nodeContent = vaultService.getNodeContent(nodeId);

        val headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(nodeContent.getContentType()));
        headers.setContentLength(nodeContent.getLength());
        if (attachment) {
            headers.setContentDispositionFormData("attachment", nodeContent.getFileName());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(nodeContent.getContentStream()));
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node metadata",
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
    public ResponseEntity<?> getNode(@PathVariable String nodeId) {
        val node = vaultService.getNode(nodeId);
        return ResponseEntity.ok(new Entry(node));
    }

    @PostMapping("/nodes/{nodeId}/restore")
    @Operation(summary = "Restore a node",
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
    public ResponseEntity<?> restoreNode(
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "false") boolean restorePermissions) {
        vaultService.restoreNode(nodeId, restorePermissions);
        return ResponseEntity.ok().body(String.format("Node %s successfully restored.", nodeId));
    }

    @PostMapping("/nodes/{nodeId}/archive")
    @Operation(summary = "Archive a node",
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
            @PathVariable String nodeId) {
        vaultService.archiveNode(nodeId);
        return ResponseEntity.ok().body(String.format("Node %s successfully archived.", nodeId));
    }

}