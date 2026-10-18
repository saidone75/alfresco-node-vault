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

package org.saidone.service;

import lombok.SneakyThrows;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for validating Basic authentication headers.
 * <p>
 * The provided credentials are decoded and checked against Alfresco via
 * {@link AlfrescoService} to verify that the user has administrative
 * privileges.
 * </p>
 * <p>
 * Successful checks are cached for a configurable time, keyed by a hash of the
 * header, to avoid calling Alfresco on every request. Failed checks are never
 * cached, so the cache neither speeds up credential guessing nor grows with it.
 * </p>
 */
@Service
public class AuthenticationService extends BaseComponent {

    private final AlfrescoService alfrescoService;

    // SHA-256 hashes of the Authorization headers recently verified as admin credentials, with their expiry
    private final Map<String, Instant> authorizedHeaders = new ConcurrentHashMap<>();

    private final Duration authCacheTtl;

    public AuthenticationService(
            AlfrescoService alfrescoService,
            @Value("${application.service.vault.auth-cache-ttl:60s}") Duration authCacheTtl) {
        this.alfrescoService = alfrescoService;
        this.authCacheTtl = authCacheTtl;
    }

    /**
     * Determines whether the given HTTP Authorization header contains valid
     * Basic credentials for an Alfresco administrator.
     *
     * @param authHeader the Authorization header value received from the client
     * @return {@code true} if the credentials are valid and belong to an admin;
     * {@code false} otherwise
     */
    public boolean isAuthorized(String authHeader) {
        if (Strings.isBlank(authHeader)) {
            return false;
        }
        val cacheKey = sha256(authHeader);
        val now = Instant.now();
        val expiresAt = authorizedHeaders.get(cacheKey);
        if (expiresAt != null && now.isBefore(expiresAt)) {
            return true;
        }
        val authorized = checkCredentials(authHeader);
        if (authorized && authCacheTtl.isPositive()) {
            // the map only holds valid admin credentials, so a sweep on insert keeps it small
            authorizedHeaders.values().removeIf(now::isAfter);
            authorizedHeaders.put(cacheKey, now.plus(authCacheTtl));
        } else {
            authorizedHeaders.remove(cacheKey);
        }
        return authorized;
    }

    /**
     * Verifies the Basic credentials of the given header against Alfresco.
     *
     * @param authHeader the Authorization header value received from the client
     * @return {@code true} if the credentials are valid and belong to an admin
     */
    private boolean checkCredentials(String authHeader) {
        val parts = authHeader.trim().split("\\s+", 2);
        if (parts.length != 2 || !"basic".equalsIgnoreCase(parts[0])) {
            return false;
        }
        try {
            val decoded = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            val userIdAndPassword = decoded.split(":", 2);
            if (userIdAndPassword.length != 2) {
                return false;
            }
            return alfrescoService.isAdmin(userIdAndPassword[0], userIdAndPassword[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @SneakyThrows
    private static String sha256(String value) {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

}
