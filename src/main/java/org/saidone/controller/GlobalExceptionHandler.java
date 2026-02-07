/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
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
