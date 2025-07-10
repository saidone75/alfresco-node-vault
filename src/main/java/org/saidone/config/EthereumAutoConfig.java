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
public class EthereumAutoConfig {

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
    public static class EthereumCredentials {
        private String account;
        private String privateKey;
    }

}
