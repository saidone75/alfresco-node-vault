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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

@Configuration
/**
 * Auto-configuration that generates temporary Ethereum credentials.
 * <p>
 * When the property {@code application.service.ethereum.auto-generate} is set
 * to {@code true}, an {@link EthereumCredentials} bean containing a newly
 * created account address and private key is exposed.
 */
public class EthereumAutoConfig {

    /**
     * Generates a new Ethereum account and exposes the credentials as a bean.
     *
     * @return freshly generated credentials
     * @throws Exception if key pair generation fails
     */
    @Bean
    @ConditionalOnProperty(value = "application.service.vault.notarization.ethereum.auto-generate", havingValue = "true")
    public EthereumCredentials ethereumCredentials() throws Exception {
        val keyPair = Keys.createEcKeyPair();
        val privateKey = Numeric.toHexStringWithPrefix(keyPair.getPrivateKey());
        val address = String.format("0x%s", Keys.getAddress(keyPair.getPublicKey()));
        return new EthereumCredentials(address, privateKey.substring(2));
    }
    
    @Data
    @AllArgsConstructor
    /**
     * Holder for an Ethereum account address and its private key.
     */
    public static class EthereumCredentials {
        /** Ethereum account address beginning with {@code 0x}. */
        private String account;
        /** Hex encoded private key. */
        private String privateKey;
    }

}
