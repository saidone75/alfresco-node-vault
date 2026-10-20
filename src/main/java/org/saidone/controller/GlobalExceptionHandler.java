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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.exception.InvalidRequestException;
import org.saidone.exception.NodeNotFoundException;
import org.saidone.exception.NodeNotNotarizedException;
import org.saidone.exception.NodeOperationInProgressException;
import org.saidone.exception.NotarizationException;
import org.saidone.exception.NotarizedHashMismatchException;
import org.saidone.exception.VaultException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.HtmlUtils;

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

    @ExceptionHandler(ServerWebInputException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleServerWebInputException(ServerWebInputException e) {
        log.warn(e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @ExceptionHandler(ResponseStatusException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException e) {
        log.warn(e.getMessage());
        val status = HttpStatus.resolve(e.getStatusCode().value());
        return buildResponse(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR,
                status != null ? status.getReasonPhrase() : "Internal server error");
    }

    @ExceptionHandler(InvalidRequestException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleInvalidRequestException(InvalidRequestException e) {
        log.warn(e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NodeNotFoundException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNodeNotFoundException(NodeNotFoundException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Node not found");
    }

    @ExceptionHandler(NodeOperationInProgressException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNodeOperationInProgressException(NodeOperationInProgressException e) {
        log.warn(e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OutOfMemoryError.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleOutOfMemoryError(OutOfMemoryError e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Server memory limit exceeded. Please contact an administrator.");
    }

    @ExceptionHandler({NodeNotNotarizedException.class, NotarizedHashMismatchException.class})
    @Operation(hidden = true)
    public ResponseEntity<String> handleNotarizationCheckException(NotarizationException e) {
        log.warn(e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NotarizationException.class)
    @Operation(hidden = true)
    public ResponseEntity<String> handleNotarizationException(NotarizationException e) {
        log.error(e.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private ResponseEntity<String> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(HtmlUtils.htmlEscape(String.valueOf(message)));
    }

}
