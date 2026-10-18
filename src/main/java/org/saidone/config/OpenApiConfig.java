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

package org.saidone.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Registers OpenAPI components and security scheme for the REST API.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Alfresco Node Vault API",
        version = "v1",
        description = """
                Archive, retrieve, restore and notarize Alfresco nodes.

                Paginated endpoints accept `page` (zero-based) and `size`; dates are ISO-8601 instants.
                Operations return a plain-text confirmation and errors a plain-text description.
                Endpoints of features disabled by configuration return `403`.

                Requests to the Alfresco REST API for archived nodes are also served by the vault: see
                [RestApi.md](https://github.com/saidone75/alfresco-node-vault/blob/main/doc/RestApi.md).
                """))
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic",
        description = "Credentials of an Alfresco administrator."
)
@SecurityRequirement(name = "basicAuth")
public class OpenApiConfig {
}
