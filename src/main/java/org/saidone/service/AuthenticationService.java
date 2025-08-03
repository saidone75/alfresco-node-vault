/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service responsible for validating Basic authentication headers.
 * <p>
 * The provided credentials are decoded and checked against Alfresco via
 * {@link AlfrescoService} to verify that the user has administrative
 * privileges.
 * </p>
 */
@RequiredArgsConstructor
@Service
public class AuthenticationService {

    private final AlfrescoService alfrescoService;

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

}
