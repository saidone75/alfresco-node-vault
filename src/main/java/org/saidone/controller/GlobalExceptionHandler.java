package org.saidone.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.saidone.exception.ApiExceptionError;
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
    public ResponseEntity<ApiExceptionError> handleException(Exception e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getClass().getSimpleName(), "Internal server error");
    }

    @ExceptionHandler(VaultException.class)
    @Operation(hidden = true)
    public ResponseEntity<ApiExceptionError> handleVaultException(VaultException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getClass().getSimpleName(), "Internal server error");
    }

    @ExceptionHandler(NodeNotFoundException.class)
    @Operation(hidden = true)
    public ResponseEntity<ApiExceptionError> handleNodeNotFoundException(NodeNotFoundException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, e.getClass().getSimpleName(), "Node not found");
    }

    @ExceptionHandler(OutOfMemoryError.class)
    @Operation(hidden = true)
    public ResponseEntity<ApiExceptionError> handleOutOfMemoryError(OutOfMemoryError e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getClass().getSimpleName(),
                "Server memory limit exceeded. Please try with a smaller file or contact administrator.");
    }

    @ExceptionHandler(NotarizationException.class)
    @Operation(hidden = true)
    public ResponseEntity<ApiExceptionError> handleNotarizationException(NotarizationException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getClass().getSimpleName(), e.getMessage());
    }

    private ResponseEntity<ApiExceptionError> buildResponse(HttpStatus status, String errorKey, String message) {
        ApiExceptionError error = new ApiExceptionError();
        error.setErrorKey(errorKey);
        error.setStatusCode(String.valueOf(status.value()));
        error.setBriefSummary(message);
        error.setStackTrace(Strings.EMPTY);
        error.setDescriptionURL(Strings.EMPTY);
        error.setLogId(Strings.EMPTY);
        return ResponseEntity.status(status).body(error);
    }
}
