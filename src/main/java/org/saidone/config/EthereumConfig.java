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
public class EthereumConfig {

    private String rpcUrl;
    private String privateKey;
    private String account;
    private boolean autoGenerate;
    
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