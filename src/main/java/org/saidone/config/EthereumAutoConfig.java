package org.saidone.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

@Configuration
public class EthereumAutoConfig {

    @Bean
    @ConditionalOnProperty(value = "application.service.ethereum.auto-generate", havingValue = "true")
    public EthereumCredentials ethereumCredentials() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String privateKey = Numeric.toHexStringWithPrefix(keyPair.getPrivateKey());
        String address = "0x" + Keys.getAddress(keyPair.getPublicKey());
        
        // Log i dettagli (solo per sviluppo - in produzione questi dovrebbero essere protetti)
        System.out.println("Account Ethereum generato automaticamente:");
        System.out.println("Indirizzo: " + address);
        
        return new EthereumCredentials(address, privateKey.substring(2));
    }
    
    @Data
    @AllArgsConstructor
    public static class EthereumCredentials {
        private String account;
        private String privateKey;
    }
}
