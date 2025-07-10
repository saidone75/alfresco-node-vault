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
    @ConditionalOnProperty(value = "application.service.ethereum.auto-generate", havingValue = "true")
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
        /** Hex encoded private key without the {@code 0x} prefix. */
        private String privateKey;
    }

}
