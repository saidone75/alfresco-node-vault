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
import org.saidone.service.crypto.AbstractCryptoService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Data
@ConfigurationProperties(prefix = "application.service.vault.encryption")
@Validated
/**
 * Configuration properties for content encryption managed through Vault.
 * <p>
 * These settings define which secret to use for encryption and how key
 * derivation is performed.
 */
public class EncryptionConfig {

    /** Vault key/value mount path. */
    @Setter
    private String vaultSecretKvMount;
    /** Path of the secret within Vault. */
    @Setter
    private String vaultSecretPath;
    /** Key name of the secret used for encryption. */
    @Setter
    private String vaultSecretKey;
    /** Fallback secret value when Vault is not available. */
    @Setter
    private String secret;

    /** Key derivation configuration. */
    @Valid
    @NotNull
    private AbstractCryptoService.Kdf kdf;

    /** JCA provider specific parameters. */
    @Valid
    private JcaProperties jca;

    /** BouncyCastle provider specific parameters. */
    @Valid
    private BcProperties bc;

    /**
     * Java Cryptography Architecture related parameters.
     */
    @Data
    public static class JcaProperties {
        /** Length in bytes of the random salt. */
        @Min(16)
        private int saltLength;
        /** Length in bytes of the initialization vector. */
        @Min(12)
        private int ivLength;
    }

    /**
     * Bouncy Castle provider related parameters.
     */
    @Data
    public static class BcProperties {
        /** Length in bytes of the random salt. */
        @Min(16)
        private int saltLength;
        /** Length in bytes of the nonce. */
        @Min(12)
        private int nonceLength;
    }

}