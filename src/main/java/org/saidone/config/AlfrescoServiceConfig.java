package org.saidone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "application.service.alfresco")
@Data
public class AlfrescoServiceConfig {

    private int searchBatchSize;
    private boolean permanentlyDeleteNodes;
    private List<String> include;

}
