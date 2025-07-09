package org.saidone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@ConfigurationProperties(prefix = "application.service.ethereum")
@Data
public class EthereumConfig {
    private String rpcUrl;
    private String privateKey;
    private String account;
    
    @Autowired(required = false)
    public void setCredentialsFromGenerator(EthereumAutoConfig.EthereumCredentials credentials) {
        if (credentials != null) {
            this.account = credentials.getAccount();
            this.privateKey = credentials.getPrivateKey();
        }
    }

}