package org.saidone.controller;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
public class ContentStreamingController {

    private final GridFsRepositoryImpl gridFsRepositoryImpl;
    private final MongoNodeRepository mongoNodeRepository;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        log.error("Error during streaming: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error during streaming: " + ex.getMessage());
    }

    @ExceptionHandler(OutOfMemoryError.class)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError ex) {
        log.error("Out of memory error during streaming: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Server memory limit exceeded. Please try with a smaller file or contact administrator.");
    }

    @GetMapping("/nodes/{nodeId}/content")
    public ResponseEntity<InputStreamResource> streamNodeContent(
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "true") boolean attachment) {

        log.debug("Streaming node content for node => {}", nodeId);

        GridFSFile gridFSFile = gridFsRepositoryImpl.findFileById(nodeId);
        if (gridFSFile == null) {
            log.warn("Node not found in GridFS => {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream contentStream = gridFsRepositoryImpl.getFileContent(gridFSFile);

            HttpHeaders headers = new HttpHeaders();

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

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(contentStream));

        } catch (Exception e) {
            log.error("Error streaming content for node {}: {}", nodeId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<?> getNodeInfo(@PathVariable String nodeId) {
        var nodeOptional = mongoNodeRepository.findById(nodeId);
        if (nodeOptional.isPresent()) {
            var nodeWrapper = nodeOptional.get();
            return ResponseEntity.ok(nodeWrapper);
        }
        return ResponseEntity.notFound().build();
    }

}