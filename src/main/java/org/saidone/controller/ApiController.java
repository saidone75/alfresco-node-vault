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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.exception.NodeNotFoundOnVaultException;
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
public class ApiController {

    private final VaultService vaultService;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Error during streaming: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error during streaming: " + e.getMessage());
    }

    @ExceptionHandler(NodeNotFoundOnVaultException.class)
    public ResponseEntity<String> handleNodeNotFoundOnVaultException(NodeNotFoundOnVaultException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(e.getMessage());
    }

    @ExceptionHandler(OutOfMemoryError.class)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError e) {
        log.error("Out of memory error during streaming: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Server memory limit exceeded. Please try with a smaller file or contact administrator.");
    }

    @GetMapping("/nodes/{nodeId}/content")
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
    public ResponseEntity<?> getNode(@PathVariable String nodeId) {
        val node = vaultService.getNode(nodeId);
        return ResponseEntity.ok(new Entry(node));
    }

    @PostMapping("/nodes/{nodeId}/restore")
    public ResponseEntity<?> restoreNode(
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "false") boolean restorePermissions) {
        vaultService.restoreNode(nodeId, restorePermissions);
        return ResponseEntity.ok().body(String.format("Node %s successfully restored.", nodeId));
    }

}