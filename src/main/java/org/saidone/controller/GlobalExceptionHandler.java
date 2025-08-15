package org.saidone.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.saidone.exception.NodeNotFoundException;
import org.saidone.exception.NotarizationException;
import org.saidone.exception.VaultException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for REST controllers.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleException(Exception e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(VaultException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleVaultException(VaultException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(NodeNotFoundException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNodeNotFoundException(NodeNotFoundException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Node not found");
    }

    @ExceptionHandler(OutOfMemoryError.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Server memory limit exceeded. Please contact an administrator.");
    }

    @ExceptionHandler(NotarizationException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNotarizationException(NotarizationException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private ResponseEntity<String> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(message);
    }

}
