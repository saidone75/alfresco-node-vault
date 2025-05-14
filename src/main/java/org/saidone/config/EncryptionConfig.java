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

package org.saidone.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Setter;
import org.saidone.service.AbstractCryptoService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Data
@ConfigurationProperties(prefix = "application.service.vault.encryption")
@Validated
public class EncryptionConfig {

    @Setter
    private String vaultSecretPath;
    @Setter
    private String vaultSecretKey;
    @Setter
    private String secret;

    @Valid
    @NotNull
    private AbstractCryptoService.Kdf kdf;

    @Valid
    private JcaProperties jca;

    @Valid
    private BcProperties bc;

    @Data
    public static class JcaProperties {
        @Min(16)
        private int saltLength;
        @Min(12)
        private int ivLength;
    }

    @Data
    public static class BcProperties {
        @Min(16)
        private int saltLength;
        @Min(12)
        private int nonceLength;
    }

}