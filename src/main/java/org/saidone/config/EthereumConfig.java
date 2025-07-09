package org.saidone.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

@Configuration
@ConfigurationProperties(prefix = "application.service.ethereum")
@Data
public class EthereumConfig {

    private String rpcUrl;
    private String privateKey;
    private String account;
    private boolean autoGenerate;
    
    @PostConstruct
    public void init() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
     if (autoGenerate) {
         ECKeyPair keyPair = Keys.createEcKeyPair();
         String privateKey = Numeric.toHexStringWithPrefix(keyPair.getPrivateKey());
         String address = "0x" + Keys.getAddress(keyPair.getPublicKey());

         this.account = address;
         this.privateKey = privateKey;

         System.out.println("Account Ethereum generato automaticamente:");
         System.out.println("Indirizzo: " + address);


     }
    }

}