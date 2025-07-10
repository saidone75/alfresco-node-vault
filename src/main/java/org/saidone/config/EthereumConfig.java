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

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

@Configuration
@ConfigurationProperties(prefix = "application.service.ethereum")
@Data
@Slf4j
/**
 * Configuration properties used to connect to an Ethereum node.
 * <p>
 * When {@code autoGenerate} is enabled a new key pair and account
 * address are created at startup.
 */
public class EthereumConfig {

    /** JSON-RPC endpoint of the Ethereum node. */
    private String rpcUrl;
    /** Hex encoded private key used to sign transactions. */
    private String privateKey;
    /** Ethereum account address used as transaction recipient. */
    private String account;
    /** Whether to automatically generate a new Ethereum account at startup. */
    private boolean autoGenerate;
    
    /**
     * Generates an Ethereum account if {@link #autoGenerate} is enabled.
     *
     * @throws InvalidAlgorithmParameterException if the algorithm parameters are invalid
     * @throws NoSuchAlgorithmException           if the algorithm cannot be found
     * @throws NoSuchProviderException            if the security provider is unavailable
     */
    @PostConstruct
    public void init() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
     if (autoGenerate) {
         val keyPair = Keys.createEcKeyPair();
         this.privateKey = Numeric.toHexStringWithPrefix(keyPair.getPrivateKey());
         this.account = String.format("0x%s", Keys.getAddress(keyPair.getPublicKey()));
         log.debug("Ethereum account automatically generated: {}", this.account);
     }
    }

}
