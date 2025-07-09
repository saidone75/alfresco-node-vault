package org.saidone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.service.ethereum")
@Data
public class EthereumConfig {
    private String rpcUrl;
    private String privateKey;
    private String account;
}
