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
import org.saidone.model.Entry;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepository;
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

    private final GridFsRepositoryImpl gridFsRepositoryImpl;
    private final MongoNodeRepository mongoNodeRepository;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        log.error("Error during streaming => {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error during streaming => " + ex.getMessage());
    }

    @ExceptionHandler(OutOfMemoryError.class)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError ex) {
        log.error("Out of memory error during streaming => {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Server memory limit exceeded. Please try with a smaller file or contact administrator.");
    }

    @GetMapping("/nodes/{nodeId}/content")
    public ResponseEntity<InputStreamResource> getNodeContent(
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "true") boolean attachment) {

        log.debug("Streaming node content for node => {}", nodeId);

        var gridFSFile = gridFsRepositoryImpl.findFileById(nodeId);
        if (gridFSFile == null) {
            log.warn("Node not found in GridFS => {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        var headers = new HttpHeaders();
        if (gridFSFile.getMetadata() != null && gridFSFile.getMetadata().containsKey("_contentType")) {
            headers.setContentType(MediaType.parseMediaType(
                    gridFSFile.getMetadata().getString("_contentType")));
        }
        if (gridFSFile.getLength() > 0) {
            headers.setContentLength(gridFSFile.getLength());
        }
        if (attachment) {
            headers.setContentDispositionFormData("attachment", gridFSFile.getFilename());
        }

        var contentStream = gridFsRepositoryImpl.getFileContent(gridFSFile);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(contentStream));
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<?> getNode(@PathVariable String nodeId) {
        var nodeOptional = mongoNodeRepository.findById(nodeId);
        if (nodeOptional.isPresent()) {
            var nodeWrapper = nodeOptional.get();
            return ResponseEntity.ok(new Entry(nodeWrapper.getNode()));
        }
        return ResponseEntity.notFound().build();
    }

}